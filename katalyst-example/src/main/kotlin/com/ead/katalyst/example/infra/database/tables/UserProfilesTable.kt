package com.ead.katalyst.example.infra.database.tables

import com.ead.katalyst.core.persistence.Table
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

/**
 * IMPORTANT: Column names MUST exactly match the entity property names (when converted to snake_case).
 *
 * The repository uses reflection to automatically bind entity properties to table columns.
 * The binding works by converting column names from snake_case to camelCase and matching
 * against entity property names. Therefore, column names MUST use the FULL property name.
 *
 * Example mappings in this table:
 * - "account_id" (snake_case) ↔ "accountId" (camelCase)
 * - "display_name" (snake_case) ↔ "displayName" (camelCase)
 * - "avatar_url" (snake_case) ↔ "avatarUrl" (camelCase)
 * - "bio" (direct match) ↔ "bio" (property)
 *
 * Supported naming conventions:
 * - snake_case (account_id)
 * - kebab-case (account-id)
 * - camelCase (accountId)
 */
object UserProfilesTable : LongIdTable("user_profiles"), Table {
    val accountId = reference(
        name = "account_id",
        foreign = AuthAccountsTable,
        onDelete = ReferenceOption.CASCADE
    )
    val displayName = varchar("display_name", 120)
    val bio = text("bio").nullable()
    val avatarUrl = varchar("avatar_url", 255).nullable()
}
