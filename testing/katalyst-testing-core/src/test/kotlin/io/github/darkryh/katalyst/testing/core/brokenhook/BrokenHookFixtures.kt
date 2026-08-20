package io.github.darkryh.katalyst.testing.core.brokenhook

import io.github.darkryh.katalyst.di.lifecycle.StartupHook

/**
 * A plain class that is not a Component/Service and is never registered in the container,
 * so nothing can resolve it.
 */
class UnregisteredCollaborator

/**
 * A bare [StartupHook] whose dependency cannot be resolved.
 *
 * Lives in its own package so package scanning in the happy-path tests does not pick it up.
 * Bootstrap must fail loudly rather than skipping the hook.
 */
class UnsatisfiableStartupHook(
    @Suppress("unused") private val missing: UnregisteredCollaborator
) : StartupHook {
    override val id: String = "unsatisfiable-startup"

    override suspend fun onStartup() = Unit
}
