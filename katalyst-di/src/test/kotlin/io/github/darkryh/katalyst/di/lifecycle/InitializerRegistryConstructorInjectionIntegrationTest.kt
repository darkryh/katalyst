package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.core.transaction.DatabaseTransactionManager
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class InitializerRegistryConstructorInjectionIntegrationTest {
    private lateinit var koin: Koin
    private lateinit var databaseFactory: DatabaseFactory
    private val probe = InitializerProbe()

    @BeforeTest
    fun setUp() {
        RegistryManager.resetAll()
        databaseFactory = DatabaseFactory.create(inMemoryDatabaseConfig())
        val txManager = DatabaseTransactionManager(databaseFactory.database)

        val app = startKoin {
            modules(
                module {
                    single { probe }
                    single<DatabaseTransactionManager> { txManager }
                    single<ApplicationInitializer> { ProbeInitializer(get()) }
                }
            )
        }
        koin = app.koin
    }

    @AfterTest
    fun tearDown() {
        RegistryManager.resetAll()
        stopKoin()
        databaseFactory.close()
    }

    @Test
    fun `initializer registry invokes custom initializer with constructor-injected dependency`() = runBlocking {
        InitializerRegistry(koin).invokeAll()
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
