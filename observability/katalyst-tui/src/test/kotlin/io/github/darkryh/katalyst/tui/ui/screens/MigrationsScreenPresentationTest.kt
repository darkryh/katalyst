package io.github.darkryh.katalyst.tui.ui.screens

import io.github.darkryh.katalyst.telemetry.model.MigrationEntry
import io.github.darkryh.katalyst.telemetry.model.MigrationFailure
import io.github.darkryh.katalyst.telemetry.model.MigrationState
import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationsScreenPresentationTest {

    @Test
    fun `every authoritative migration state has an unambiguous label`() {
        assertEquals("applied", migrationStateLabel(MigrationState.APPLIED))
        assertEquals("pending", migrationStateLabel(MigrationState.PENDING))
        assertEquals("baselined", migrationStateLabel(MigrationState.BASELINED))
        assertEquals("filtered", migrationStateLabel(MigrationState.FILTERED))
        assertEquals("orphaned!", migrationStateLabel(MigrationState.UNKNOWN_APPLIED))
    }

    @Test
    fun `unknown applied tally is human readable`() {
        assertEquals("unknown applied", migrationTallyLabel("unknownApplied"))
        assertEquals("unknown applied", migrationTallyLabel("UNKNOWN_APPLIED"))
    }

    @Test
    fun `failure alert identifies migration and error`() {
        val failure = MigrationFailure(epochMs = 1L, id = "2_add_index", message = "lock timeout")

        assertEquals(
            "✗ migration attempt failed — 2_add_index: lock timeout",
            migrationFailureAlert(failure),
        )
    }

    @Test
    fun `database-only row does not fabricate transaction mode`() {
        val entry = MigrationEntry(
            id = "1_orphaned",
            state = MigrationState.UNKNOWN_APPLIED,
            transactional = null,
        )

        assertEquals("source unavailable — execution mode unknown", migrationMode(entry))
    }

    @Test
    fun `non-transactional mode keeps rollback warning and version`() {
        val entry = MigrationEntry(
            id = "3_concurrent_index",
            state = MigrationState.PENDING,
            versionKey = "3",
            transactional = false,
        )

        assertEquals(
            "NON-transactional — partial failure cannot roll back · version 3",
            migrationMode(entry),
        )
    }
}
