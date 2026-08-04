package io.github.darkryh.katalyst.examplefixtures

import io.github.darkryh.katalyst.di.lifecycle.StartupHook

/**
 * Test fixtures for the startup-hook smoke test, deliberately outside
 * `io.github.darkryh.katalyst.example` — the package the sample scans.
 *
 * A class implementing a framework interface is discovered wherever it lives, and the test
 * classpath is scanned like any other. Inside the scan root, [SmokeProbeStartupHook] would be
 * discovered by *every* test that boots the sample, and each would have to supply a [SmokeProbe]
 * to satisfy its constructor — failing the ones that have no reason to know it exists. The test
 * that owns the fixture registers both explicitly, so it needs them reachable, not discovered.
 */
internal class SmokeProbe {
    var executed: Boolean = false
}

internal class SmokeProbeStartupHook(
    private val probe: SmokeProbe
) : StartupHook {
    override val id: String = "sample-smoke-probe-startup-hook"
    override val order: Int = 5

    override suspend fun onStartup() {
        probe.executed = true
    }
}
