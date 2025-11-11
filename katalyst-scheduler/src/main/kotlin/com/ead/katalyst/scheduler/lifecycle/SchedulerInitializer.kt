package com.ead.katalyst.scheduler.lifecycle

import com.ead.katalyst.core.component.Service
import com.ead.katalyst.di.internal.ServiceRegistry
import com.ead.katalyst.di.lifecycle.ApplicationInitializer
import com.ead.katalyst.scheduler.exception.SchedulerDiscoveryException
import com.ead.katalyst.scheduler.exception.SchedulerInvocationException
import com.ead.katalyst.scheduler.exception.SchedulerServiceNotAvailableException
import com.ead.katalyst.scheduler.exception.SchedulerValidationException
import com.ead.katalyst.scheduler.job.SchedulerJobHandle
import org.koin.core.Koin
import org.objectweb.asm.*
import org.slf4j.LoggerFactory
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

/**
 * Automatically discovers and invokes scheduler registration methods.
 *
 * **Ownership**: This initializer is owned by the scheduler module and handles all
 * scheduler-specific discovery and invocation logic.
 *
 * **Execution Order & Safety Guarantee:**
 * Runs SECOND (order=-50), AFTER StartupValidator (order=-100).
 * StartupValidator ensures database schema is ready, so scheduler methods can
 * safely query tables without crashing.
 *
 * **Important Contract:**
 * At this point in the initialization sequence:
 * ✓ Database connection verified and working
 * ✓ ALL registered tables verified to exist in schema
 * ✓ Safe to invoke scheduler methods that query tables
 * ✓ No additional schema validation needed
 *
 * If StartupValidator threw an exception → this initializer NEVER runs.
 * If we reach this point → schema is guaranteed valid.
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
internal class SchedulerInitializer : ApplicationInitializer {
    private val logger = LoggerFactory.getLogger("SchedulerInitializer")

    override val initializerId: String = "SchedulerInitializer"
    override val order: Int = -50

    override suspend fun onApplicationReady(koin: Koin) {
        logger.info("")
        logger.info("╔════════════════════════════════════════════════════╗")
        logger.info("║ PHASE 2: Scheduler Discovery & Registration       ║")
        logger.info("║ (Database schema guaranteed ready by Phase 1)      ║")
        logger.info("╚════════════════════════════════════════════════════╝")
        logger.info("")

        try {
            // Check SchedulerService availability
            logger.info("Checking SchedulerService availability...")
            val schedulerAvailable = runCatching<Any> {
                koin.get(getSchedulerServiceClass())
            }.isSuccess

            if (!schedulerAvailable) {
                logger.warn("⚠ SchedulerService not available - scheduler disabled")
                logger.info("")
                logger.info("╔════════════════════════════════════════════════════╗")
                logger.info("║ ✓ PHASE 2 SKIPPED: Scheduler not enabled          ║")
                logger.info("╚════════════════════════════════════════════════════╝")
                logger.info("")
                // Log as debug, don't throw - scheduler is optional
                logger.debug("SchedulerService not found in Koin, scheduler will not be initialized")
                return
            }
            logger.info("✓ SchedulerService available")
            logger.info("✓ Database schema ready (validated by StartupValidator)")

            // Get all services from ServiceRegistry (populated during component discovery)
            logger.info("Retrieving services from ServiceRegistry...")
            val allServices = ServiceRegistry.getAll()

            if (allServices.isEmpty()) {
                logger.info("No services found")
                logger.info("")
                logger.info("╔════════════════════════════════════════════════════╗")
                logger.info("║ ✓ PHASE 2 PASSED: No scheduler methods            ║")
                logger.info("╚════════════════════════════════════════════════════╝")
                logger.info("")
                return
            }

            logger.info("Found {} service(s) to scan", allServices.size)
            logger.info("")

            // Step 1: Discover candidate methods by reflection
            logger.info("STEP 1: Discovering candidate methods (reflection)...")
            val candidates = discoverCandidateMethods(allServices)

            if (candidates.isEmpty()) {
                logger.info("No candidate scheduler methods found")
                logger.info("")
                logger.info("╔════════════════════════════════════════════════════╗")
                logger.info("║ ✓ PHASE 2 PASSED: No scheduler methods            ║")
                logger.info("╚════════════════════════════════════════════════════╝")
                logger.info("")
                return
            }

            logger.info("Found {} candidate method(s):", candidates.size)
            candidates.forEach { (service, method) ->
                logger.info("  [Candidate] {}.{}()",
                    service::class.simpleName, method.name)
            }
            logger.info("")

            // Step 2: Validate candidates via bytecode analysis
            logger.info("STEP 2: Validating candidate methods (bytecode analysis)...")
            val validatedMethods = validateCandidatesByBytecode(candidates)

            if (validatedMethods.isEmpty()) {
                logger.warn("⚠ No candidates passed bytecode validation")
                logger.info("")
                logger.info("╔════════════════════════════════════════════════════╗")
                logger.info("║ ✓ PHASE 2 PASSED: No valid scheduler methods      ║")
                logger.info("╚════════════════════════════════════════════════════╝")
                logger.info("")
                return
            }

            logger.info("Validated {} method(s):", validatedMethods.size)
            validatedMethods.forEach { (service, method) ->
                logger.info("  [Valid] {}.{}()",
                    service::class.simpleName, method.name)
            }
            logger.info("")

            // Step 3: Invoke validated methods
            logger.info("STEP 3: Invoking validated methods...")
            var successCount = 0
            var failureCount = 0

            validatedMethods.forEach { (service, method) ->
                val signature = "${service::class.simpleName}.${method.name}()"

                try {
                    logger.info("  → Invoking {}", signature)
                    method.isAccessible = true
                    val result = method.call(service)

                    if (isSchedulerJobHandle(result)) {
                        logger.info("    ✓ {} registered successfully", signature)
                        successCount++
                    } else {
                        logger.error("    ✗ {} returned invalid type: {}",
                            signature, result?.let { it::class.simpleName } ?: "null")
                        failureCount++
                    }
                } catch (e: Exception) {
                    logger.error("    ✗ {} FAILED: {}", signature, e.message)
                    failureCount++
                    throw SchedulerInvocationException(
                        message = "Failed to invoke scheduler method $signature: ${e.message}",
                        cause = e
                    )
                }
            }

            logger.info("")
            logger.info("Invocation Summary: {} success, {} failure",
                successCount, failureCount)

            if (failureCount > 0) {
                throw SchedulerInvocationException(
                    message = "Scheduler invocation encountered $failureCount error(s). See logs above for details."
                )
            }

            logger.info("")
            logger.info("╔════════════════════════════════════════════════════╗")
            logger.info("║ ✓ PHASE 2 PASSED: {} scheduler task(s) registered ║", successCount)
            logger.info("╚════════════════════════════════════════════════════╝")
            logger.info("")

        } catch (e: Exception) {
            logger.error("")
            logger.error("╔════════════════════════════════════════════════════╗")
            logger.error("║ ✗ SCHEDULER INITIALIZATION FAILED                 ║")
            logger.error("║ No scheduled tasks will be registered              ║")
            logger.error("╚════════════════════════════════════════════════════╝")
            logger.error("Reason: {}", e.message)
            logger.error("")
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
    ): Map<Service, kotlin.reflect.KFunction<*>> {
        val candidates = mutableMapOf<Service, kotlin.reflect.KFunction<*>>()

        services.forEach { service ->
            val serviceClass = service::class

            serviceClass.functions
                .filter { function ->
                    // Must return SchedulerJobHandle
                    val isSchedulerJobHandle = runCatching {
                        val schedulerJobHandleClass = Class.forName(
                            "com.ead.katalyst.scheduler.job.SchedulerJobHandle"
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
                    candidates[service] = function
                }
        }

        return candidates
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
        candidates: Map<Service, kotlin.reflect.KFunction<*>>
    ): Map<Service, kotlin.reflect.KFunction<*>> {
        val validated = mutableMapOf<Service, kotlin.reflect.KFunction<*>>()

        candidates.forEach { (service, method) ->
            val javaMethod = service::class.java.declaredMethods
                .find { it.name == method.name }
                ?: return@forEach

            val isValid = runCatching {
                SchedulerMethodBytecodeValidator.validates(javaMethod)
            }.getOrElse { false }

            if (isValid) {
                validated[service] = method
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
                "com.ead.katalyst.scheduler.job.SchedulerJobHandle"
            )
            schedulerJobHandleClass.isInstance(obj)
        }.getOrElse { false }
    }

    private fun getSchedulerServiceClass(): kotlin.reflect.KClass<*> {
        return runCatching {
            Class.forName("com.ead.katalyst.scheduler.service.SchedulerService").kotlin
        }.getOrNull() ?: throw IllegalStateException("SchedulerService class not found")
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
    private val SCHEDULER_METHOD_NAMES = setOf(
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
                                if (methodName in SCHEDULER_METHOD_NAMES &&
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
