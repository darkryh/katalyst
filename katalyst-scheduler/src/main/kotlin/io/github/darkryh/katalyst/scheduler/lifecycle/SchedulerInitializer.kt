package io.github.darkryh.katalyst.scheduler.lifecycle

import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.di.internal.ServiceRegistry
import io.github.darkryh.katalyst.di.lifecycle.ApplicationReadyInitializer
import io.github.darkryh.katalyst.scheduler.exception.SchedulerDiscoveryException
import io.github.darkryh.katalyst.scheduler.exception.SchedulerInvocationException
import io.github.darkryh.katalyst.scheduler.exception.SchedulerValidationException
import io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle
import org.objectweb.asm.*
import org.slf4j.LoggerFactory
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

private data class SchedulerMethodCandidate(
    val service: Service,
    val method: kotlin.reflect.KFunction<*>
)

/**
 * Automatically discovers and invokes scheduler registration methods.
 *
 * **Ownership**: This initializer is owned by the scheduler module and handles all
 * scheduler-specific discovery and invocation logic.
 *
 * **Execution Order & Safety Guarantee:**
 * Runs in runtime-ready phase (default order=0), after Ktor readiness has been
 * acknowledged and pre-start validation has completed.
 *
 * **Important Contract:**
 * At this point in the runtime-ready sequence:
 * ✓ Database connection verified and working
 * ✓ ALL registered tables verified to exist in schema
 * ✓ Safe to invoke scheduler methods that query tables
 * ✓ No additional schema validation needed
 *
 * If pre-start validation failed → runtime-ready initializers never run.
 * If we reach this point → schema/connectivity guarantees are already established.
 *
 * **Discovery Process (3-Step Validation):**
 *
 * STEP 1: Reflection-based signature matching
 * ─────────────────────────────────────────
 * Scan all services for methods matching:
 * - Return type: SchedulerJobHandle (explicit marker)
 * - Parameters: No required params
 * - Visibility: Not private
 *
 * STEP 2: Bytecode signature validation
 * ─────────────────────────────────────────
 * Verify the method actually calls scheduler methods via bytecode analysis:
 * - Check if method calls scheduler.scheduleCron(...)
 * - Check if method calls scheduler.schedule(...)
 * - Check if method calls scheduler.scheduleFixedDelay(...)
 *
 * STEP 3: Invocation with error handling
 * ─────────────────────────────────────────
 * Invoke validated method and verify Job is returned
 *
 * **Why Bytecode Validation?**
 *
 * Without bytecode validation, this would be possible but wrong:
 * ```kotlin
 * // False positive - returns SchedulerJobHandle but doesn't register anything!
 * fun scheduleAuthDigest(): SchedulerJobHandle {
 *     return Job().asSchedulerHandle()  // ❌ Creates fake job, no scheduler call
 * }
 * ```
 *
 * Bytecode validation ensures the method actually invokes scheduler methods,
 * preventing invalid implementations from being discovered.
 *
 * **Module**: katalyst-scheduler
 */
internal class SchedulerInitializer : ApplicationReadyInitializer {
    private val logger = LoggerFactory.getLogger("SchedulerInitializer")

    override val initializerId: String = "SchedulerInitializer"
    override val order: Int = -50

