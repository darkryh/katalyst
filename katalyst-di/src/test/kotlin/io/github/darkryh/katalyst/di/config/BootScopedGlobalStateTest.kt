package io.github.darkryh.katalyst.di.config

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.di.lifecycle.BootstrapProgress
import io.github.darkryh.katalyst.di.lifecycle.BootstrapProgressLogger.PhaseStatus
import io.github.darkryh.katalyst.di.lifecycle.StartupWarnings
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * The bootstrap report — phase timings and startup warnings — is held in process-global
 * singletons that describe *one* boot. Nothing used to clear them, so a second boot in the
 * same JVM inherited the first one's report: warnings were shown twice, and phases the new
 * boot never reached still read as completed.
 */
class BootScopedGlobalStateTest {

    @AfterTest
    fun tearDown() {
        stopKatalystStandalone()
        BootstrapProgress.clear()
        StartupWarnings.clear()
    }

    @Test
    fun `a second bootstrap does not report the first bootstrap's startup warnings`() {
        boot()
        val firstBootWarnings = StartupWarnings.get()
        assertTrue(
            firstBootWarnings.isNotEmpty(),
            "a boot without the events feature should record at least one warning"
        )
        stopKatalystStandalone()

        boot()

        assertEquals(
            firstBootWarnings.size,
            StartupWarnings.get().size,
            "the second boot's report still carries the first boot's warnings"
        )
    }

    @Test
    fun `a bootstrap that fails immediately does not report the previous bootstrap's phases`() {
        boot()
        assertTrue(
            BootstrapProgress.getLifecycles().any { it.status == PhaseStatus.COMPLETED },
            "a successful boot should have completed phases"
        )
        stopKatalystStandalone()

        // No bean engine selected: the boot fails before the first phase is ever started,
        // so every phase must read as not-yet-run.
        assertFails {
            bootstrapKatalystContainer(
                databaseConfig = inMemoryDb(),
                serverConfig = testServerConfiguration(),
                beanEngine = null,
            )
        }

        val completed = BootstrapProgress.getLifecycles()
            .filter { it.status == PhaseStatus.COMPLETED }
            .map { it.lifecycle.lifecycleRef }
        assertEquals(
            emptyList(),
            completed,
            "a boot that never started a phase reports the previous boot's phases as completed"
        )
    }

    private fun boot() {
        initializeKatalystStandalone(
            options = KatalystDIOptions(
                databaseConfig = inMemoryDb(),
                beanEngine = TestBeanEngine(),
            ),
            serverConfiguration = testServerConfiguration(),
        )
    }

    private fun testServerConfiguration() = ServerConfiguration(
        engine = null,
        deployment = ServerDeploymentConfiguration.createDefault()
    )

    private fun inMemoryDb() = DatabaseConfig(
        url = "jdbc:h2:mem:boot-scoped-state-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        driver = "org.h2.Driver",
        username = "sa",
        password = "",
        maxPoolSize = 4,
        minIdleConnections = 1,
        connectionTimeout = 3000,
        idleTimeout = 10000,
        maxLifetime = 30000,
        autoCommit = false,
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
    )
}
