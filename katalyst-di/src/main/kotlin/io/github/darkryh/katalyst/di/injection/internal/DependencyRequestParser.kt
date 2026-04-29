package io.github.darkryh.katalyst.di.injection.internal

import io.github.darkryh.katalyst.di.analysis.InjectionMode
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

    return DependencyRequest(
        requestedType = requestedType,
        targetType = requestedType,
        targetNullable = type.isMarkedNullable,
        isDeferred = false,
        mode = InjectionMode.DIRECT
    )
}
