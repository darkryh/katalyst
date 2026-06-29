package io.github.darkryh.katalyst.di.lifecycle.test

import io.github.darkryh.katalyst.di.lifecycle.StartupHook

/**
 * Test fixture for StartupHook.
 *
 * Allows tests to create hooks with configurable behavior:
 * - Custom ID
 * - Custom order
 * - Custom onReady logic
 */
class TestStartupHook(
    id: String,
    override val order: Int = 0,
    private val onReady: suspend () -> Unit = {}
) : StartupHook {
    override val id: String = id

    override suspend fun onStartup() {
        onReady()
    }
}
