package io.github.darkryh.katalyst.scheduler.lifecycle

import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.invocation.CallableInvoker
import io.github.darkryh.katalyst.di.invocation.ParameterResolver
import io.github.darkryh.katalyst.di.internal.ServiceRegistry
import io.github.darkryh.katalyst.di.lifecycle.ReadyHook
import io.github.darkryh.katalyst.scheduler.exception.SchedulerInvocationException
import io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory
import kotlin.reflect.KVisibility
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.jvmErasure

private data class SchedulerMethodCandidate(
    val service: Service,
    val method: kotlin.reflect.KFunction<*>,
)

/**
 * Discovers scheduler registration methods declared directly on Katalyst services.
 *
 * The application-facing contract is:
 * - the owning class implements [Service]
 * - the scheduler method returns [SchedulerJobHandle]
 * - the method schedules through `requireScheduler()`
 */
internal class SchedulerInitializer : ReadyHook {
    private val logger = LoggerFactory.getLogger("SchedulerInitializer")

    override val id: String = "SchedulerInitializer"
    override val order: Int = -50

    override suspend fun onReady() {
        try {
            val allServices = ServiceRegistry.getAll()
            logger.info("Scheduler runtime-ready initialization started")
            logger.debug("Scheduler scanning {} service instance(s)", allServices.size)

            if (allServices.isEmpty()) {
                logger.info("Scheduler initialization completed: no services available for scanning")
                return
            }

            val candidates = discoverCandidateMethods(allServices)
            if (candidates.isEmpty()) {
                logger.info("Scheduler initialization completed: no scheduler service methods discovered")
                return
            }

            val validatedMethods = validateCandidatesByBytecode(candidates)
            if (validatedMethods.isEmpty()) {
                logger.info("Scheduler initialization completed: no valid scheduler service methods after validation")
                return
            }

            val container = KatalystContainerProvider.currentOrNull()
            val resolver = container?.let(::ParameterResolver)
            var successCount = 0
            var failureCount = 0

            validatedMethods.forEach { candidate ->
                val service = candidate.service
                val method = candidate.method
                val signature = "${service::class.simpleName}.${method.name}()"

                try {
                    logger.debug("Invoking scheduler service method {}", signature)
                    val result = CallableInvoker.callMemberWithDefaults(
                        instance = service,
                        function = method,
                        resolver = resolver,
                        ownerDescription = "scheduler method $signature",
                    )

                    if (result is SchedulerJobHandle) {
                        logger.debug("Scheduler service method registered successfully: {}", signature)
                        successCount++
                    } else {
                        logger.error(
                            "Scheduler service method returned invalid type: {} -> {}",
                            signature,
                            result?.let { it::class.simpleName } ?: "null",
                        )
                        failureCount++
                    }
                } catch (e: Exception) {
                    logger.error("Scheduler service method failed: {} - {}", signature, e.message)
                    failureCount++
                    throw SchedulerInvocationException(
                        message = "Failed to invoke scheduler method $signature: ${e.message}",
                        cause = e,
                    )
                }
            }

            logger.info(
                "Scheduler initialization completed: {} registration(s), {} failure(s)",
                successCount,
                failureCount,
            )

            if (failureCount > 0) {
                throw SchedulerInvocationException(
                    message = "Scheduler invocation encountered $failureCount error(s). See logs above for details.",
                )
            }
        } catch (e: Exception) {
            logger.error("Scheduler runtime-ready initialization failed: {}", e.message)
            throw e
        }
    }

    private fun discoverCandidateMethods(services: List<Service>): List<SchedulerMethodCandidate> {
        val candidates = mutableListOf<SchedulerMethodCandidate>()

        services.forEach { service ->
            service::class.functions
                .filter { function ->
                    val returnClass = function.returnType.jvmErasure.java
                    SchedulerJobHandle::class.java.isAssignableFrom(returnClass) &&
                        function.visibility != KVisibility.PRIVATE
                }
                .forEach { function ->
                    candidates += SchedulerMethodCandidate(service, function)
                }
        }

        return candidates.sortedWith(
            compareBy<SchedulerMethodCandidate>(
                { it.service::class.qualifiedName ?: it.service::class.simpleName ?: "" },
                { it.method.name },
            ),
        )
    }

    private fun validateCandidatesByBytecode(
        candidates: List<SchedulerMethodCandidate>,
    ): List<SchedulerMethodCandidate> =
        candidates.filter { candidate ->
            val javaMethod = candidate.service::class.java.declaredMethods
                .find { it.name == candidate.method.name }
                ?: return@filter false

            SchedulerMethodBytecodeValidator.validates(javaMethod).also { valid ->
                logger.debug(
                    "Method {}.{} bytecode validation: {}",
                    candidate.service::class.simpleName,
                    candidate.method.name,
                    valid,
                )
            }
        }
}

internal object SchedulerMethodBytecodeValidator {
    private val logger = LoggerFactory.getLogger("SchedulerMethodBytecodeValidator")
    private val schedulerMethodNames = setOf("jobs", "scheduleCron", "schedule", "scheduleFixedDelay")

    fun validates(method: java.lang.reflect.Method): Boolean =
        runCatching {
            val className = method.declaringClass.name.replace('.', '/') + ".class"
            val stream = method.declaringClass.classLoader?.getResourceAsStream(className)
                ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(className)
                ?: return false

            stream.use { input ->
                val reader = ClassReader(input)
                var found = false

                reader.accept(
                    object : ClassVisitor(Opcodes.ASM9) {
                        override fun visitMethod(
                            access: Int,
                            name: String,
                            descriptor: String,
                            signature: String?,
                            exceptions: Array<out String>?,
                        ): MethodVisitor? {
                            val parent = super.visitMethod(access, name, descriptor, signature, exceptions)
                            if (name != method.name) return parent

                            return object : MethodVisitor(Opcodes.ASM9, parent) {
                                override fun visitMethodInsn(
                                    opcode: Int,
                                    owner: String,
                                    methodName: String,
                                    descriptor: String,
                                    isInterface: Boolean,
                                ) {
                                    if (
                                        methodName in schedulerMethodNames &&
                                        (owner.contains("SchedulerService") || owner.contains("ServiceScheduler"))
                                    ) {
                                        found = true
                                    }
                                    super.visitMethodInsn(opcode, owner, methodName, descriptor, isInterface)
                                }
                            }
                        }
                    },
                    ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
                )

                found
            }
        }.getOrElse {
            logger.debug("Bytecode validation failed for {}: {}", method.name, it.message)
            false
        }
}
