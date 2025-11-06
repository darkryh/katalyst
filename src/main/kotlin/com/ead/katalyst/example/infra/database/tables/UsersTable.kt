package com.ead.katalyst.example.infra.database.tables

import com.ead.katalyst.tables.Table
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * Users table definition implementing the Katalyst Table marker interface.
 *
 * By implementing [Table], this object is automatically discovered and registered
 * in the Koin DI container during application startup, making it available for
 * injection into repositories and services.
 *
 * Example usage:
 * ```kotlin
 * class UserRepository(private val usersTable: UsersTable) : Repository {
 *     suspend fun getAllUsers(): List<User> {
 *         return transaction {
 *             usersTable.selectAll().map { row -> /* ... */ }
 *         }
 *     }
 * }
 * ```
 */
object UsersTable : LongIdTable("users"), Table {
    val name = varchar("name", 100)
    val email = varchar("email", 150).uniqueIndex()
    val active = bool("active").default(true)
}