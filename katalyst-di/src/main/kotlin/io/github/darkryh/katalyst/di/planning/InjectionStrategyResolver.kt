package io.github.darkryh.katalyst.di.planning

import kotlin.reflect.KParameter

/**
 * Centralizes the supported automatic parameter strategies.
 *
 * The resolver deliberately does not create hidden proxies. It reflects the
 * current supported behavior: supplied framework values, direct lookup,
 * Kotlin defaults, nullable nulls, or a fatal missing dependency.
 */
object InjectionStrategyResolver {
    fun strategyFor(parameter: KParameter, hasSuppliedValue: Boolean, hasBinding: Boolean): InjectionStrategy =
        when {
            hasSuppliedValue -> InjectionStrategy.SUPPLIED
            hasBinding -> InjectionStrategy.DIRECT
            parameter.isOptional -> InjectionStrategy.DEFAULT_VALUE
            parameter.type.isMarkedNullable -> InjectionStrategy.NULL_VALUE
            else -> InjectionStrategy.MISSING_REQUIRED
        }
}

enum class InjectionStrategy {
    SUPPLIED,
    DIRECT,
    DEFAULT_VALUE,
    NULL_VALUE,
    MISSING_REQUIRED
}
