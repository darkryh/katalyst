package io.github.darkryh.katalyst.testing.core.migrations

import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.migrations.sql.SqlMigration

/**
 * Migrations used by [MigrationBootRegressionTest].
 *
 * These deliberately live in their own package: the boot test scans exactly this package so
 * the assertions are about migrations only, with no unrelated components in play.
 */

/**
 * The shape that regressed in issue #16 — a migration reached through the abstract
 * [SqlMigration] intermediate rather than by implementing [KatalystMigration] directly.
 * Its only direct supertype is an abstract class, so any binding computed from *declared*
 * supertypes misses the marker entirely.
 */
class CreateWidgetsMigration : SqlMigration() {
    override val id: String = "20240101_create_widgets"
    override val description: String = "create widgets table"

    override fun statements(): List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS widgets (id INT PRIMARY KEY, name VARCHAR(64))"
    )
}

/** A second migration, to prove ordering and that several migrations are all reachable. */
class AddWidgetsIndexMigration : SqlMigration() {
    override val id: String = "20240102_add_widgets_index"
    override val description: String = "index widgets.name"

    override fun statements(): List<String> = listOf(
        "CREATE INDEX IF NOT EXISTS idx_widgets_name ON widgets (name)"
    )
}

/** A migration implementing the marker interface directly, the other supported shape. */
class DirectInterfaceMigration : KatalystMigration {
    override val id: String = "20240103_direct_interface"
    override val description: String = "create gadgets table"

    override fun up() {
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
            .exec("CREATE TABLE IF NOT EXISTS gadgets (id INT PRIMARY KEY)")
    }
}
