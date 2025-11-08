package com.ead.katalyst.example.infra.database.tables

import com.ead.katalyst.tables.Table
import org.jetbrains.exposed.dao.id.LongIdTable

object AuthAccountsTable : LongIdTable("auth_accounts"), Table {
    val email = varchar("email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAtMillis = long("created_at_ms")
    val lastLoginAtMillis = long("last_login_at_ms").nullable()
}
