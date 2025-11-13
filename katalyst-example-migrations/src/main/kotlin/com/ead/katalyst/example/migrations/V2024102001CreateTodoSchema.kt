package com.ead.katalyst.example.migrations

import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.example.migrations.schema.TodoItemsTable
import com.ead.katalyst.example.migrations.schema.TodoListsTable
import com.ead.katalyst.migrations.KatalystMigration
import com.ead.katalyst.migrations.service.SchemaDiffService
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * First migration for the todo example: creates the `todo_lists` and
 * `todo_items` tables using Exposed's diff utilities so the generated SQL
 * reflects exactly what the DSL models declare.
 */
class V2024102001CreateTodoSchema(
    private val schemaDiffService: SchemaDiffService,
    private val databaseFactory: DatabaseFactory
) : KatalystMigration {

    private val logger = LoggerFactory.getLogger(V2024102001CreateTodoSchema::class.java)

    override val id: String = "2024102001_create_todo_schema"
    override val description: String = "Create todo_lists and todo_items tables"
    override val tags: Set<String> = setOf("schema")

    override fun up() {
        val statements = schemaDiffService.statementsRequiredForDatabaseMigration(
            TodoListsTable,
            TodoItemsTable
        )

        if (statements.isEmpty()) {
            logger.info("[{}] Schema already up to date", id)
            return
        }

        transaction(databaseFactory.database) {
            statements.forEach { sql ->
                logger.debug("[{}] Executing: {}", id, sql)
                exec(sql)
            }
        }

        logger.info("[{}] Applied {} statement(s)", id, statements.size)
    }
}
