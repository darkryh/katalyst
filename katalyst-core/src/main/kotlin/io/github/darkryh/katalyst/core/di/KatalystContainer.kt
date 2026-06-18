package io.github.darkryh.katalyst.core.di

import kotlin.reflect.KClass

/**
 * Minimal Katalyst-owned dependency resolution contract.
 *
 * Framework integration code should depend on this facade instead of a concrete
 * DI engine when parameters/scopes are not required.
 */
interface KatalystContainer {
    fun <T : Any> get(type: KClass<T>, qualifier: String? = null): T

    fun <T : Any> getOrNull(type: KClass<T>, qualifier: String? = null): T?

    fun <T : Any> getAll(type: KClass<T>): List<T>

    fun contains(type: KClass<*>, qualifier: String? = null): Boolean
}

inline fun <reified T : Any> KatalystContainer.get(qualifier: String? = null): T =
    get(T::class, qualifier)

inline fun <reified T : Any> KatalystContainer.getOrNull(qualifier: String? = null): T? =
    getOrNull(T::class, qualifier)

inline fun <reified T : Any> KatalystContainer.getAll(): List<T> =
    getAll(T::class)

inline fun <reified T : Any> KatalystContainer.contains(qualifier: String? = null): Boolean =
    contains(T::class, qualifier)
