package io.github.darkryh.katalyst.di.model

import kotlin.reflect.KClass

/**
 * Stable key for a Katalyst binding.
 *
 * Qualifiers are intentionally modeled here instead of being carried as
 * ad hoc strings across analyzers, validators, and invokers.
 */
data class TypeKey(
    val type: KClass<*>,
    val qualifier: String? = null
) {
    override fun toString(): String =
        qualifier?.let { "${type.qualifiedName ?: type.simpleName}[qualifier=$it]" }
            ?: (type.qualifiedName ?: type.simpleName ?: "Unknown")
}
