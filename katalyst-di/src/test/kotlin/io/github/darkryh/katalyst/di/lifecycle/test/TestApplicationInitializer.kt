package io.github.darkryh.katalyst.di.lifecycle.test

import io.github.darkryh.katalyst.di.lifecycle.ApplicationInitializer
import org.koin.core.Koin

/**
 * Test fixture for ApplicationInitializer.
 *
 * Allows tests to create initializers with configurable behavior:
 * - Custom ID
 * - Custom order
 * - Custom onReady logic
 */
class TestApplicationInitializer(
    id: String,
    override val order: Int = 0,
    private val onReady: suspend () -> Unit = {}
) : ApplicationInitializer {
    override val initializerId: String = id

    override suspend fun onApplicationReady(koin: Koin) {
        onReady()
    }
}
