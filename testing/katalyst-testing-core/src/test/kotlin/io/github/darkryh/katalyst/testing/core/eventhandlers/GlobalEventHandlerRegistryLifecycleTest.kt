package io.github.darkryh.katalyst.testing.core.eventhandlers

import io.github.darkryh.katalyst.events.bus.GlobalEventHandlerRegistry
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `GlobalEventHandlerRegistry` is a hand-off buffer: discovery fills it, and the events
 * feature drains it in `onReady`. With the feature disabled nothing drains it, so the
 * discovered handlers — instances bound to the container being built — used to sit there for
 * the rest of the process and get subscribed by whichever later bootstrap did enable events.
 */
class GlobalEventHandlerRegistryLifecycleTest {

    @BeforeTest
    fun setUp() {
        HandledEvents.reset()
    }

    @AfterTest
    fun tearDown() {
        HandledEvents.reset()
    }

    @Test
    fun `handlers discovered with the events feature disabled do not outlive the bootstrap`() {
        val environment = katalystTestEnvironment {
            scan("io.github.darkryh.katalyst.testing.core.eventhandlers")
            features {
                disableEvents()
                disableScheduler()
            }
        }

        // Without this the assertion below would also hold if nothing was ever discovered.
        assertTrue(
            GlobalEventHandlerRegistry.size() > 0,
            "the handler should have been discovered and buffered while the container was up"
        )

        environment.close()

        assertEquals(
            0,
            GlobalEventHandlerRegistry.size(),
            "handlers of a stopped container are still buffered and a later bootstrap would subscribe them"
        )
    }
}
