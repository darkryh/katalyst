package com.ead.katalyst.example.infra.database.tables

import com.ead.katalyst.core.persistence.Table
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * IMPORTANT: Column names MUST exactly match the entity property names (when converted to snake_case).
 *
 * The repository uses reflection to automatically bind entity properties to table columns.
 * The binding works by converting column names from snake_case to camelCase and matching
 * against entity property names. Therefore, column names MUST use the FULL property name.
 *
 * ✗ WRONG: "created_at_ms" (abbreviation) ← won't match property "createdAtMillis"
 * ✓ CORRECT: "created_at_millis" (full word) ← matches property "createdAtMillis"
 *
 * Supported naming conventions:
 * - snake_case (created_at_millis)
 * - kebab-case (created-at-millis)
 * - camelCase (createdAtMillis)
 */
object AuthAccountsTable : LongIdTable("auth_accounts"), Table {
    val email = varchar("email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAtMillis = long("created_at_millis")
    val lastLoginAtMillis = long("last_login_at_millis").nullable()
}
