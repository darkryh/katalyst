package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.di.config.ServerConfiguration
import io.github.darkryh.katalyst.di.config.ServerDeploymentConfiguration
import io.github.darkryh.katalyst.di.config.bootstrapKatalystContainer
import io.github.darkryh.katalyst.di.config.SchemaManagementOptions
import io.github.darkryh.katalyst.di.config.SchemaPolicy
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.migrations.feature.MigrationFeature
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.migrations.runner.MigrationRunner
import io.github.darkryh.katalyst.telemetry.TelemetryFeature
import io.github.darkryh.katalyst.telemetry.capture.integrationfixture.BlockingDiscoveryMigration
import io.github.darkryh.katalyst.telemetry.model.MigrationState
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** End-to-end regression for issue #36's attach-during-discovery ordering. */
class MigrationDiscoveryIntegrationTest {

    @AfterTest
    fun tearDown() {
        BlockingDiscoveryMigration.release.countDown()
        runCatching { KoinBeanEngine.stop() }
        BlockingDiscoveryMigration.reset()
    }

    @Test
    fun `real bootstrap never reconciles history against a source set still being registered`() {
        val databaseUrl = "jdbc:h2:mem:migration-discovery-integration-${System.nanoTime()};DB_CLOSE_DELAY=-1"
        val databaseConfig = DatabaseConfig(
            url = databaseUrl,
            driver = "org.h2.Driver",
            username = "sa",
            password = "",
        )
        seedAppliedHistory(databaseConfig)
        BlockingDiscoveryMigration.reset()
        val bootFailure = AtomicReference<Throwable?>()

        val boot = thread(name = "migration-discovery-integration-boot") {
            runCatching {
                bootstrapKatalystContainer(
                    databaseConfig = databaseConfig,
                    scanPackages = arrayOf(
                        "io.github.darkryh.katalyst.telemetry.capture.integrationfixture"
                    ),
                    features = listOf(
                        TelemetryFeature,
                        MigrationFeature(MigrationOptions()),
                    ),
                    serverConfig = ServerConfiguration(
                        engine = null,
                        deployment = ServerDeploymentConfiguration.createDefault(),
                    ),
                    schemaManagement = SchemaManagementOptions(policy = SchemaPolicy.NONE),
                    beanEngine = KoinBeanEngine,
                )
            }.onFailure(bootFailure::set)
        }

        assertTrue(
            BlockingDiscoveryMigration.entered.await(10, TimeUnit.SECONDS),
            "component discovery never reached the blocking migration fixture",
        )

        val duringDiscovery = TelemetryStore.active?.snapshot()?.migrations
            ?: error("migration telemetry was not attached during discovery")
        assertEquals(false, duringDiscovery.statusReady)
        assertTrue(duringDiscovery.entries.isEmpty())
        assertEquals(null, duringDiscovery.tallies["unknownApplied"])

        BlockingDiscoveryMigration.release.countDown()
        boot.join(20_000)
        assertTrue(!boot.isAlive, "bootstrap did not finish after discovery was released")
        assertNull(bootFailure.get(), "bootstrap failed: ${bootFailure.get()?.message}")

        val afterDiscovery = TelemetryStore.active?.snapshot()?.migrations
            ?: error("migration telemetry disappeared after bootstrap")
        val entry = afterDiscovery.entries.single()
        assertTrue(afterDiscovery.statusReady)
        assertEquals(MigrationState.APPLIED, entry.state)
        assertEquals(BlockingDiscoveryMigration.ID, entry.id)
        assertEquals(BlockingDiscoveryMigration.ID, entry.checksumDb)
        assertEquals(BlockingDiscoveryMigration.ID, entry.checksumCode)
        assertEquals(0, afterDiscovery.tallies["unknownApplied"])
    }

    private fun seedAppliedHistory(databaseConfig: DatabaseConfig) {
        val databaseFactory = DatabaseFactory.create(databaseConfig)
        try {
            MigrationRunner(databaseFactory, MigrationOptions()).runMigrations(
                listOf(
                    object : KatalystMigration {
                        override val id: String = BlockingDiscoveryMigration.ID
                        override fun up() = Unit
                    }
                )
            )
        } finally {
            databaseFactory.close()
        }
    }
}
