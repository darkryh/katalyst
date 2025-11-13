package com.ead.katalyst.example.migrations

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.example.migrations.schema.TodoItemsTable
import com.ead.katalyst.example.migrations.schema.TodoListsTable
import com.ead.katalyst.migrations.options.MigrationOptions
import com.ead.katalyst.migrations.runner.MigrationRunner
import com.ead.katalyst.migrations.service.SchemaDiffService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Paths
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer

class PostgresExampleMigrationsTest {

    private var container: PostgreSQLContainer<*>? = null
    private var databaseFactory: DatabaseFactory? = null
    private var schemaDiffService: SchemaDiffService? = null

    @BeforeTest
    fun setUp() {
        val startResult = runCatching {
            PostgreSQLContainer("postgres:16-alpine").apply {
                withDatabaseName("katalyst_example")
                withUsername("katalyst")
                withPassword("katalyst")
                start()
            }
        }

        container = startResult.getOrNull()

        val runningContainer = container ?: run {
            println("Skipping Postgres test – Docker not available: ${startResult.exceptionOrNull()?.message}")
            return
        }

        val config = DatabaseConfig(
            url = runningContainer.jdbcUrl,
            driver = runningContainer.driverClassName,
            username = runningContainer.username,
            password = runningContainer.password
        )

        val factory = DatabaseFactory.create(config)
        databaseFactory = factory
        schemaDiffService = SchemaDiffService(
            factory,
            Paths.get("build/generated-postgres-sql")
        )
    }

    @AfterTest
    fun tearDown() {
        runCatching { databaseFactory?.close() }
        runCatching { container?.stop() }
    }

    @Test
    fun `applies migrations against postgres`() {
        val factory = databaseFactory ?: return
        val schemaService = schemaDiffService ?: return

        val runner = MigrationRunner(factory, MigrationOptions())
        runner.runMigrations(
            listOf(
                V2024102001CreateTodoSchema(schemaService, factory),
                V2024102002SeedSampleTodoList(factory)
            )
        )

        transaction(factory.database) {
            assertEquals(1, TodoListsTable.selectAll().count())
            assertEquals(2, TodoItemsTable.selectAll().count())
        }
    }
}
