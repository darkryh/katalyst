package com.ead.katalyst.di

import com.ead.katalyst.database.DatabaseConfig
import com.ead.katalyst.database.DatabaseTransactionManager
import com.ead.katalyst.di.fixtures.*
import com.ead.katalyst.tables.Table
import com.ead.katalyst.di.features.KatalystFeature
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import org.koin.core.Koin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

class InitializeKatalystDITest : KoinTest {

    @BeforeTest
    fun setup() {
        runCatching { stopKoin() }
    }

    @AfterTest
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun `initializeKatalystDI registers framework components`() = runBlocking {
        val koin = bootstrapKatalystDI(
            databaseConfig = DatabaseConfig(
                url = "jdbc:h2:mem:katalyst-test;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            ),
            scanPackages = arrayOf("com.ead.katalyst.di.fixtures")
        )

        val repository = koin.get<TestRepository>()
        val validator = koin.get<TestValidator>()
        val service = koin.get<TestService>()
        val transactionManager = koin.get<DatabaseTransactionManager>()
        val eventHandler = koin.get<SampleEventHandler>()
        val discoveredTables = koin.getAll<Table>()
        val exposedTables = koin.getAll<org.jetbrains.exposed.sql.Table>()

        assertNotNull(repository)
        assertNotNull(validator)
        assertNotNull(service)
        assertNotNull(transactionManager)
        assertNotNull(eventHandler)
        assertSame(service.transactionManager, transactionManager, "Service should receive injected transaction manager")
        assertTrue(discoveredTables.any { it === TestTable }, "TestTable should be registered as Katalyst Table")
        assertTrue(exposedTables.any { it == TestTable }, "TestTable should be registered as Exposed Table")

        service.create(TestEntity(id = 1, name = "test"))
        assertEquals(TestEntity(1, "test"), repository.findById(1))

        eventHandler.handle(SampleCreatedEvent(TestEntity(2, "event")))
        assertEquals(1, eventHandler.handledEvents().size)
    }

    @Test
    fun `bootstrapKatalystDI augments existing Koin context`() {
        stopKoin()
        val tokenModule = module {
            single { UUID.randomUUID() }
        }
        val initial = org.koin.core.context.startKoin {
            modules(tokenModule)
        }.koin
        val token = initial.get<UUID>()

        val config = DatabaseConfig(
            url = "jdbc:h2:mem:katalyst-test-existing;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        val reused = bootstrapKatalystDI(
            databaseConfig = config,
            scanPackages = arrayOf("com.ead.katalyst.di.fixtures")
        )

        assertEquals(token, reused.get<UUID>())
    }

    @Test
    fun `features contribute modules and hooks`() {
        val hookInvoked = AtomicBoolean(false)
        val feature = object : KatalystFeature {
            override val id: String = "test-feature"

            override fun provideModules() = listOf(
                module {
                    single { FeatureMarker("alive") }
                }
            )

            override fun onKoinReady(koin: Koin) {
                koin.get<FeatureMarker>()
                hookInvoked.set(true)
            }
        }

        val koin = bootstrapKatalystDI(
            databaseConfig = DatabaseConfig(
                url = "jdbc:h2:mem:katalyst-test-feature;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            ),
            features = listOf(feature)
        )

        val marker = koin.get<FeatureMarker>()
        assertEquals("alive", marker.value)
        assertTrue(hookInvoked.get(), "Feature hook should execute once Koin is ready")
    }
}

private data class FeatureMarker(val value: String)
