package io.github.darkryh.katalyst.ktor

import io.ktor.server.application.Application

/**
 * Marker for components that contribute to the Ktor pipeline.
 * Discovered modules are invoked automatically once the application is ready.
 */
interface KtorModule {
    val order: Int get() = 0
    fun install(application: Application)
}
