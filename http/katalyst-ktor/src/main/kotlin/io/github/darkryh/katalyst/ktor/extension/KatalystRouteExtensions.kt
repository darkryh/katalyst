@file:Suppress("unused","UnusedReceiverParameter")

package io.github.darkryh.katalyst.ktor.extension

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider

/**
 * Get the active Katalyst container facade.
 *
 * Katalyst application and test bootstraps publish this facade during DI
 * initialization. Applications must install a Katalyst DI adapter module.
 */
fun getKatalystContainer(): KatalystContainer =
    KatalystContainerProvider.current()

inline fun <reified T : Any> Application.ktInject(): Lazy<T> =
    lazy { getKatalystContainer().get(T::class) }


/**
 * Lazily resolve a dependency from the active Katalyst container inside a routing tree.
 */
inline fun <reified T : Any> Route.ktInject(): Lazy<T> =
    lazy { getKatalystContainer().get(T::class) }

/**
 * Resolve a dependency immediately from within a request handler.
 */
inline fun <reified T : Any> ApplicationCall.ktInject(): Lazy<T> =
    lazy { getKatalystContainer().get(T::class) }

/**
 * Accessor for the active Katalyst container from an [Application].
 */
fun Application.katalystContainer(): KatalystContainer = getKatalystContainer()
