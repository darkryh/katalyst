package io.github.darkryh.katalyst.di.injection.internal

import io.github.darkryh.katalyst.di.analysis.InjectionMode
import io.github.darkryh.katalyst.di.injection.Provider
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

internal data class DependencyRequest(
    val requestedType: KClass<*>,
    val targetType: KClass<*>,
    val targetNullable: Boolean,
    val isDeferred: Boolean,
    val mode: InjectionMode
)

internal fun parseDependencyRequest(type: KType): DependencyRequest {
    val requestedType = type.jvmErasure

    fun targetAt(index: Int): KType? = type.arguments.getOrNull(index)?.type
    fun targetClass(index: Int): KClass<*>? = targetAt(index)?.classifier as? KClass<*>

    return when (requestedType) {
        Provider::class -> {
            val targetType = targetClass(0)
                ?: throw IllegalStateException("Provider dependency requires a concrete generic type: $type")
            DependencyRequest(
                requestedType = requestedType,
                targetType = targetType,
                targetNullable = targetAt(0)?.isMarkedNullable == true,
                isDeferred = true,
                mode = InjectionMode.PROVIDER
            )
        }

        Lazy::class -> {
            val targetType = targetClass(0)
                ?: throw IllegalStateException("Lazy dependency requires a concrete generic type: $type")
            DependencyRequest(
                requestedType = requestedType,
                targetType = targetType,
                targetNullable = targetAt(0)?.isMarkedNullable == true,
                isDeferred = true,
                mode = InjectionMode.LAZY
            )
        }

        Function0::class -> {
            val targetType = targetClass(0)
                ?: throw IllegalStateException("Function0 dependency requires a concrete return type: $type")
            DependencyRequest(
                requestedType = requestedType,
                targetType = targetType,
                targetNullable = targetAt(0)?.isMarkedNullable == true,
                isDeferred = true,
                mode = InjectionMode.FUNCTION
            )
        }

        else -> DependencyRequest(
            requestedType = requestedType,
            targetType = requestedType,
            targetNullable = type.isMarkedNullable,
            isDeferred = false,
            mode = InjectionMode.DIRECT
        )
    }
}
