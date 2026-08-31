package io.github.darkryh.katalyst.telemetry.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigrationModelSerializationTest {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `loading and unavailable-history state round trips`() {
        val snapshot = MigrationSnapshot(
            historyReadable = false,
            historyError = "database unavailable",
            statusReady = false,
            recentFailures = listOf(MigrationFailure(7L, "1_failed", "boom")),
        )

        val decoded = json.decodeFromString(
            MigrationSnapshot.serializer(),
            json.encodeToString(MigrationSnapshot.serializer(), snapshot),
        )

        assertFalse(decoded.statusReady)
        assertFalse(decoded.historyReadable)
        assertEquals("database unavailable", decoded.historyError)
        assertEquals("1_failed", decoded.recentFailures.single().id)
    }

    @Test
    fun `entry preserves independent checksums and unknown transactionality`() {
        val entry = MigrationEntry(
            id = "1_orphaned",
            state = MigrationState.UNKNOWN_APPLIED,
            checksumDb = "stored",
            checksumCode = null,
            transactional = null,
        )

        val decoded = json.decodeFromString(
            MigrationEntry.serializer(),
            json.encodeToString(MigrationEntry.serializer(), entry),
        )

        assertEquals("stored", decoded.checksumDb)
        assertNull(decoded.checksumCode)
        assertNull(decoded.transactional)
    }

    @Test
    fun `older migration payload defaults to authoritative status`() {
        val decoded = json.decodeFromString(
            MigrationSnapshot.serializer(),
            """{"entries":[],"tallies":{},"historyReadable":true,"runAtStartup":true}""",
        )

        assertTrue(decoded.statusReady)
        assertNull(decoded.historyError)
    }
}
