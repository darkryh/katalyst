package io.github.darkryh.katalyst.di.invocation

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.exception.DependencyInjectionException
import io.github.darkryh.katalyst.di.injection.InjectNamed
import io.github.darkryh.katalyst.di.injection.internal.parseDependencyRequest
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation

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

        val resolved = container.getFromContainerOrNull(request.targetType, qualifierName)
        if (resolved != null) return ResolvedParameter.supply(resolved)

        if (parameter.isOptional) {
            return ResolvedParameter.skipDefault()
        }

        if (request.targetNullable) {
            return ResolvedParameter.supply(null)
        }

        val parameterName = parameter.name?.let { " parameter '$it'" } ?: " parameter"
        val qualifierHint = qualifierName?.let { " with qualifier '$it'" } ?: ""
        throw DependencyInjectionException(
            "Cannot resolve${parameterName} of type ${request.targetType.qualifiedName}$qualifierHint for $ownerDescription"
        )
    }
}

internal fun KatalystContainer.getFromContainerOrNull(kClass: KClass<*>, qualifierName: String? = null): Any? =
    try {
        @Suppress("UNCHECKED_CAST")
        get(
            kClass as KClass<Any>,
            qualifier = qualifierName
        )
    } catch (_: Exception) {
        null
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
