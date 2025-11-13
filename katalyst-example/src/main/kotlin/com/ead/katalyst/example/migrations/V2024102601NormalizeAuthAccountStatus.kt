package com.ead.katalyst.example.migrations

import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.migrations.KatalystMigration
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Migration that backfills auth_accounts.status based on last login.
 */
class V2024102601NormalizeAuthAccountStatus(
    private val databaseFactory: DatabaseFactory
) : KatalystMigration {

    override val id: String = "2024102601_normalize_auth_account_status"
    override val description: String = "Mark accounts with no login history as disabled"
    override val tags: Set<String> = setOf("data-fix")

    override fun up() {
        transaction(databaseFactory.database) {
            exec(
                """
                UPDATE auth_accounts
                SET status = CASE
                    WHEN last_login_at_millis IS NULL THEN 'disabled'
                    ELSE 'active'
                END
                """.trimIndent()
            )
        }
    }
}
