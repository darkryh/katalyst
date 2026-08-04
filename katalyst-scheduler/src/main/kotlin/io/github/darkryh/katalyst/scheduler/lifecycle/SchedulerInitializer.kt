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
import org.objectweb.asm.Type
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

private data class SchedulerMethodCandidate(
    val service: Service,
    val method: KFunction<*>,
)

/**
 * A candidate the framework decided not to register, with the reason it was dropped.
 *
 * Rejections are reported at WARN and counted in the completion summary: losing a job must never
 * be silent, but it must not stop the application from booting either.
 */
private data class SchedulerMethodRejection(
    val candidate: SchedulerMethodCandidate,
    val reason: String,
)

/**
 * Discovers scheduler registration methods declared on Katalyst services.
 *
 * The application-facing contract is:
 * - the owning class implements [Service]
 * - the scheduler method is public (private methods are skipped, and reported)
 * - the scheduler method returns [SchedulerJobHandle]
 * - the method schedules through `requireScheduler()`, either directly or through a helper on
 *   the same class hierarchy (see [SchedulerMethodBytecodeValidator])
 *
 * The method may be declared on a base class, and it may suspend: both are invoked normally.
 */
internal class SchedulerInitializer : ReadyHook {
    private val logger = LoggerFactory.getLogger("SchedulerInitializer")

    override val id: String = "SchedulerInitializer"
    override val order: Int = -50

    /**
     * Methods reached while proving an accepted candidate registers jobs.
     *
     * A private helper in this set is not a dropped scheduler method - it is the delegate of one -
     * so it must not be reported as a rejection.
     */
    private val delegationTargets = mutableSetOf<SchedulerMethodBytecodeValidator.MethodRef>()

    override suspend fun onReady() {
        try {
            val allServices = ServiceRegistry.getAll()
            logger.info("Scheduler runtime-ready initialization started")
            logger.debug("Scheduler scanning {} service instance(s)", allServices.size)

            if (allServices.isEmpty()) {
                logger.info("Scheduler initialization completed: no services available for scanning")
                return
            }

            delegationTargets.clear()
            val candidates = discoverCandidateMethods(allServices)
            val validatedMethods = validateCandidatesByBytecode(candidates)
            val rejections = collectRejections(allServices, candidates, validatedMethods)

            rejections.forEach { rejection ->
                // A rejection is never fatal - the application keeps booting - but losing a job
                // has to be visible, so it is reported here and counted in the summary below.
                logger.warn(
                    "Scheduler service method not registered: {} {}",
                    signatureOf(rejection.candidate),
                    rejection.reason,
                )
            }

            if (validatedMethods.isEmpty()) {
                logger.debug("No scheduler service method survived discovery and validation")
                logCompletion(registrations = 0, rejections = rejections.size, failures = 0)
                return
            }

            val container = KatalystContainerProvider.currentOrNull()
            val resolver = container?.let(::ParameterResolver)
            var successCount = 0
            var failureCount = 0
            val failureDetails = mutableListOf<String>()

            validatedMethods.forEach { candidate ->
                val service = candidate.service
                val method = candidate.method
                val signature = signatureOf(candidate)

                try {
                    logger.debug("Invoking scheduler service method {}", signature)
                    val result = invokeSchedulerMethod(service, method, resolver, signature)

                    if (result is SchedulerJobHandle) {
                        logger.debug("Scheduler service method registered successfully: {}", signature)
                        successCount++
                    } else {
                        val reason = "$signature returned ${result?.let { it::class.simpleName } ?: "null"} " +
                            "instead of SchedulerJobHandle"
                        logger.error(
                            "Scheduler service method returned invalid type: {} -> {}",
                            signature,
                            result?.let { it::class.simpleName } ?: "null",
                        )
                        failureCount++
                        failureDetails += reason
                    }
                } catch (e: Exception) {
                    // Isolate this candidate's failure: log it and keep registering the rest.
                    // The aggregate failure is surfaced once, after all candidates are attempted, below.
                    // `message` is null for plenty of exception types, so fall back to toString():
                    // an aggregate that reads "...: null" tells the application nothing.
                    val cause = e.message ?: e.toString()
                    logger.error("Scheduler service method failed: {} - {}", signature, cause)
                    failureCount++
                    failureDetails += "$signature: $cause"
                }
            }

            logCompletion(
                registrations = successCount,
                rejections = rejections.size,
                failures = failureCount,
            )

            if (failureCount > 0) {
                throw SchedulerInvocationException(
                    message = "Scheduler invocation encountered $failureCount error(s): " +
                        failureDetails.joinToString("; "),
                )
            }
        } catch (e: Exception) {
            logger.error("Scheduler runtime-ready initialization failed: {}", e.message ?: e.toString())
            throw e
        }
    }

