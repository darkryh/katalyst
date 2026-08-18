package io.github.darkryh.katalyst.di.invocation

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.exception.DependencyInjectionException
import io.github.darkryh.katalyst.di.injection.InjectNamed
import io.github.darkryh.katalyst.di.injection.internal.DependencyRequest
import io.github.darkryh.katalyst.di.injection.internal.parseDependencyRequest
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import io.github.darkryh.katalyst.core.exception.BeanNotFoundException

private val logger = LoggerFactory.getLogger("ParameterResolver")

/**
 * Resolves Kotlin callable parameters for Katalyst-owned reflective invocation.
 *
 * The resolver intentionally supports normal dependency parameters first:
 * supplied framework receivers, direct container bindings, Kotlin defaults, and nullable
 * missing values. Framework call sites should add new behavior here rather than
 * implementing their own parameter rules.
 */
class ParameterResolver(
    private val container: KatalystContainer
) {
    fun resolveParameters(
        function: KFunction<*>,
        suppliedParameters: Map<KParameter, Any?> = emptyMap(),
        ownerDescription: String
    ): Map<KParameter, Any?> {
        val args = suppliedParameters.toMutableMap()

        function.parameters.forEach { parameter ->
            if (parameter in args) return@forEach
            if (parameter.kind != KParameter.Kind.VALUE) return@forEach

            val resolved = resolveValueParameter(parameter, ownerDescription)
            if (resolved.shouldSupply) {
                args[parameter] = resolved.value
            }
        }

        return args
    }

    private fun resolveValueParameter(
        parameter: KParameter,
        ownerDescription: String
    ): ResolvedParameter {
        val request = parseDependencyRequest(parameter.type)
        val qualifierName = parameter.findAnnotation<InjectNamed>()?.value

        var resolutionFailure: Throwable? = null
        val resolved = try {
            @Suppress("UNCHECKED_CAST")
            container.get(request.targetType as KClass<Any>, qualifier = qualifierName)
        } catch (e: Exception) {
            // The binding may genuinely be absent (fall back to default/null below), or the
            // container may have failed while actually constructing/resolving the binding
            // (a real DI failure). Either way we don't swallow it here: if resolution ends up
            // required and non-nullable, this becomes the cause of the exception thrown below
            // so the real failure surfaces instead of being masked by a generic message.
            resolutionFailure = e
            null
        }
        if (resolved != null) return ResolvedParameter.supply(resolved)

        if (parameter.isOptional) {
            reportDroppedFailure(
                failure = resolutionFailure,
                parameter = parameter,
                request = request,
                qualifierName = qualifierName,
                ownerDescription = ownerDescription,
                fallbackDescription = "its declared Kotlin default",
            )
            return ResolvedParameter.skipDefault()
        }

        if (request.targetNullable) {
            reportDroppedFailure(
                failure = resolutionFailure,
                parameter = parameter,
                request = request,
                qualifierName = qualifierName,
                ownerDescription = ownerDescription,
                fallbackDescription = "null",
            )
            return ResolvedParameter.supply(null)
        }

        val parameterName = parameter.name?.let { " parameter '$it'" } ?: " parameter"
        val qualifierHint = qualifierName?.let { " with qualifier '$it'" } ?: ""
        throw DependencyInjectionException(
            "Cannot resolve${parameterName} of type ${request.targetType.qualifiedName}$qualifierHint for $ownerDescription",
            cause = resolutionFailure
        )
    }

    /**
     * Reports a container failure that the optional/nullable fallback is about to discard.
     *
     * An optional or nullable parameter exists so a component can be built *without* a
     * dependency, so "nothing is bound for this type" is routine and stays at DEBUG. A binding
     * that exists and blows up while being produced is not routine: the component is built
     * anyway, with a default or `null` in place of a service that was supposed to be there, and
     * before this the captured failure was thrown away with no output at any level.
     */
    private fun reportDroppedFailure(
        failure: Throwable?,
        parameter: KParameter,
        request: DependencyRequest,
        qualifierName: String?,
        ownerDescription: String,
        fallbackDescription: String,
    ) {
        if (failure == null) return

        val parameterName = parameter.name ?: "<unnamed>"
        val targetType = request.targetType.qualifiedName ?: request.targetType.simpleName
        val qualifierHint = qualifierName?.let { " with qualifier '$it'" } ?: ""

        if (failure.isMissingBindingFailure()) {
            logger.debug(
                "No binding for parameter '{}' of type {}{} for {}; using {}",
                parameterName,
                targetType,
                qualifierHint,
                ownerDescription,
                fallbackDescription,
            )
            return
        }

        logger.warn(
            "Parameter '{}' of type {}{} for {} failed to resolve and was replaced by {}: {}: {}. " +
                "{} was built without it.",
            parameterName,
            targetType,
            qualifierHint,
            ownerDescription,
            fallbackDescription,
            failure::class.simpleName,
            failure.message,
            ownerDescription,
            failure,
        )
    }
}