    override suspend fun onRuntimeReady() {
        try {
            // Get all services from ServiceRegistry (populated during component discovery)
            val allServices = ServiceRegistry.getAll()
            logger.info("Scheduler runtime-ready initialization started")
            logger.debug("Scheduler scanning {} service instance(s)", allServices.size)

            if (allServices.isEmpty()) {
                logger.info("Scheduler initialization completed: no services available for scanning")
                return
            }

            // Step 1: Discover candidate methods by reflection
            logger.debug("Scheduler step 1/3: discovering candidate methods")
            val candidates = discoverCandidateMethods(allServices)

            if (candidates.isEmpty()) {
                logger.info("Scheduler initialization completed: no scheduler methods discovered")
                return
            }

            logger.debug("Scheduler discovered {} candidate method(s)", candidates.size)
            if (logger.isDebugEnabled) {
                candidates.groupBy { it.service::class.simpleName ?: "UnknownService" }
                    .forEach { (serviceName, methods) ->
                        logger.debug(
                            "Scheduler candidates for {}: {}",
                            serviceName,
                            methods.map { it.method.name }.sorted()
                        )
                    }
            }

            // Step 2: Validate candidates via bytecode analysis
            logger.debug("Scheduler step 2/3: validating candidates with bytecode analysis")
            val validatedMethods = validateCandidatesByBytecode(candidates)

            if (validatedMethods.isEmpty()) {
                logger.info("Scheduler initialization completed: no valid scheduler methods after validation")
                return
            }

            logger.debug("Scheduler validated {} method(s)", validatedMethods.size)
            if (logger.isDebugEnabled) {
                validatedMethods.groupBy { it.service::class.simpleName ?: "UnknownService" }
                    .forEach { (serviceName, methods) ->
                        logger.debug(
                            "Scheduler validated methods for {}: {}",
                            serviceName,
                            methods.map { it.method.name }.sorted()
                        )
                    }
            }

            // Step 3: Invoke validated methods
            logger.debug("Scheduler step 3/3: invoking validated methods")
            var successCount = 0
            var failureCount = 0

            validatedMethods.forEach { candidate ->
                val service = candidate.service
                val method = candidate.method
                val signature = "${service::class.simpleName}.${method.name}()"

                try {
                    logger.debug("Invoking scheduler method {}", signature)
                    method.isAccessible = true
                    val result = method.call(service)

                    if (isSchedulerJobHandle(result)) {
                        logger.debug("Scheduler method registered successfully: {}", signature)
                        successCount++
                    } else {
                        logger.error(
                            "Scheduler method returned invalid type: {} -> {}",
                            signature,
                            result?.let { it::class.simpleName } ?: "null"
                        )
                        failureCount++
                    }
                } catch (e: Exception) {
                    logger.error("Scheduler method failed: {} - {}", signature, e.message)
                    failureCount++
                    throw SchedulerInvocationException(
                        message = "Failed to invoke scheduler method $signature: ${e.message}",
                        cause = e
                    )
                }
            }

            logger.info(
                "Scheduler initialization completed: {} registration(s), {} failure(s)",
                successCount,
                failureCount
            )

            if (failureCount > 0) {
                throw SchedulerInvocationException(
                    message = "Scheduler invocation encountered $failureCount error(s). See logs above for details."
                )
            }

        } catch (e: Exception) {
            logger.error("Scheduler runtime-ready initialization failed: {}", e.message)
            throw e
        }
    }

    /**
     * STEP 1: Discover candidate methods using reflection.
     *
     * Finds all methods matching:
     * - Return type: SchedulerJobHandle
     * - No required parameters
     * - Not private visibility
     */
    private fun discoverCandidateMethods(
        services: List<Service>
    ): List<SchedulerMethodCandidate> {
        val candidates = mutableListOf<SchedulerMethodCandidate>()

        services.forEach { service ->
            val serviceClass = service::class

            serviceClass.functions
                .filter { function ->
                    // Must return SchedulerJobHandle
                    val isSchedulerJobHandle = runCatching {
                        val schedulerJobHandleClass = Class.forName(
                            "io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle"
                        )
                        val returnClass = function.returnType.jvmErasure.java
                        schedulerJobHandleClass.isAssignableFrom(returnClass) ||
                            returnClass == schedulerJobHandleClass
                    }.getOrElse { false }

                    if (!isSchedulerJobHandle) return@filter false

                    // No required parameters
                    val hasNoRequiredParams = function.parameters
                        .filter { it.kind == KParameter.Kind.VALUE }
                        .all { it.isOptional }

                    if (!hasNoRequiredParams) return@filter false

                    // Not private
                    val isNotPrivate = function.visibility != KVisibility.PRIVATE

                    isNotPrivate
                }
                .forEach { function ->
                    candidates += SchedulerMethodCandidate(service, function)
                }
        }

        return candidates.sortedWith(
            compareBy<SchedulerMethodCandidate>(
                { it.service::class.qualifiedName ?: it.service::class.simpleName ?: "" },
                { it.method.name }
            )
        )
    }

