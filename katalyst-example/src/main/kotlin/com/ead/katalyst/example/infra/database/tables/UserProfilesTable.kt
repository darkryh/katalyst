package com.ead.katalyst.example.infra.database.tables

import com.ead.katalyst.tables.Table
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

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
