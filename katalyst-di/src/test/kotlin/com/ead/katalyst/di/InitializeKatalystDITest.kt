package com.ead.katalyst.di

import com.ead.katalyst.database.DatabaseConfig
import com.ead.katalyst.database.DatabaseTransactionManager
import com.ead.katalyst.di.fixtures.SampleCreatedEvent
import com.ead.katalyst.di.fixtures.SampleEventHandler
import com.ead.katalyst.di.fixtures.TestEntity
import com.ead.katalyst.di.fixtures.TestRepository
import com.ead.katalyst.di.fixtures.TestService
import com.ead.katalyst.di.fixtures.TestTable
import com.ead.katalyst.di.fixtures.TestValidator
import com.ead.katalyst.tables.Table
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
}