    /**
     * Invokes one scheduler method, resolving its parameters from the container.
     *
     * Suspend methods take a separate path: [KFunction.callBy] cannot invoke one (it has no
     * continuation to pass) and fails with an exception that carries no useful message. [onReady]
     * is itself suspending, so the arguments are resolved through the same [ParameterResolver]
     * [CallableInvoker] would use and the call is completed with `callSuspendBy` from here.
     */
    private suspend fun invokeSchedulerMethod(
        service: Service,
        method: KFunction<*>,
        resolver: ParameterResolver?,
        signature: String,
    ): Any? {
        val ownerDescription = "scheduler method $signature"

        if (!method.isSuspend) {
            return CallableInvoker.callMemberWithDefaults(
                instance = service,
                function = method,
                resolver = resolver,
                ownerDescription = ownerDescription,
            )
        }

        method.isAccessible = true
        val instanceParameter = method.parameters
            .firstOrNull { it.kind == KParameter.Kind.INSTANCE }
            ?: throw IllegalArgumentException("Cannot invoke ${method.name}: missing instance parameter")

        val supplied = mapOf<KParameter, Any?>(instanceParameter to service)
        val arguments = resolver?.resolveParameters(
            function = method,
            suppliedParameters = supplied,
            ownerDescription = ownerDescription,
        ) ?: supplied

        return try {
            method.callSuspendBy(arguments)
        } catch (error: InvocationTargetException) {
            throw error.targetException ?: error
        }
    }

    private fun discoverCandidateMethods(services: List<Service>): List<SchedulerMethodCandidate> {
        val candidates = mutableListOf<SchedulerMethodCandidate>()

        services.forEach { service ->
            // `functions` includes inherited members, so a scheduler method declared on a base
            // service class is a candidate too.
            service::class.functions
                .filter { function ->
                    function.registersSchedulerJobs() && function.visibility != KVisibility.PRIVATE
                }
                .forEach { function ->
                    candidates += SchedulerMethodCandidate(service, function)
                }
        }

        return candidates.sorted()
    }

    private fun validateCandidatesByBytecode(
        candidates: List<SchedulerMethodCandidate>,
    ): List<SchedulerMethodCandidate> =
        candidates.filter { candidate ->
            // Resolve the JVM method from the KFunction: looking it up by name on the concrete
            // class drops methods inherited from a base class and can pick the wrong overload.
            val javaMethod = candidate.method.javaMethod ?: return@filter false

            val outcome = SchedulerMethodBytecodeValidator.validate(
                method = javaMethod,
                serviceClass = candidate.service::class.java,
            )
            if (outcome.valid) {
                delegationTargets += outcome.visited
            }

            logger.debug(
                "Method {}.{} bytecode validation: {}",
                candidate.service::class.simpleName,
                candidate.method.name,
                outcome.valid,
            )
            outcome.valid
        }

