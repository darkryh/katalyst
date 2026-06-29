package io.github.darkryh.katalyst.di.config

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.di.lifecycle.ReadyHook
import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeReadyInitializationFlowTest {

    @AfterTest
    fun tearDown() {
        RegistryManager.resetAll()
        stopKatalystStandalone()
    }

    @Test
    fun `runtime-ready initializers are deferred when standalone activation disabled`() {
        val probe = RuntimeReadyProbe()
        val options = KatalystDIOptions(
            databaseConfig = inMemoryDb(),
            beanEngine = TestBeanEngine(),
            scanPackages = emptyArray(),
            features = listOf(RuntimeReadyTestFeature(probe))
        )

        val koin = initializeKatalystStandalone(
            options = options,
            serverConfiguration = ServerConfiguration(
                engine = null,
                deployment = ServerDeploymentConfiguration.createDefault()
            ),
            activateRuntimeReadyInitializers = false
        )

        assertFalse(probe.executed, "Runtime-ready initializer must not execute before activation call")

        runRuntimeReadyInitializers(koin)

        assertTrue(probe.executed)
        assertEquals(1, probe.executions)
    }

    @Test
    fun `standalone shutdown is idempotent`() {
        val options = KatalystDIOptions(
            databaseConfig = inMemoryDb(),
            beanEngine = TestBeanEngine(),
            scanPackages = emptyArray(),
            features = emptyList()
        )

        initializeKatalystStandalone(
            options = options,
            serverConfiguration = ServerConfiguration(
                engine = null,
                deployment = ServerDeploymentConfiguration.createDefault()
            ),
            activateRuntimeReadyInitializers = false
        )

        stopKatalystStandalone()
        stopKatalystStandalone()
    }

    private fun inMemoryDb() = DatabaseConfig(
        url = "jdbc:h2:mem:runtime-ready-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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

private class RuntimeReadyProbe {
    var executed: Boolean = false
    var executions: Int = 0
}

private class RuntimeReadyTestFeature(
    private val probe: RuntimeReadyProbe
) : KatalystFeature {
    override val id: String = "runtime-ready-test-feature"

    override fun provideBeanModules(): List<KatalystBeanModule> = listOf(
        katalystBeanModule {
            single<ReadyHook> { RuntimeReadyProbeInitializer(probe) }
        }
    )
}

private class RuntimeReadyProbeInitializer(
    private val probe: RuntimeReadyProbe
) : ReadyHook {
    override val id: String = "RuntimeReadyProbeInitializer"

    override suspend fun onReady() {
        probe.executed = true
        probe.executions += 1
    }
}
