package io.github.darkryh.katalyst.testing.core.migrations

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.di.config.SchemaPolicy
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.migrations.feature.MigrationFeature
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end regression coverage for issue #16.
 *
 * `MigrationFeature.onReady` looks its migrations up with `getAll<KatalystMigration>()`.
 * Discovered migrations used to be registered under their concrete class only, so that
 * lookup returned an empty list and no migration ever ran — with no error and no warning.
 * Boot reported success while the schema was never touched.
 *
 * Every test here boots the real container and asserts against the real database, because
 * the defect lived in the wiring *between* discovery and lookup: no unit test of either half
 * could see it. The existing migration tests all drive `MigrationRunner` with an explicit
 * list, which is precisely the path that kept working throughout.
 *
 * The schema policy is never `CREATE_MISSING` here. That policy creates tables itself, which
 * is what masked this bug in production: the DDL still appeared, just from the schema policy
 * instead of from the migration.
 */
class MigrationBootRegressionTest {

    private var environment: KatalystTestEnvironment? = null
    private lateinit var jdbcUrl: String

    @AfterTest
    fun tearDown() {
        environment?.close()
        environment = null
    }

    private fun boot(policy: SchemaPolicy): KatalystTestEnvironment {
        jdbcUrl = "jdbc:h2:mem:katalyst-mig-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        return katalystTestEnvironment {
            database(
                DatabaseConfig(
                    url = jdbcUrl,
                    driver = "org.h2.Driver",
                    username = "sa",
                    password = ""
                )
            )
            scan("io.github.darkryh.katalyst.testing.core.migrations")
            schema(policy)
            disableScheduler()
            feature(MigrationFeature(MigrationOptions(runAtStartup = true)))
        }.also { environment = it }
    }

    private fun query(sql: String): Int? =
        runCatching {
            DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
                connection.createStatement().executeQuery(sql).use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }.getOrNull()

    private fun tableRowCount(table: String): Int? = query("SELECT COUNT(*) FROM $table")

    private fun assertMigrationsApplied(policy: SchemaPolicy) {
        assertEquals(
            0,
            tableRowCount("widgets"),
            "[$policy] the widgets table should have been created by CreateWidgetsMigration"
        )
        assertEquals(
            0,
            tableRowCount("gadgets"),
            "[$policy] the gadgets table should have been created by DirectInterfaceMigration"
        )
        assertEquals(
            3,
            tableRowCount("katalyst_schema_migrations"),
            "[$policy] all three discovered migrations should be recorded in history"
        )
    }

    @Test
    fun `migrations run at startup under the none schema policy`() {
        boot(SchemaPolicy.NONE)
        assertMigrationsApplied(SchemaPolicy.NONE)
    }

    @Test
    fun `migrations run at startup under the validate schema policy`() {
        boot(SchemaPolicy.VALIDATE)
        assertMigrationsApplied(SchemaPolicy.VALIDATE)
    }

    @Test
    fun `migrations run at startup under createMissingAndValidate`() {
        boot(SchemaPolicy.CREATE_MISSING_AND_VALIDATE)
        assertMigrationsApplied(SchemaPolicy.CREATE_MISSING_AND_VALIDATE)
    }

    @Test
    fun `every discovered migration is reachable through the container`() {
        val env = boot(SchemaPolicy.NONE)

        val migrations = env.container.getAll(KatalystMigration::class)

        assertEquals(
            listOf(
                "20240101_create_widgets",
                "20240102_add_widgets_index",
                "20240103_direct_interface",
            ),
            migrations.map { it.id }.sorted(),
            "getAll<KatalystMigration> must return every discovered migration, including " +
                "those reached through the abstract SqlMigration base class"
        )
    }

    @Test
    fun `migrations are not re-applied on a second boot against the same database`() {
        boot(SchemaPolicy.NONE)
        val firstUrl = jdbcUrl

        // Assert the precondition explicitly. Without it this test is vacuously true when
        // migrations never run at all: the history table is absent after both boots, and
        // "unchanged" trivially holds. Idempotence is only meaningful once something was
        // actually applied, so pin the first boot to a concrete count.
        assertEquals(
            3,
            tableRowCount("katalyst_schema_migrations"),
            "precondition: the first boot must apply all three migrations"
        )

        environment?.close()
        environment = null

        // Boot again against the SAME database; already-applied migrations must be skipped.
        environment = katalystTestEnvironment {
            database(
                DatabaseConfig(url = firstUrl, driver = "org.h2.Driver", username = "sa", password = "")
            )
            scan("io.github.darkryh.katalyst.testing.core.migrations")
            schema(SchemaPolicy.NONE)
            disableScheduler()
            feature(MigrationFeature(MigrationOptions(runAtStartup = true)))
        }
        jdbcUrl = firstUrl

        assertEquals(
            3,
            tableRowCount("katalyst_schema_migrations"),
            "a second boot must not re-apply migrations already recorded in history"
        )
    }

    @Test
    fun `runAtStartup false discovers migrations without executing them`() {
        jdbcUrl = "jdbc:h2:mem:katalyst-mig-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        environment = katalystTestEnvironment {
            database(
                DatabaseConfig(url = jdbcUrl, driver = "org.h2.Driver", username = "sa", password = "")
            )
            scan("io.github.darkryh.katalyst.testing.core.migrations")
            schema(SchemaPolicy.NONE)
            disableScheduler()
            feature(MigrationFeature(MigrationOptions(runAtStartup = false)))
        }

        assertTrue(
            tableRowCount("widgets") == null,
            "runAtStartup=false must not execute migrations"
        )
        assertEquals(
            3,
            environment!!.container.getAll(KatalystMigration::class).size,
            "migrations must still be discovered and registered when runAtStartup=false"
        )
    }
}
