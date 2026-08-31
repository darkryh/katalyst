package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.migrations.runner.MigrationRunner
import io.github.darkryh.katalyst.migrations.telemetry.MigrationTelemetry
import io.github.darkryh.katalyst.telemetry.model.MigrationSnapshot
import io.github.darkryh.katalyst.telemetry.model.MigrationState
import io.github.darkryh.katalyst.telemetry.store.TelemetryIdentity
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigrationCapturerStatusTest {

    private lateinit var databaseFactory: DatabaseFactory
    private lateinit var options: MigrationOptions
    private lateinit var runner: MigrationRunner
    private lateinit var container: MutableMigrationContainer

    @BeforeTest
    fun setUp() {
        MigrationTelemetry.reset()
        KatalystContainerProvider.reset()
        databaseFactory = DatabaseFactory.create(
            DatabaseConfig(
                url = "jdbc:h2:mem:migration-capturer-${System.nanoTime()};DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
            )
        )
        options = MigrationOptions()
        runner = MigrationRunner(databaseFactory, options)
        container = MutableMigrationContainer(runner, options)
        KatalystContainerProvider.set(container)
    }

    @AfterTest
    fun tearDown() {
        KatalystContainerProvider.reset()
        MigrationTelemetry.reset()
        runCatching { databaseFactory.close() }
    }

    @Test
    fun `partial discovery is loading and never fabricates unknown applied migrations`() {
        val first = TestMigration("1_first")
        val second = TestMigration("2_second")
        runner.runMigrations(listOf(first, second))
        container.migrations = listOf(first)
        var discoveryComplete = false
        val store = storeWith(MigrationCapturer.forTest(sourceSetReady = { discoveryComplete }))

        val duringDiscovery = store.migrationProvider?.invoke()

        assertEquals(false, duringDiscovery?.statusReady)
        assertTrue(duringDiscovery?.entries.orEmpty().isEmpty())
        assertTrue(duringDiscovery?.tallies.orEmpty().isEmpty())

        container.migrations = listOf(first, second)
        discoveryComplete = true
        val afterDiscovery = store.migrationProvider?.invoke()

        assertEquals(true, afterDiscovery?.statusReady)
        assertEquals(setOf(first.id, second.id), afterDiscovery?.entries?.map { it.id }?.toSet())
        assertTrue(afterDiscovery?.entries.orEmpty().all { it.state == MigrationState.APPLIED })
        assertEquals(0, afterDiscovery?.tallies?.get("unknownApplied"))
    }

    @Test
    fun `committed migration revision invalidates a cached pending snapshot immediately`() {
        val migration = TestMigration("1_cached_pending")
        container.migrations = listOf(migration)
        val store = storeWith(MigrationCapturer.forTest(sourceSetReady = { true }))

        val before = store.migrationProvider?.invoke()
        assertEquals(MigrationState.PENDING, before?.entries?.single()?.state)

        runner.runMigrations(listOf(migration))
        val after = store.migrationProvider?.invoke()

        assertEquals(MigrationState.APPLIED, after?.entries?.single()?.state)
        assertEquals(migration.checksum, after?.entries?.single()?.checksumDb)
        assertEquals(migration.checksum, after?.entries?.single()?.checksumCode)
    }

    @Test
    fun `genuine database-only migration remains orphaned after discovery`() {
        val migration = TestMigration("1_orphaned")
        runner.runMigrations(listOf(migration))
        container.migrations = emptyList()
        val store = storeWith(MigrationCapturer.forTest(sourceSetReady = { true }))

        val entry = store.migrationProvider?.invoke()?.entries?.single()

        assertEquals(MigrationState.UNKNOWN_APPLIED, entry?.state)
        assertEquals(migration.checksum, entry?.checksumDb)
        assertNull(entry?.checksumCode)
        assertNull(entry?.transactional)
    }

    @Test
    fun `capturer maps every migration state and checksum drift truthfully`() {
        val applied = TestMigration("1_applied", tags = setOf("prod"))
        val driftStored = TestMigration("2_drift", checksum = "stored", tags = setOf("prod"))
        val driftCode = TestMigration("2_drift", checksum = "changed", tags = setOf("prod"))
        val baselined = TestMigration("3_baselined", tags = setOf("prod"))
        val pending = TestMigration("4_pending", tags = setOf("prod"))
        val filtered = TestMigration("5_filtered", tags = setOf("dev"))
        val orphaned = TestMigration("9_orphaned")

        runner.runMigrations(listOf(applied, driftStored, orphaned))
        MigrationRunner(
            databaseFactory,
            MigrationOptions(baselineVersion = baselined.id),
        ).runMigrations(listOf(baselined))

        val statusOptions = MigrationOptions(includeTags = setOf("prod"))
        container.runner = MigrationRunner(databaseFactory, statusOptions)
        container.options = statusOptions
        container.migrations = listOf(applied, driftCode, baselined, pending, filtered)
        val store = storeWith(MigrationCapturer.forTest(sourceSetReady = { true }))

        val snapshot = store.migrationProvider?.invoke() ?: error("migration snapshot missing")
        val entries = snapshot.entries.associateBy { it.id }

        assertEquals(MigrationState.APPLIED, entries.getValue(applied.id).state)
        assertEquals(MigrationState.APPLIED, entries.getValue(driftCode.id).state)
        assertTrue(entries.getValue(driftCode.id).checksumDrift)
        assertEquals("stored", entries.getValue(driftCode.id).checksumDb)
        assertEquals("changed", entries.getValue(driftCode.id).checksumCode)
        assertEquals(MigrationState.BASELINED, entries.getValue(baselined.id).state)
        assertEquals(MigrationState.PENDING, entries.getValue(pending.id).state)
        assertEquals(MigrationState.FILTERED, entries.getValue(filtered.id).state)
        assertEquals(MigrationState.UNKNOWN_APPLIED, entries.getValue(orphaned.id).state)
        assertEquals(2, snapshot.tallies.getValue("applied"))
        assertEquals(1, snapshot.tallies.getValue("baselined"))
        assertEquals(1, snapshot.tallies.getValue("pending"))
        assertEquals(1, snapshot.tallies.getValue("filtered"))
        assertEquals(1, snapshot.tallies.getValue("unknownApplied"))
    }

    @Test
    fun `unreadable history is explicit and does not fabricate pending entries`() {
        val migration = TestMigration("1_unreadable")
        container.migrations = listOf(migration)
        databaseFactory.close()
        val store = storeWith(MigrationCapturer.forTest(sourceSetReady = { true }))

        val snapshot = store.migrationProvider?.invoke() ?: error("migration snapshot missing")

        assertEquals(false, snapshot.historyReadable)
        assertTrue(snapshot.historyError?.isNotBlank() == true)
        assertTrue(snapshot.entries.isEmpty())
        assertTrue(snapshot.validationErrors.any { it.startsWith("Migration history is unreadable:") })
    }

    @Test
    fun `failed non-blocking attempt remains pending and exposes its error`() {
        val migration = FailingMigration("1_failed")
        container.migrations = listOf(migration)

        runner.runMigrations(listOf(migration))
        val store = storeWith(MigrationCapturer.forTest(sourceSetReady = { true }))
        val snapshot = store.migrationProvider?.invoke() ?: error("migration snapshot missing")

        assertEquals(MigrationState.PENDING, snapshot.entries.single().state)
        assertEquals(migration.id, snapshot.recentFailures.last().id)
        assertEquals("expected failure", snapshot.recentFailures.last().message)
    }

    @Test
    fun `stable revision uses ttl cache while revision and expiry force reload`() {
        var now = 100L
        var revision = 1L
        var loads = 0
        val cache = RevisionAwareMigrationSnapshotCache(ttlMs = 60_000L, nowMs = { now })
        val loader = {
            loads++
            MigrationSnapshot(tallies = mapOf("load" to loads))
        }

        assertEquals(1, cache.get({ revision }, loader)?.tallies?.get("load"))
        assertEquals(1, cache.get({ revision }, loader)?.tallies?.get("load"))
        assertEquals(1, loads)

        revision++
        assertEquals(2, cache.get({ revision }, loader)?.tallies?.get("load"))
        assertEquals(2, loads)

        now += 60_000L
        assertEquals(3, cache.get({ revision }, loader)?.tallies?.get("load"))
        assertEquals(3, loads)
    }

    private fun storeWith(capturer: MigrationCapturer): TelemetryStore =
        TelemetryStore(
            TelemetryIdentity(
                appName = "migration-capturer-status-test",
                pid = 1L,
                katalystVersion = "test",
                startedAtEpochMs = 1L,
                host = "127.0.0.1",
                port = 0,
            )
        ).also(capturer::install)
}

private class TestMigration(
    override val id: String,
    override val checksum: String = id,
    override val tags: Set<String> = emptySet(),
) : KatalystMigration {
    override fun up() = Unit
}

private class FailingMigration(
    override val id: String,
) : KatalystMigration {
    override val blocking: Boolean = false

    override fun up(): Unit = error("expected failure")
}

private class MutableMigrationContainer(
    var runner: MigrationRunner,
    var options: MigrationOptions,
) : KatalystContainer {
    var migrations: List<KatalystMigration> = emptyList()

    override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
        getOrNull(type, qualifier) ?: error("No test bean for ${type.qualifiedName}")

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? = when (type) {
        MigrationRunner::class -> runner as T
        MigrationOptions::class -> options as T
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getAll(type: KClass<T>): List<T> = when (type) {
        KatalystMigration::class -> migrations as List<T>
        else -> emptyList()
    }

    override fun contains(type: KClass<*>, qualifier: String?): Boolean =
        getOrNull(type, qualifier) != null
}
