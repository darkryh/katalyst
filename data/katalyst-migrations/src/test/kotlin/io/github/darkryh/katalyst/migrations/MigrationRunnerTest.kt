package io.github.darkryh.katalyst.migrations

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.migrations.runner.MigrationState
import io.github.darkryh.katalyst.migrations.runner.MigrationRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
                url = "jdbc:h2:mem:katalyst-migrations-test-${System.nanoTime()};DB_CLOSE_DELAY=-1",
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

    @Test
    fun `rejects duplicate migration ids before execution`() {
        val runner = MigrationRunner(databaseFactory, MigrationOptions())

        val error = assertFailsWith<IllegalArgumentException> {
            runner.runMigrations(listOf(DuplicateMigrationA, DuplicateMigrationB))
        }

        assertTrue(error.message.orEmpty().contains("Duplicate migration id"))
    }

    @Test
    fun `validate reports duplicate migration ids without executing`() {
        val runner = MigrationRunner(databaseFactory, MigrationOptions())

        val result = runner.validateMigrations(listOf(DuplicateMigrationA, DuplicateMigrationB))

        assertFalse(result.valid)
        assertTrue(result.errors.single().contains("Duplicate migration id"))
    }

    @Test
    fun `validate reports checksum drift for applied migrations`() {
        val runner = MigrationRunner(databaseFactory, MigrationOptions())

        runner.runMigrations(listOf(ChecksumMigration(checksum = "original")))

        val result = runner.validateMigrations(listOf(ChecksumMigration(checksum = "changed")))

        assertFalse(result.valid)
        assertTrue(result.errors.single().contains("Checksum mismatch for migration checksum_drift"))
    }

    @Test
    fun `status reports pending and applied migrations`() {
        val runner = MigrationRunner(databaseFactory, MigrationOptions())

        runner.runMigrations(listOf(StatusAppliedMigration))

        val report = runner.status(listOf(StatusAppliedMigration, StatusPendingMigration))

        assertEquals(MigrationState.APPLIED, report.migrations.single { it.id == "status_applied" }.state)
        assertEquals(MigrationState.PENDING, report.migrations.single { it.id == "status_pending" }.state)
        assertEquals(listOf("status_pending"), report.pending.map { it.id })
    }

    @Test
    fun `status reports filtered migrations`() {
        val runner = MigrationRunner(databaseFactory, MigrationOptions(includeTags = setOf("prod")))

        val report = runner.status(listOf(DevSeedMigration))

        assertEquals(MigrationState.FILTERED, report.migrations.single().state)
    }

    @Test
    fun `dry run returns pending migrations without executing them`() {
        val runner = MigrationRunner(databaseFactory, MigrationOptions())
        val migration = RecordingMigration("dry_run_pending")

        val report = runner.dryRun(listOf(migration))

        assertEquals(1, report.count)
        assertEquals("dry_run_pending", report.pending.single().id)
        assertFalse(migration.executed)
    }

    @Test
    fun `target version uses numeric migration ordering`() {
        val runner = MigrationRunner(databaseFactory, MigrationOptions(targetVersion = "2"))

        runner.runMigrations(listOf(TargetVersionMigration10, TargetVersionMigration2))

        transaction(databaseFactory.database) {
            assertEquals(1, TargetVersionTable.selectAll().count())
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

private object TargetVersionTable : Table("target_version_table") {
    val id = integer("id").autoIncrement()
    val migrationSource = varchar("source", 32)
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

private object DuplicateMigrationA : KatalystMigration {
    override val id: String = "duplicate"
    override fun up() = Unit
}

private object DuplicateMigrationB : KatalystMigration {
    override val id: String = "duplicate"
    override fun up() = Unit
}

private class ChecksumMigration(
    override val checksum: String,
) : KatalystMigration {
    override val id: String = "checksum_drift"
    override fun up() = Unit
}

private object StatusAppliedMigration : KatalystMigration {
    override val id: String = "status_applied"
    override fun up() = Unit
}

private object StatusPendingMigration : KatalystMigration {
    override val id: String = "status_pending"
    override fun up() = Unit
}

private class RecordingMigration(
    override val id: String,
) : KatalystMigration {
    var executed: Boolean = false

    override fun up() {
        executed = true
    }
}

private object TargetVersionMigration2 : KatalystMigration {
    override val id: String = "2"
    override fun up() {
        SchemaUtils.create(TargetVersionTable)
        TargetVersionTable.insert {
            it[migrationSource] = "two"
        }
    }
}

private object TargetVersionMigration10 : KatalystMigration {
    override val id: String = "10"
    override fun up() {
        SchemaUtils.create(TargetVersionTable)
        TargetVersionTable.insert {
            it[migrationSource] = "ten"
        }
    }
}