/**
 * Resolves [kClass] from the container, or `null` when it cannot be produced.
 *
 * This drives well-known property injection, so a swallowed failure here means a framework
 * service (the transaction manager, the scheduler) is quietly left unassigned on a component
 * that declared it. A genuinely absent binding is the normal case for an optional framework
 * module and stays at DEBUG; anything else is reported at WARN.
 *
 * @param ownerDescription what the value was being resolved for, used in the diagnostic only.
 */
internal fun KatalystContainer.getFromContainerOrNull(
    kClass: KClass<*>,
    qualifierName: String? = null,
    ownerDescription: String? = null,
): Any? =
    try {
        @Suppress("UNCHECKED_CAST")
        get(
            kClass as KClass<Any>,
            qualifier = qualifierName
        )
    } catch (error: Exception) {
        val owner = ownerDescription?.let { " for $it" } ?: ""
        val qualifierHint = qualifierName?.let { " with qualifier '$it'" } ?: ""
        if (error.isMissingBindingFailure()) {
            logger.debug(
                "No binding for {}{}{}; leaving it unassigned",
                kClass.qualifiedName ?: kClass.simpleName,
                qualifierHint,
                owner,
            )
        } else {
            logger.warn(
                "Could not resolve {}{}{}; it will be left unassigned. Reason: {}: {}",
                kClass.qualifiedName ?: kClass.simpleName,
                qualifierHint,
                owner,
                error::class.simpleName,
                error.message,
                error,
            )
        }
        null
    }

/**
 * Whether [this] means "the container has no binding for that type", as opposed to "the container
 * has a binding and it failed while producing the instance".
 *
 * Matched by exception name because katalyst-di must not depend on any concrete bean engine: Koin
 * raises `NoDefinitionFoundException` for the routine case and `InstanceCreationException` for the
 * real one, while a plain container implementation typically raises `NoSuchElementException` or an
 * `IllegalStateException("No bean registered for ...")`. Only the top-level throwable is inspected
 * on purpose - an `InstanceCreationException` *caused by* a missing nested dependency is still a
 * real failure of an existing binding and must not be downgraded.
 */
internal fun Throwable.isMissingBindingFailure(): Boolean {
    // The typed answer, and the only one that matters for a Katalyst container: every engine
    // raises BeanNotFoundException for "nothing is registered for this type". Checked first and
    // by type, so the classification cannot be broken by rewording a message — this decides
    // whether an unsatisfied optional dependency is a routine DEBUG note or a WARN, and getting
    // it wrong in the other direction would turn every optional dependency into a loud failure.
    if (this is BeanNotFoundException) return true

    // Fallback for exceptions raised outside a Katalyst container — a raw Koin lookup in
    // application code, or a future engine that has not been routed through the contract yet.
    // Deliberately kept: it is a heuristic, but a missing-bean failure misreported as a hard
    // error is worse than a slightly permissive match.
    val name = this::class.simpleName ?: this::class.java.simpleName
    if (name.contains("NoSuchElement") ||
        name.contains("NoDefinitionFound") ||
        name.contains("NoBeanDefFound") ||
        name.contains("DefinitionNotFound")
    ) {
        return true
    }

    val message = message ?: return false
    return message.contains("No definition found", ignoreCase = true) ||
        message.contains("No bean registered", ignoreCase = true) ||
        message.contains("No binding registered", ignoreCase = true)
}

private data class ResolvedParameter(
    val shouldSupply: Boolean,
    val value: Any?
) {
    companion object {
        fun supply(value: Any?): ResolvedParameter = ResolvedParameter(true, value)
        fun skipDefault(): ResolvedParameter = ResolvedParameter(false, null)
    }
}
