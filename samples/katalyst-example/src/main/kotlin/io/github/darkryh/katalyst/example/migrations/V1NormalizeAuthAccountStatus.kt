package io.github.darkryh.katalyst.example.migrations

import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.example.infra.database.tables.AuthAccountsTable
import io.github.darkryh.katalyst.migrations.KatalystMigration
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Migration that backfills auth_accounts.status based on last login.
 */
class V1NormalizeAuthAccountStatus(
    private val databaseFactory: DatabaseFactory
) : KatalystMigration {

    override val id: String = "1_normalize_auth_account_status"
    override val description: String = "Mark accounts with no login history as disabled"
    override val tags: Set<String> = setOf("data-fix")

    override fun up() {
        transaction(databaseFactory.database) {
            exec(
                """
                UPDATE ${AuthAccountsTable.tableName}
                SET status = CASE
                    WHEN last_login_at_millis IS NULL THEN 'disabled'
                    ELSE 'active'
                END
                """.trimIndent()
            )
        }
    }
}
