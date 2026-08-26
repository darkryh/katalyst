package io.github.darkryh.katalyst.database

import io.github.darkryh.katalyst.config.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins [DatabaseFactory.addMissingColumns] — the half of "create what is missing" that
 * `CREATE TABLE IF NOT EXISTS` structurally cannot do, because it skips an existing table whole.
 *
 * The definitions below all name the same physical table, so each test can create the table
 * from one shape and then diff a wider (or differently typed) shape against it — which is exactly
 * what happens to a real application when a column is added to a `Table` after the first boot.
 */
class AddMissingColumnsTest {

    private object WidgetsV1 : Table("amc_widgets") {
        val id = long("id")
        val name = varchar("name", 64)
        override val primaryKey = PrimaryKey(id)
    }

    /** [WidgetsV1] plus two columns, one of them indexed. */
    private object WidgetsV2 : Table("amc_widgets") {
        val id = long("id")
        val name = varchar("name", 64)
        val slug = varchar("slug", 64).nullable().index("amc_widgets_slug_idx")
        val notes = text("notes").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    /** [WidgetsV1] with `name` widened. Drift, not absence — nothing here is "missing". */
    private object WidgetsRetyped : Table("amc_widgets") {
        val id = long("id")
        val name = varchar("name", 512)
        override val primaryKey = PrimaryKey(id)
    }

    /** A column that cannot be back-filled: NOT NULL with no default. */
    private object WidgetsWithRequiredColumn : Table("amc_widgets") {
        val id = long("id")
        val name = varchar("name", 64)
        val owner = varchar("owner", 64)
        override val primaryKey = PrimaryKey(id)
    }

    private val factories = mutableListOf<DatabaseFactory>()

    @AfterTest
    fun tearDown() {
        factories.forEach { runCatching { it.close() } }
        factories.clear()
    }

    private fun freshFactory(): DatabaseFactory =
        DatabaseFactory.create(
            DatabaseConfig(
                url = "jdbc:h2:mem:amc_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
            )
        ).also { factories += it }

    private fun DatabaseFactory.columnNames(): Set<String> =
        transaction(database) {
            exec("SELECT * FROM amc_widgets WHERE 1 = 0") { rs ->
                (1..rs.metaData.columnCount).map { rs.metaData.getColumnName(it).lowercase() }.toSet()
            }
        }.orEmpty()

    @Test
    fun `adds a column the existing table does not have`() {
        val factory = freshFactory()
        factory.createTable(WidgetsV1)

        val statements = factory.addMissingColumns(WidgetsV2)

        assertTrue(statements.isNotEmpty(), "expected DDL for the two new columns")
        assertTrue(
            statements.all { it.startsWith("ALTER TABLE") || it.startsWith("CREATE INDEX") },
            "every statement must be additive, got: $statements"
        )
        assertEquals(setOf("id", "name", "slug", "notes"), factory.columnNames())
    }

    @Test
    fun `creates the index a new column participates in`() {
        val factory = freshFactory()
        factory.createTable(WidgetsV1)

        val statements = factory.addMissingColumns(WidgetsV2)

        assertTrue(
            statements.any { it.contains("amc_widgets_slug_idx", ignoreCase = true) },
            "an index defined on a newly added column has to be created with it, got: $statements"
        )
    }

    @Test
    fun `is a no-op when the live table already matches`() {
        val factory = freshFactory()
        factory.createTable(WidgetsV2)

        assertEquals(emptyList(), factory.addMissingColumns(WidgetsV2))
    }

    @Test
    fun `is idempotent - a second run finds nothing left to add`() {
        val factory = freshFactory()
        factory.createTable(WidgetsV1)

        assertTrue(factory.addMissingColumns(WidgetsV2).isNotEmpty())
        assertEquals(
            emptyList(),
            factory.addMissingColumns(WidgetsV2),
            "the first run must have closed the gap completely"
        )
    }

    @Test
    fun `never drops a column the definition no longer declares`() {
        val factory = freshFactory()
        factory.createTable(WidgetsV2)

        val statements = factory.addMissingColumns(WidgetsV1)

        assertEquals(emptyList(), statements, "a narrower definition is not a request to drop data")
        assertTrue("slug" in factory.columnNames())
        assertTrue("notes" in factory.columnNames())
    }

    /**
     * The reason this computes its own diff instead of calling Exposed's
     * `SchemaUtils.addMissingColumnsStatements`: that function also emits `ALTER COLUMN` for a
     * column whose type drifted. Rewriting a column that already holds data is a migration's
     * decision, not a boot-time one.
     */
    @Test
    fun `never retypes a column whose type drifted`() {
        val factory = freshFactory()
        factory.createTable(WidgetsV1)

        assertEquals(
            emptyList(),
            factory.addMissingColumns(WidgetsRetyped),
            "a type change is drift to REPORT, never DDL to run at boot"
        )
    }

    @Test
    fun `skips a table that does not exist rather than adding all of its columns`() {
        val factory = freshFactory()

        assertEquals(
            emptyList(),
            factory.addMissingColumns(WidgetsV1),
            "an absent table has no columns to alter — creating it belongs to the create step"
        )
    }

    @Test
    fun `fails with the statement the database rejected when a required column cannot be back-filled`() {
        val factory = freshFactory()
        factory.createTable(WidgetsV1)
        transaction(factory.database) {
            // Inserted through the DSL rather than raw SQL: Exposed quotes some identifiers and not
            // others, so hand-written SQL here would be guessing at the folding rules.
            WidgetsV1.insert {
                it[WidgetsV1.id] = 1
                it[WidgetsV1.name] = "existing row"
            }
        }

        val failure = assertFailsWith<Exception> {
            factory.addMissingColumns(WidgetsWithRequiredColumn)
        }

        val messages = generateSequence(failure as Throwable) { it.cause }.mapNotNull { it.message }
        assertTrue(
            messages.any { it.contains("owner", ignoreCase = true) },
            "the diagnosis is the statement itself — the developer has to see which column, got: " +
                messages.toList()
        )
    }

    @Test
    fun `returns nothing for no tables`() {
        assertEquals(emptyList(), freshFactory().addMissingColumns())
    }
}
