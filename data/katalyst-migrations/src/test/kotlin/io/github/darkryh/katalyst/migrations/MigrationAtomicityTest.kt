package io.github.darkryh.katalyst.migrations

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.migrations.runner.MigrationRunner
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A migration and its history row must land together or not at all.
 *
 * The runner used to commit the migration body in one transaction and then write the bookkeeping
 * row in a second. Any failure between the two — a crashed pod, a lost connection, a rejected
 * insert — left the database changed with nothing recording it. The next boot saw no history row,
 * re-ran a non-idempotent `up()`, and died on a duplicate object; recovery meant editing the
 * database by hand. That window is invisible to a functional test, which is why it survived: every
 * existing migration test asserts the happy path, where both steps succeed.
 *
 * **Scope, stated honestly.** These tests exercise DML, because that is what a transaction can
 * actually undo. Most databases — H2 and MySQL among them — perform an implicit commit around DDL,
 * so a `CREATE TABLE` inside a migration is *not* rolled back no matter how the runner is written.
 * The fix therefore guarantees atomicity of the body-plus-record pair for data changes on every
 * database, and for schema changes only on engines with transactional DDL (PostgreSQL). A migration
 * mixing DDL and DML on H2 can still leave the DDL behind, and no amount of runner code changes
 * that — it is a property of the engine.
 */
class MigrationAtomicityTest {

    private lateinit var databaseFactory: DatabaseFactory

    @BeforeTest
    fun setup() {
        databaseFactory = DatabaseFactory.create(
            DatabaseConfig(
                url = "jdbc:h2:mem:katalyst-atomicity-${System.nanoTime()};DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
            )
        )
        // The target of the migrations below. Created up front so the migrations themselves only
        // ever perform DML, keeping the assertions about the runner rather than about H2's DDL
        // semantics.
        transaction(databaseFactory.database) { exec("CREATE TABLE payload (note VARCHAR(64))") }
    }

    @AfterTest
    fun tearDown() {
        runCatching { databaseFactory.close() }
    }

    private fun payloadRows(): List<String> = transaction(databaseFactory.database) {
        val rows = mutableListOf<String>()
        exec("SELECT note FROM payload") { rs -> while (rs.next()) rows += rs.getString(1) }
        rows
    }

    private fun historyIds(): List<String> = transaction(databaseFactory.database) {
        val ids = mutableListOf<String>()
        exec("SELECT migration_id FROM katalyst_schema_migrations") { rs ->
            while (rs.next()) ids += rs.getString(1)
        }
        ids
    }

    /**
     * A migration whose body succeeds but whose history row cannot be written.
     *
     * `description` overflows the history table's `VARCHAR(1024)`, so the bookkeeping insert is
     * rejected by the database. That reproduces "the body succeeded, the record did not"
     * deterministically and without DDL — which would trigger an implicit commit and mask the very
     * thing under test.
     */
    private object BookkeepingRejected : KatalystMigration {
        override val id = "V1__bookkeeping_rejected"
        override val description = "x".repeat(2_000)
        override fun up() {
            transaction { exec("INSERT INTO payload (note) VALUES ('written-by-migration')") }
        }
    }

    private object WritesPayload : KatalystMigration {
        override val id = "V1__writes_payload"
        override fun up() {
            transaction { exec("INSERT INTO payload (note) VALUES ('written-by-migration')") }
        }
    }

    @Test
    fun `a failed history write rolls back the migration body with it`() {
        val runner = MigrationRunner(databaseFactory, MigrationOptions())

        // The bookkeeping insert now fails inside the migration's own transaction, so the runner
        // reports a migration failure rather than letting it escape after the change has landed.
        assertFailsWith<Exception> { runner.runMigrations(listOf(BookkeepingRejected)) }

        assertTrue(
            payloadRows().isEmpty(),
            "the migration body survived a failed history write — the two are not atomic, got ${payloadRows()}",
        )
    }

    @Test
    fun `the happy path still commits both the change and its record`() {
        // The other half of the invariant: atomicity must not have cost us the normal outcome.
        val runner = MigrationRunner(databaseFactory, MigrationOptions())

        runner.runMigrations(listOf(WritesPayload))

        assertEquals(listOf("written-by-migration"), payloadRows(), "the migration did not apply")
        assertEquals(listOf(WritesPayload.id), historyIds(), "the migration was not recorded")
    }

    @Test
    fun `a migration that throws leaves neither its change nor a history row`() {
        val throwing = object : KatalystMigration {
            override val id = "V2__throws_midway"
            override fun up() {
                transaction {
                    exec("INSERT INTO payload (note) VALUES ('half-applied')")
                    error("boom, halfway through")
                }
            }
        }
        val runner = MigrationRunner(databaseFactory, MigrationOptions())

        assertFailsWith<Exception> { runner.runMigrations(listOf(throwing)) }

        assertTrue(payloadRows().isEmpty(), "a partial change survived a throwing migration")
        assertTrue(historyIds().isEmpty(), "a failed migration must not be recorded as applied")
    }

    @Test
    fun `a failed migration is not recorded, so the next boot retries it cleanly`() {
        // The recovery property the split-transaction bug destroyed: after a failure the database
        // is back where it started, so restarting with a corrected migration simply works.
        val runner = MigrationRunner(databaseFactory, MigrationOptions())
        assertFailsWith<Exception> { runner.runMigrations(listOf(BookkeepingRejected)) }
        assertTrue(historyIds().isEmpty(), "a failed migration must not be recorded")

        val repaired = object : KatalystMigration {
            override val id = BookkeepingRejected.id
            override val description = "now within the column limit"
            override fun up() {
                transaction { exec("INSERT INTO payload (note) VALUES ('written-by-migration')") }
            }
        }

        MigrationRunner(databaseFactory, MigrationOptions()).runMigrations(listOf(repaired))

        assertEquals(listOf("written-by-migration"), payloadRows(), "the retry did not apply")
        assertEquals(listOf(repaired.id), historyIds(), "the retry was not recorded")
    }

    @Test
    fun `an already-applied migration is not run twice`() {
        val runner = MigrationRunner(databaseFactory, MigrationOptions())
        runner.runMigrations(listOf(WritesPayload))

        MigrationRunner(databaseFactory, MigrationOptions()).runMigrations(listOf(WritesPayload))

        assertEquals(
            listOf("written-by-migration"),
            payloadRows(),
            "the migration ran a second time — idempotence is broken",
        )
    }
}