    /**
     * STEP 2: Validate candidates using bytecode analysis.
     *
     * Verifies that each method actually calls one of:
     * - scheduler.scheduleCron(...)
     * - scheduler.schedule(...)
     * - scheduler.scheduleFixedDelay(...)
     *
     * This prevents false positives where method returns SchedulerJobHandle
     * but doesn't actually register a scheduler task.
     */
    private fun validateCandidatesByBytecode(
        candidates: List<SchedulerMethodCandidate>
    ): List<SchedulerMethodCandidate> {
        val validated = mutableListOf<SchedulerMethodCandidate>()

        candidates.forEach { candidate ->
            val service = candidate.service
            val method = candidate.method
            val javaMethod = service::class.java.declaredMethods
                .find { it.name == method.name }
                ?: return@forEach

            val isValid = runCatching {
                SchedulerMethodBytecodeValidator.validates(javaMethod)
            }.getOrElse { false }

            if (isValid) {
                validated += candidate
                logger.debug("Method {}.{} passed bytecode validation",
                    service::class.simpleName, method.name)
            } else {
                logger.debug("Method {}.{} FAILED bytecode validation",
                    service::class.simpleName, method.name)
            }
        }

        return validated
    }

    private fun isSchedulerJobHandle(obj: Any?): Boolean {
        return runCatching {
            val schedulerJobHandleClass = Class.forName(
                "io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle"
            )
            schedulerJobHandleClass.isInstance(obj)
        }.getOrElse { false }
    }

}

/**
 * Validates scheduler methods via bytecode analysis.
 *
 * Ensures method actually calls scheduler methods:
 * - scheduler.scheduleCron(...)
 * - scheduler.schedule(...)
 * - scheduler.scheduleFixedDelay(...)
 */
internal object SchedulerMethodBytecodeValidator {
    private val logger = LoggerFactory.getLogger("SchedulerMethodBytecodeValidator")

    /**
     * Known scheduler method names to look for in bytecode.
     */
    private val SCHEDULER_METHOD_PREFIXES = setOf(
        "scheduleCron",
        "schedule",
        "scheduleFixedDelay"
    )

    /**
     * Validates if a method calls scheduler methods via bytecode inspection.
     *
     * @param method The Java method to validate
     * @return true if method calls a scheduler method, false otherwise
     */
    fun validates(method: java.lang.reflect.Method): Boolean {
        return runCatching {
            val className = method.declaringClass.name.replace('.', '/') + ".class"
            val stream = method.declaringClass.classLoader?.getResourceAsStream(className)
                ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(className)
                ?: return false

            stream.use { input ->
                val reader = ClassReader(input)
                var found = false

                reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?
                    ): MethodVisitor? {
                        val parent = super.visitMethod(access, name, descriptor, signature, exceptions)

                        // Only inspect the target method
                        if (name != method.name) return parent

                        return object : MethodVisitor(Opcodes.ASM9, parent) {
                            override fun visitMethodInsn(
                                opcode: Int,
                                owner: String,
                                methodName: String,
                                descriptor: String,
                                isInterface: Boolean
                            ) {
                                // Check if calling scheduler method
                                if (SCHEDULER_METHOD_PREFIXES.any { methodName.startsWith(it) } &&
                                    owner.contains("SchedulerService")) {
                                    found = true
                                }
                                super.visitMethodInsn(opcode, owner, methodName, descriptor, isInterface)
                            }
                        }
                    }
                }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

                found
            }
        }.getOrElse {
            logger.debug("Bytecode validation failed for {}: {}",
                method.name, it.message)
            false
        }
    }
}
