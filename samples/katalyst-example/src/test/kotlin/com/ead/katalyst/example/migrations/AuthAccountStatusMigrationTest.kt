package com.ead.katalyst.example.migrations

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.example.infra.database.tables.AuthAccountsTable
import com.ead.katalyst.migrations.options.MigrationOptions
import com.ead.katalyst.migrations.runner.MigrationRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class AuthAccountStatusMigrationTest {

    private lateinit var databaseFactory: DatabaseFactory

    @BeforeTest
    fun setUp() {
        databaseFactory = DatabaseFactory.create(
            DatabaseConfig(
                url = "jdbc:h2:mem:auth-status-test;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            )
        )

        transaction(databaseFactory.database) {
            SchemaUtils.create(AuthAccountsTable)
        }
    }

    @AfterTest
    fun tearDown() {
        runCatching { databaseFactory.close() }
    }

    @Test
    fun `migration updates dormant accounts and records history`() {
        transaction(databaseFactory.database) {
            AuthAccountsTable.insert { statement ->
                statement[AuthAccountsTable.email] = "dormant@example.com"
                statement[AuthAccountsTable.passwordHash] = "hash"
                statement[AuthAccountsTable.createdAtMillis] = System.currentTimeMillis()
                statement[AuthAccountsTable.lastLoginAtMillis] = null
                statement[AuthAccountsTable.status] = "pending"
            }
            AuthAccountsTable.insert { statement ->
                statement[AuthAccountsTable.email] = "active@example.com"
                statement[AuthAccountsTable.passwordHash] = "hash"
                statement[AuthAccountsTable.createdAtMillis] = System.currentTimeMillis()
                statement[AuthAccountsTable.lastLoginAtMillis] = System.currentTimeMillis()
                statement[AuthAccountsTable.status] = "pending"
            }
        }

        val runner = MigrationRunner(databaseFactory, MigrationOptions())
        runner.runMigrations(listOf(V2024102601NormalizeAuthAccountStatus(databaseFactory)))

        val dormantStatus = transaction(databaseFactory.database) {
            AuthAccountsTable
                .selectAll()
                .andWhere { AuthAccountsTable.email eq "dormant@example.com" }
                .map { it[AuthAccountsTable.status] }
                .single()
        }
        val activeStatus = transaction(databaseFactory.database) {
            AuthAccountsTable
                .selectAll()
                .andWhere { AuthAccountsTable.email eq "active@example.com" }
                .map { it[AuthAccountsTable.status] }
                .single()
        }

        assertEquals("disabled", dormantStatus)
        assertEquals("active", activeStatus)

        val historyCount = transaction(databaseFactory.database) {
            exec(
                "SELECT COUNT(*) FROM katalyst_schema_migrations WHERE migration_id = '2024102601_normalize_auth_account_status'"
            ) { rs ->
                if (rs.next()) rs.getLong(1) else 0L
            }
        }

        assertEquals(1L, historyCount)
    }
}
