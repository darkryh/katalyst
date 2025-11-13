package com.ead.katalyst.example.migrations

import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.example.migrations.schema.TodoItemsTable
import com.ead.katalyst.example.migrations.schema.TodoListsTable
import com.ead.katalyst.migrations.KatalystMigration
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Seeds a single todo list with two items to make manual verification easier.
 * Tagged as `demo` so production deployments can skip it via MigrationOptions.
 */
class V2024102002SeedSampleTodoList(
    private val databaseFactory: DatabaseFactory
) : KatalystMigration {

    private val logger = LoggerFactory.getLogger(V2024102002SeedSampleTodoList::class.java)

    override val id: String = "2024102002_seed_sample_todo_list"
    override val description: String = "Insert a demo todo list with a couple of items"
    override val tags: Set<String> = setOf("demo", "seed")
    override val blocking: Boolean = false

    override fun up() {
        transaction(databaseFactory.database) {
            val existing = TodoListsTable
                .selectAll()
                .where { TodoListsTable.name eq SEED_LIST_NAME }
                .limit(1)
                .firstOrNull()

            if (existing != null) {
                logger.info("[{}] Demo list already present, skipping insert", id)
                return@transaction
            }

            val now = System.currentTimeMillis()
            val todoListId = TodoListsTable.insertAndGetId { statement ->
                statement[name] = SEED_LIST_NAME
                statement[createdAtMillis] = now
            }.value

            SAMPLE_ITEMS.forEach { item ->
                TodoItemsTable.insert { statement ->
                    statement[TodoItemsTable.listId] = EntityID(todoListId, TodoListsTable)
                    statement[description] = item
                    statement[completedAtMillis] = null
                }
            }

            logger.info("[{}] Seeded demo todo list with {} item(s)", id, SAMPLE_ITEMS.size)
        }
    }

    private companion object {
        private const val SEED_LIST_NAME = "Katalyst Demo Tasks"
        private val SAMPLE_ITEMS = listOf(
            "Wire migrations into the app",
            "Verify schema drift detection"
        )
    }
}