    /**
     * Collects every candidate the framework decided not to register, with a concrete reason.
     *
     * Private helpers that an accepted method delegates to are deliberately not rejections: they
     * are how that method registers its jobs.
     */
    private fun collectRejections(
        services: List<Service>,
        candidates: List<SchedulerMethodCandidate>,
        validated: List<SchedulerMethodCandidate>,
    ): List<SchedulerMethodRejection> {
        val rejections = mutableListOf<SchedulerMethodRejection>()

        // Compared by service *identity*: a service that implements equals() must not make one
        // instance's accepted method hide another instance's rejected one.
        val wasAccepted = { candidate: SchedulerMethodCandidate ->
            validated.any { it.service === candidate.service && it.method == candidate.method }
        }

        candidates.filterNot(wasAccepted).forEach { candidate ->
            val reason = if (candidate.method.javaMethod == null) {
                "cannot be resolved to a JVM method"
            } else {
                "does not reach ServiceScheduler.jobs within " +
                    "${SchedulerMethodBytecodeValidator.MAX_DELEGATION_DEPTH} calls"
            }
            rejections += SchedulerMethodRejection(candidate, reason)
        }

        rejections += discoverPrivateCandidates(services)
            .filterNot { candidate -> candidate.isDelegationTarget() }
            .map { candidate ->
                SchedulerMethodRejection(candidate, "is private (scheduler methods must be public)")
            }

        return rejections.sortedBy { signatureOf(it.candidate) }
    }

    private fun discoverPrivateCandidates(services: List<Service>): List<SchedulerMethodCandidate> =
        services.flatMap { service ->
            service::class.functions
                .filter { function ->
                    function.registersSchedulerJobs() && function.visibility == KVisibility.PRIVATE
                }
                .map { function -> SchedulerMethodCandidate(service, function) }
        }.sorted()

    private fun SchedulerMethodCandidate.isDelegationTarget(): Boolean {
        val javaMethod = method.javaMethod ?: return false
        return SchedulerMethodBytecodeValidator.refOf(javaMethod) in delegationTargets
    }

    private fun logCompletion(registrations: Int, rejections: Int, failures: Int) {
        logger.info(
            "Scheduler initialization completed: {} registration(s), {} rejection(s), {} failure(s)",
            registrations,
            rejections,
            failures,
        )
    }

    private fun signatureOf(candidate: SchedulerMethodCandidate): String =
        "${candidate.service::class.simpleName}.${candidate.method.name}()"

    private fun KFunction<*>.registersSchedulerJobs(): Boolean =
        SchedulerJobHandle::class.java.isAssignableFrom(returnType.jvmErasure.java)

    private fun List<SchedulerMethodCandidate>.sorted(): List<SchedulerMethodCandidate> =
        sortedWith(
            compareBy<SchedulerMethodCandidate>(
                { it.service::class.qualifiedName ?: it.service::class.simpleName ?: "" },
                { it.method.name },
            ),
        )
}

/**
 * Decides whether a candidate method really registers scheduler jobs, by reading its bytecode.
 *
 * A method qualifies when it invokes one of the scheduler DSL entry points on `SchedulerService`
 * or `ServiceScheduler`. That call does not have to be in the method's own body: registration is
 * frequently delegated to a helper, so calls to other methods **of the same class hierarchy** are
 * followed too, up to [MAX_DELEGATION_DEPTH] hops. Every `(owner, name, descriptor)` triple is
 * recorded as it is visited, which both memoises the walk and makes a cycle (`a -> b -> a`)
 * terminate instead of recursing forever.
 *
 * The guard exists so that any method returning a `SchedulerJobHandle` is not blindly invoked at
 * startup; only calls that stay inside the candidate's own hierarchy are followed, so unrelated
 * library code is never walked.
 */
internal object SchedulerMethodBytecodeValidator {
    private val logger = LoggerFactory.getLogger("SchedulerMethodBytecodeValidator")
    private val schedulerMethodNames = setOf("jobs", "scheduleCron", "schedule", "scheduleFixedDelay")

    /** How many delegating calls the scan follows before giving up on a candidate. */
    const val MAX_DELEGATION_DEPTH: Int = 3

