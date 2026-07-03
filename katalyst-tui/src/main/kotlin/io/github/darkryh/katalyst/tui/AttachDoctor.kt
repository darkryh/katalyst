package io.github.darkryh.katalyst.tui

import io.github.darkryh.katalyst.tui.attach.RunDiscovery
import io.github.darkryh.katalyst.tui.attach.TelemetryClient
import io.github.darkryh.katalyst.tui.viewmodel.InspectorViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Headless attach doctor: exercises the exact discovery + attach path the inspector UI uses —
 * descriptor enumeration, snapshot fetch, and a live [InspectorViewModel] state window — printing
 * each step so the whole chain can be validated without a TTY (CI, ssh, "why won't it connect?").
 *
 * Run it from the installed distribution (classpath = every jar in the dist's lib dir):
 * ```
 * ./gradlew :katalyst-tui:installDist
 * cd katalyst-tui/build/install/katalyst-tui/lib
 * java -cp "*" io.github.darkryh.katalyst.tui.AttachDoctorKt
 * ```
 */
fun main(): Unit = runBlocking {
    println("run dir: ${RunDiscovery.runDirectory()} exists=${RunDiscovery.runDirectory().exists()}")
    val found = RunDiscovery.discover()
    println("discovered ${found.size} descriptor(s)")
    found.forEach { println("  - ${it.appName} pid=${it.pid} ${it.host}:${it.telemetryPort} status=${it.status}") }

    val client = TelemetryClient()
    for (d in found) {
        val t0 = System.currentTimeMillis()
        val snap = client.fetchSnapshot(d)
        val ms = System.currentTimeMillis() - t0
        if (snap == null) {
            println("  FETCH FAILED for pid=${d.pid} after ${ms}ms")
        } else {
            println("  fetched snapshot from pid=${d.pid} in ${ms}ms: scheduler jobs=${snap.scheduler?.jobs?.size}, boot=${snap.boot != null}, health=${snap.health.level}")
        }
    }
    client.close()

    println("--- InspectorViewModel state transitions (6s window) ---")
    val vm = InspectorViewModel()
    val collector = launch {
        vm.state.collect { s ->
            println("  state: attach=${s.attach::class.simpleName} backends=${s.backends.size} snapshot=${s.snapshot != null} err=${s.lastError}")
        }
    }
    withTimeoutOrNull(6.seconds) { collector.join() }
    collector.cancel()
    vm.close()
}
