package io.github.darkryh.katalyst.telemetry

import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression coverage for the resource leak fixed alongside [TelemetryFeature.installShutdownHook]:
 * the feature used to start the loopback CIO transport and never stop it (`server` was write-only).
 *
 * [TelemetryFeature] is a process-wide singleton that only ever stops its transport from a real JVM
 * shutdown hook, and there is no practical way to force an actual JVM exit from a unit test. Instead,
 * this test drives [TelemetryFeature.shutdownHookActionForTest] — an internal seam that runs exactly
 * the same action ([TelemetryFeature]'s private `stopTransport()`) the registered shutdown hook
 * invokes — and asserts the transport's OS port is genuinely released afterwards. What is NOT covered
 * here (and would require a subprocess/integration-level test) is that `Runtime.addShutdownHook`
 * registration itself fires correctly on real process exit; that wiring was verified by code reading,
 * not by test.
 */
class TelemetryFeatureShutdownTest {

    private val loopback = InetAddress.getByName("127.0.0.1")

    @AfterTest
    fun cleanup() {
        System.clearProperty("katalyst.telemetry.enabled")
        // Always stop whatever transport this test attached so it never leaks into other tests.
        TelemetryFeature.shutdownHookActionForTest()
    }

    @Test
    fun `shutdown hook action stops the transport and releases its bound port`() {
        System.setProperty("katalyst.telemetry.enabled", "true")

        val modules = TelemetryFeature.provideBeanModules()
        assertTrue(modules.isNotEmpty(), "telemetry should attach when enabled")

        val port = TelemetryStore.active?.snapshot()?.meta?.telemetryPort
        assertTrue(port != null && port > 0, "telemetry transport must bind a concrete loopback port")

        // While the transport is live, the port must still be occupied on the loopback address (bind
        // attempt fails). Bound explicitly to 127.0.0.1 — a wildcard-address ServerSocket(port) can
        // succeed alongside an already-bound specific address on some platforms and would defeat this
        // check.
        val bindWhileRunning = runCatching { ServerSocket(port, 1, loopback).close() }
        assertTrue(bindWhileRunning.isFailure, "port must still be held by the live transport")

        // Run exactly the action the JVM shutdown hook installed by attach() would run.
        TelemetryFeature.shutdownHookActionForTest()

        // After stop, the port must have been released back to the OS.
        val bindAfterStop = runCatching { ServerSocket(port, 1, loopback).close() }
        assertTrue(
            bindAfterStop.isSuccess,
            "port must be released after the shutdown hook stops the transport (leak regression)",
        )
    }
}
