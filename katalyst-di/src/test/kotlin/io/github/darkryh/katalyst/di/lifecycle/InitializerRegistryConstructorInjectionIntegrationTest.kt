package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class InitializerRegistryConstructorInjectionIntegrationTest {
    private lateinit var engine: TestBeanEngine
    private lateinit var databaseFactory: DatabaseFactory
    private val probe = InitializerProbe()

    @BeforeTest
    fun setUp() {
        RegistryManager.resetAll()
        databaseFactory = DatabaseFactory.create(inMemoryDatabaseConfig())
        val txManager = DatabaseTransactionManager(databaseFactory.database)

        engine = TestBeanEngine()
        engine.registerInstance(probe, InitializerProbe::class)
        engine.registerInstance(txManager, DatabaseTransactionManager::class)
        engine.registerInstance(ProbeInitializer(probe), ApplicationInitializer::class)
    }

    @AfterTest
    fun tearDown() {
        RegistryManager.resetAll()
        engine.stop()
        databaseFactory.close()
    }

    @Test
    fun `initializer registry invokes custom initializer with constructor-injected dependency`() = runBlocking {
        InitializerRegistry(engine.container).invokeAll()
        assertTrue(probe.executed)
    }
}

private class InitializerProbe {
    var executed: Boolean = false
}

private class ProbeInitializer(
    private val probe: InitializerProbe
) : ApplicationInitializer {
    override val initializerId: String = "ProbeInitializer"
    override val order: Int = 1

    override suspend fun onApplicationReady() {
        probe.executed = true
    }
}
