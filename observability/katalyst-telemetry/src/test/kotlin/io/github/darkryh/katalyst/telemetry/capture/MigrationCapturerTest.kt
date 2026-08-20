package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.migrations.telemetry.MigrationTelemetry
import io.github.darkryh.katalyst.telemetry.store.TelemetryIdentity
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression coverage for wiring [MigrationTelemetry.failures] into the telemetry snapshot.
 *
 * `MigrationTelemetry` maintains a bounded ring of recent migration failures but, before this fix,
 * nothing ever read it back out -- [MigrationCapturer] is the only reader, so this asserts its
 * `capture()` (installed as `TelemetryStore.migrationProvider`) actually surfaces them.
 *
 * `MigrationTelemetry` is a process-global singleton, so this only asserts the presence of the
 * ids this test itself records (never exact list equality), to stay independent of any residue
 * from other tests sharing the same JVM.
 */
class MigrationCapturerTest {

    private fun identity() = TelemetryIdentity(
        appName = "migration-capturer-test",
        pid = 1L,
        katalystVersion = "test",
        startedAtEpochMs = System.currentTimeMillis(),
        host = "127.0.0.1",
        port = 0,
    )

    @Test
    fun `recent failures recorded in MigrationTelemetry are surfaced in the migration snapshot`() {
        val store = TelemetryStore(identity())
        MigrationCapturer().install(store)

        MigrationTelemetry.recordFailure("1_migration_capturer_test", "boom")

        val snapshot = store.migrationProvider?.invoke()
        assertTrue(snapshot != null, "capture() must report a snapshot once a failure has been recorded")

        val recorded = snapshot.recentFailures.filter { it.id == "1_migration_capturer_test" }
        assertTrue(recorded.isNotEmpty(), "the recorded failure must appear in recentFailures")
        assertEquals("boom", recorded.last().message)
    }

    @Test
    fun `capture never fabricates a running-migration marker`() {
        val store = TelemetryStore(identity())
        MigrationCapturer().install(store)

        // Without a container/runner bean and without this test recording an in-flight marker,
        // capture() must not report one -- regardless of whatever other tests may have already
        // recorded into the shared, process-global MigrationTelemetry failure ring.
        val snapshot = store.migrationProvider?.invoke()
        assertEquals(snapshot?.runningId, null, "no migration is running in this test")
    }
}
