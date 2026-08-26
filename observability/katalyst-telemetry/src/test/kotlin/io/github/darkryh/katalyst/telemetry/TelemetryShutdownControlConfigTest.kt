package io.github.darkryh.katalyst.telemetry

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the default and the opt-out for remote shutdown control.
 *
 * The default is a deliberate decision, not an accident of writing `?: true`: on, because the
 * inspector's `/shutdown` is only honest while the backend will answer it, and the endpoint sits
 * behind the same loopback + per-run-token gate that already guards a snapshot of the application's
 * entire internal state. Off is one property away for anyone who draws that line differently, and
 * that switch has to keep working — an operator who turns it off and is not obeyed has a much worse
 * problem than the command that started all this.
 */
class TelemetryShutdownControlConfigTest {

    private val property = "katalyst.telemetry.shutdownControl"

    @AfterTest
    fun tearDown() {
        System.clearProperty(property)
    }

    @Test
    fun `is on by default`() {
        System.clearProperty(property)

        assertTrue(TelemetryConfig.fromEnvironment().shutdownControlEnabled)
    }

    @Test
    fun `is switched off by an explicit false`() {
        System.setProperty(property, "false")

        assertFalse(TelemetryConfig.fromEnvironment().shutdownControlEnabled)
    }

    @Test
    fun `only an explicit false switches it off`() {
        // Opt-OUT, matching how telemetry's own `enabled` flag reads: anything that is not "false"
        // leaves the capability on, so a typo cannot quietly disarm the inspector.
        listOf("true", "TRUE", "yes", "", "off").forEach { value ->
            System.setProperty(property, value)

            assertTrue(
                TelemetryConfig.fromEnvironment().shutdownControlEnabled,
                "'$value' is not 'false' and must leave shutdown control on",
            )
        }
    }

    @Test
    fun `false is matched case-insensitively`() {
        listOf("false", "False", "FALSE").forEach { value ->
            System.setProperty(property, value)

            assertFalse(TelemetryConfig.fromEnvironment().shutdownControlEnabled, value)
        }
    }
}
