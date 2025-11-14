package com.ead.katalyst.migrations

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.migrations.options.MigrationOptions
import com.ead.katalyst.migrations.runner.MigrationRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class MigrationRunnerTest {

    private lateinit var databaseFactory: DatabaseFactory

    @BeforeTest
    fun setup() {
        databaseFactory = DatabaseFactory.create(
            DatabaseConfig(
                url = "jdbc:h2:mem:katalyst-migrations-test;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            )
        )
    }

    @AfterTest
    fun tearDown() {
        runCatching { databaseFactory.close() }
    }

    @Test
    fun `applies migrations sequentially`() {
        val runner = MigrationRunner(databaseFactory, MigrationOptions())

        runner.runMigrations(listOf(CreateFooTableMigration, CreateBarTableMigration))

        transaction(databaseFactory.database) {
            FooTable.insertSample()
            BarTable.insertSample()

            assertEquals(1, FooTable.selectAll().count())
            assertEquals(1, BarTable.selectAll().count())
        }
    }

    @Test
    fun `respects tag filters`() {
        val options = MigrationOptions(includeTags = setOf("prod"))
        val runner = MigrationRunner(databaseFactory, options)

        runner.runMigrations(listOf(DevSeedMigration))

        transaction(databaseFactory.database) {
            SchemaUtils.create(SeededTable)
            assertEquals(0, SeededTable.selectAll().count())
        }
    }
}

private object FooTable : Table("foo_table") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 32)
    override val primaryKey = PrimaryKey(id)

    fun insertSample() {
        insert {
            it[name] = "foo"
        }
    }
}

private object BarTable : Table("bar_table") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 32)
    override val primaryKey = PrimaryKey(id)

    fun insertSample() {
        insert {
            it[name] = "bar"
        }
    }
}

private object SeededTable : Table("seeded_table") {
    val id = integer("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)
}

private object CreateFooTableMigration : KatalystMigration {
    override val id: String = "2024100901_create_foo"
    override fun up() {
        SchemaUtils.create(FooTable)
    }
}

private object CreateBarTableMigration : KatalystMigration {
    override val id: String = "2024100902_create_bar"
    override fun up() {
        SchemaUtils.create(BarTable)
    }
}

private object DevSeedMigration : KatalystMigration {
    override val id: String = "2024100903_dev_seed"
    override val tags: Set<String> = setOf("dev")

    override fun up() {
        SchemaUtils.create(SeededTable)
        SeededTable.insert { }
    }
}
