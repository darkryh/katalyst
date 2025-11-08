package com.ead.katalyst.di.features

import org.koin.core.Koin
import org.koin.core.module.Module

/**
 * Represents an optional Katalyst feature (scheduler, events, websockets, etc.)
 * that can be plugged into the bootstrap process when its module is on the classpath.
 *
 * Each feature can contribute Koin modules up-front via [provideModules] and perform
 * additional wiring (e.g., flag registration, handler binding) once the Koin context
 * is ready via [onKoinReady].
 */
interface KatalystFeature {
    /** Stable identifier mainly used for logging/debugging. */
    val id: String

    /**
     * Modules that should be loaded during bootstrap before auto-discovery runs.
     */
    fun provideModules(): List<Module> = emptyList()

    /**
     * Optional hook executed after Koin is fully initialized.
     */
    fun onKoinReady(koin: Koin) {}
}
