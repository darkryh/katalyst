package com.ead.katalyst.di

import com.ead.katalyst.database.DatabaseConfig
import com.ead.katalyst.database.DatabaseTransactionManager
import com.ead.katalyst.di.fixtures.*
import com.ead.katalyst.services.service.SchedulerService
import com.ead.katalyst.tables.Table
import com.ead.katalyst.events.EventBus
import com.ead.katalyst.events.EventConfiguration
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.*
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
        assertTrue(service.transactionManager === transactionManager, "Service should receive injected transaction manager")
        assertTrue(discoveredTables.any { it === TestTable }, "TestTable should be registered as Katalyst Table")
        assertTrue(exposedTables.any { it == TestTable }, "TestTable should be registered as Exposed Table")

        service.create(TestEntity(id = 1, name = "test"))
        assertEquals(TestEntity(1, "test"), repository.findById(1))

        eventHandler.handle(SampleCreatedEvent(TestEntity(2, "event")))
        assertEquals(1, eventHandler.handledEvents().size)
    }

    @Test
    fun `scheduler module is registered only when enabled`() {
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:katalyst-test-scheduler;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        val disabled = bootstrapKatalystDI(
            databaseConfig = config,
            enableScheduler = false,
            scanPackages = arrayOf("com.ead.katalyst.di.fixtures")
        )

        val schedulerResolution = runCatching { disabled.get<SchedulerService>() }
        assertTrue(schedulerResolution.isFailure, "SchedulerService should not be available when scheduler is disabled")

        stopKoin()

        val enabled = bootstrapKatalystDI(
            databaseConfig = config,
            enableScheduler = true,
            scanPackages = arrayOf("com.ead.katalyst.di.fixtures")
        )

        assertNotNull(enabled.get<SchedulerService>())
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
    fun `websockets flag defaults to disabled`() {
        val koin = bootstrapKatalystDI(
            databaseConfig = DatabaseConfig(
                url = "jdbc:h2:mem:katalyst-test-websocket-flag;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            )
        )

        val flag = koin.get<Boolean>(qualifier = named("enableWebSockets"))
        assertFalse(flag, "WebSocket flag should default to false")
    }

    @Test
    fun `websockets flag enabled when requested`() {
        val koin = bootstrapKatalystDI(
            databaseConfig = DatabaseConfig(
                url = "jdbc:h2:mem:katalyst-test-websocket-enabled;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            ),
            enableWebSockets = true
        )

        val flag = koin.get<Boolean>(qualifier = named("enableWebSockets"))
        assertTrue(flag, "WebSocket flag should be true when enabled")
    }

    @Test
    fun `events module wires handlers when enabled`() = runBlocking {
        val koin = bootstrapKatalystDI(
            databaseConfig = DatabaseConfig(
                url = "jdbc:h2:mem:katalyst-test-events;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            ),
            scanPackages = arrayOf("com.ead.katalyst.di.fixtures"),
            eventConfiguration = EventConfiguration().apply { applicationBus() }
        )

        val bus = koin.get<EventBus>()
        val handler = koin.get<SampleEventHandler>()
        val event = SampleCreatedEvent(TestEntity(99, "event"))
        bus.publish(event)

        val handled = handler.handledEvents()
        assertEquals(1, handled.size)
        assertEquals(event, handled.first())
    }
}