    /** A JVM method identity: the descriptor is what keeps overloads apart. */
    internal data class MethodRef(
        val owner: String,
        val name: String,
        val descriptor: String,
    )

    internal data class Outcome(
        val valid: Boolean,
        val visited: Set<MethodRef>,
    )

    fun refOf(method: Method): MethodRef = MethodRef(
        owner = Type.getInternalName(method.declaringClass),
        name = method.name,
        descriptor = Type.getMethodDescriptor(method),
    )

    fun validate(method: Method, serviceClass: Class<*>): Outcome =
        runCatching {
            val loader = serviceClass.classLoader
                ?: method.declaringClass.classLoader
                ?: Thread.currentThread().contextClassLoader
                ?: return@runCatching Outcome(valid = false, visited = emptySet())

            // The concrete service and the declaring class can differ (inherited methods), and
            // both ends of that chain are legitimate delegation targets.
            val hierarchy = hierarchyOf(serviceClass) + hierarchyOf(method.declaringClass)
            val visited = linkedSetOf<MethodRef>()
            val valid = reachesScheduler(refOf(method), loader, hierarchy, visited, depth = 0)

            Outcome(valid = valid, visited = visited)
        }.getOrElse { error ->
            logger.debug("Bytecode validation failed for {}: {}", method.name, error.message)
            Outcome(valid = false, visited = emptySet())
        }

    private fun reachesScheduler(
        ref: MethodRef,
        loader: ClassLoader,
        hierarchy: Set<String>,
        visited: MutableSet<MethodRef>,
        depth: Int,
    ): Boolean {
        if (ref.owner !in hierarchy) return false
        // Already seen: either it is being walked right now (a cycle) or it was walked and did
        // not reach the scheduler. Either way, stop.
        if (!visited.add(ref)) return false

        val stream = loader.getResourceAsStream("${ref.owner}.class") ?: return false

        var found = false
        var bodyFound = false
        var superName: String? = null
        val delegates = mutableListOf<MethodRef>()

        stream.use { input ->
            ClassReader(input).accept(
                object : ClassVisitor(Opcodes.ASM9) {
                    override fun visit(
                        version: Int,
                        access: Int,
                        name: String,
                        signature: String?,
                        superClassName: String?,
                        interfaces: Array<out String>?,
                    ) {
                        superName = superClassName
                    }

                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?,
                    ): MethodVisitor? {
                        if (name != ref.name || descriptor != ref.descriptor) return null
                        bodyFound = true

                        return object : MethodVisitor(Opcodes.ASM9) {
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
                                } else if (owner in hierarchy) {
                                    delegates += MethodRef(owner, methodName, descriptor)
                                }
                            }
                        }
                    }
                },
                ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
            )
        }

        if (found) return true

        // A call site names the *static* receiver type, which is not necessarily where the body
        // lives. Walking up to the declaring class is resolution, not delegation, so it does not
        // consume depth.
        if (!bodyFound) {
            val parent = superName ?: return false
            return reachesScheduler(ref.copy(owner = parent), loader, hierarchy, visited, depth)
        }

        if (depth >= MAX_DELEGATION_DEPTH) return false
        return delegates.any { delegate ->
            reachesScheduler(delegate, loader, hierarchy, visited, depth + 1)
        }
    }

    /** Internal names of a class, its superclasses and every interface they implement. */
    private fun hierarchyOf(type: Class<*>): Set<String> {
        val names = linkedSetOf<String>()
        var current: Class<*>? = type

        while (current != null && current != Any::class.java) {
            names += Type.getInternalName(current)
            current.interfaces.forEach { collectInterfaces(it, names) }
            current = current.superclass
        }

        return names
    }

    private fun collectInterfaces(type: Class<*>, into: MutableSet<String>) {
        val internalName = Type.getInternalName(type)
        if (!into.add(internalName)) return
        // Kotlin compiles interface method bodies into a nested DefaultImpls class.
        into += "$internalName\$DefaultImpls"
        type.interfaces.forEach { collectInterfaces(it, into) }
    }
}
