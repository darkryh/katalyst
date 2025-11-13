package com.ead.katalyst.example.migrations.schema

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * Tiny todo list schema kept inside the migration module so we can demonstrate
 * migrations without depending on the larger sample application.
 */
object TodoListsTable : LongIdTable("todo_lists") {
    val name = varchar("name", 120)
    val createdAtMillis = long("created_at_millis")
}

object TodoItemsTable : LongIdTable("todo_items") {
    val listId = reference(
        name = "list_id",
        foreign = TodoListsTable,
        onDelete = ReferenceOption.CASCADE
    )
    val description = varchar("description", 255)
    val completedAtMillis = long("completed_at_millis").nullable()
}
