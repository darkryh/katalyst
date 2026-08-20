package io.github.darkryh.katalyst.migrations.sql

import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.migrations.util.hashStatements
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/**
 * Convenience base class for migrations expressed as raw SQL statements.
 *
 * Subclasses provide a stable list of statements; the runner executes them
 * inside Exposed's transaction context.
 */
abstract class SqlMigration : KatalystMigration {

    /**
     * Statements that will be executed sequentially.
     */
    protected abstract fun statements(): List<String>

    internal fun statementsForTesting(): List<String> = statements()

    override val checksum: String
        get() = hashStatements(statements())

    override fun up() {
        val tx = TransactionManager.current()
        statements().forEach { sql ->
            tx.exec(sql)
        }
    }
}
