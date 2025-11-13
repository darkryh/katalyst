package com.ead.katalyst.example.migrations

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.example.migrations.schema.TodoItemsTable
import com.ead.katalyst.example.migrations.schema.TodoListsTable
import com.ead.katalyst.migrations.options.MigrationOptions
import com.ead.katalyst.migrations.runner.MigrationRunner
import com.ead.katalyst.migrations.service.SchemaDiffService
import kotlin.test.assertFailsWith
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Paths
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExampleMigrationsTest {

    private lateinit var databaseFactory: DatabaseFactory
    private lateinit var schemaDiffService: SchemaDiffService

    @BeforeTest
    fun setUp() {
        databaseFactory = DatabaseFactory.create(
            DatabaseConfig(
                url = "jdbc:h2:mem:todo-example;MODE=MYSQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            )
        )
        schemaDiffService = SchemaDiffService(
            databaseFactory,
            Paths.get("build/generated-test-sql")
        )
    }

    @AfterTest
    fun tearDown() {
        runCatching { databaseFactory.close() }
    }

    @Test
    fun `creates schema and seeds demo data`() {
        val runner = MigrationRunner(databaseFactory, MigrationOptions())
        val migrations = listOf(
            V2024102001CreateTodoSchema(schemaDiffService, databaseFactory),
            V2024102002SeedSampleTodoList(databaseFactory)
        )

        runner.runMigrations(migrations)
        runner.runMigrations(migrations) // second run should be a no-op

        transaction(databaseFactory.database) {
            val lists = TodoListsTable.selectAll().count()
            val items = TodoItemsTable.selectAll().count()

            assertEquals(1, lists, "Expected exactly one demo todo list")
            assertEquals(2, items, "Expected two demo tasks in the seeded list")
        }
    }

    @Test
    fun `respects tag filters`() {
        val runnerWithFilter = MigrationRunner(
            databaseFactory,
            MigrationOptions(includeTags = setOf("schema"))
        )
        val runnerDefault = MigrationRunner(databaseFactory, MigrationOptions())

        val createSchema = V2024102001CreateTodoSchema(schemaDiffService, databaseFactory)
        val seedDemo = V2024102002SeedSampleTodoList(databaseFactory)

        runnerWithFilter.runMigrations(listOf(createSchema, seedDemo))

        transaction(databaseFactory.database) {
            assertEquals(
                0,
                TodoListsTable.selectAll().count().toInt(),
                "Seed migration should have been skipped"
            )
        }

        runnerDefault.runMigrations(listOf(createSchema, seedDemo))

        transaction(databaseFactory.database) {
            assertEquals(1, TodoListsTable.selectAll().count().toInt())
            assertEquals(2, TodoItemsTable.selectAll().count().toInt())
        }
    }

    @Test
    fun `fails when checksum drifts`() {
        val runner = MigrationRunner(databaseFactory, MigrationOptions())
        runner.runMigrations(listOf(DriftExampleMigration(checksumValue = "v1")))

        val error = assertFailsWith<IllegalStateException> {
            runner.runMigrations(listOf(DriftExampleMigration(checksumValue = "v2")))
        }

        assertTrue(error.message?.contains("Checksum mismatch") == true)
    }

    @Test
    fun `applies baseline and target filters`() {
        val recorder = mutableListOf<String>()

        val migrations = listOf(
            RecordingMigration("2024102101", "alpha", recorder),
            RecordingMigration("2024102102", "beta", recorder),
            RecordingMigration("2024102103", "gamma", recorder)
        )

        val runner = MigrationRunner(
            databaseFactory,
            MigrationOptions(
                baselineVersion = "2024102101",
                targetVersion = "2024102102"
            )
        )

        runner.runMigrations(migrations)

        assertEquals(listOf("beta"), recorder, "Only beta should execute (alpha baselined, gamma beyond target)")
    }

    @Test
    fun `stops on blocking failure`() {
        val recorder = mutableListOf<String>()
        val runner = MigrationRunner(databaseFactory, MigrationOptions())

        assertFailsWith<RuntimeException> {
            runner.runMigrations(
                listOf(
                    BlockingFailureMigration,
                    RecordingMigration("2024102202_success", "success", recorder)
                )
            )
        }

        assertEquals(emptyList(), recorder, "Runner should stop before executing follow-up migrations")
    }

    @Test
    fun `continues past non-blocking failure`() {
        val recorder = mutableListOf<String>()
        val runner = MigrationRunner(databaseFactory, MigrationOptions(stopOnFailure = false))

        runner.runMigrations(
            listOf(
                NonBlockingFailureMigration,
                RecordingMigration("2024102202_success", "success", recorder)
            )
        )

        assertEquals(listOf("success"), recorder, "Runner should continue when failure is non-blocking")
    }

    private fun assertTrue(value: Boolean, message: String? = null) {
        kotlin.test.assertTrue(value, message)
    }
}

private object DriftExampleTable : Table("drift_example") {
    val id = integer("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)
}

private class DriftExampleMigration(
    private val checksumValue: String
) : com.ead.katalyst.migrations.KatalystMigration {
    override val id: String = "2024102199_drift_example"
    override val checksum: String = checksumValue

    override fun up() {
        SchemaUtils.create(DriftExampleTable)
    }
}

private class RecordingMigration(
    override val id: String,
    private val label: String,
    private val recorder: MutableList<String>
) : com.ead.katalyst.migrations.KatalystMigration {
    override val transactional: Boolean = false

    override fun up() {
        recorder += label
    }
}

private object BlockingFailureMigration : com.ead.katalyst.migrations.KatalystMigration {
    override val id: String = "2024102201_blocking_fail"
    override val transactional: Boolean = false

    override fun up() {
        error("Simulated blocking failure")
    }
}

private object NonBlockingFailureMigration : com.ead.katalyst.migrations.KatalystMigration {
    override val id: String = "2024102201_non_blocking_fail"
    override val transactional: Boolean = false
    override val blocking: Boolean = false

    override fun up() {
        error("Simulated non-blocking failure")
    }
}
