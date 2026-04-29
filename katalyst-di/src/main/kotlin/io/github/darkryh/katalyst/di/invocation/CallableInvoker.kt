package io.github.darkryh.katalyst.di.invocation

import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.isAccessible

/**
 * Shared Kotlin callable invocation helper for framework-owned reflective calls.
 *
 * The first supported use case is invoking member functions while preserving
 * Kotlin default arguments through [KFunction.callBy]. Additional parameter
 * resolution should be added here rather than reimplemented in scheduler, route,
 * lifecycle, or config-loader code paths.
 */
object CallableInvoker {
    fun <T> callConstructor(
        constructor: KFunction<T>,
        resolver: ParameterResolver,
        ownerDescription: String
    ): T {
        constructor.isAccessible = true
        return constructor.callBy(
            resolver.resolveParameters(
                function = constructor,
                ownerDescription = ownerDescription
            )
        )
    }

    fun callMemberWithDefaults(
        instance: Any,
        function: KFunction<*>,
        resolver: ParameterResolver? = null,
        ownerDescription: String = function.name
    ): Any? {
        function.isAccessible = true
        val instanceParameter = function.parameters
            .firstOrNull { it.kind == KParameter.Kind.INSTANCE }
            ?: throw IllegalArgumentException(
                "Cannot invoke ${function.name}: missing instance parameter"
            )

        val supplied = mapOf(instanceParameter to instance)
        val args = resolver?.resolveParameters(
            function = function,
            suppliedParameters = supplied,
            ownerDescription = ownerDescription
        ) ?: supplied

        return function.callBy(args)
    }

    fun callFunction(
        function: KFunction<*>,
        suppliedParameters: Map<KParameter, Any?>,
        resolver: ParameterResolver,
        ownerDescription: String
    ): Any? {
        function.isAccessible = true
        return function.callBy(
            resolver.resolveParameters(
                function = function,
                suppliedParameters = suppliedParameters,
                ownerDescription = ownerDescription
            )
        )
    }
}
