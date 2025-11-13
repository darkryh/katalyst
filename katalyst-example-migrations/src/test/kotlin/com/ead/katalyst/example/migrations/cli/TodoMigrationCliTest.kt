package com.ead.katalyst.example.migrations.cli

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.example.migrations.schema.TodoItemsTable
import com.ead.katalyst.example.migrations.schema.TodoListsTable
import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Files
import java.nio.file.Paths
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class TodoMigrationCliTest {

    private val dbFilePrefix = Paths.get("build/cli-test-db")
    private val config = DatabaseConfig(
        url = "jdbc:h2:file:${dbFilePrefix.toAbsolutePath()};AUTO_SERVER=TRUE;MODE=MYSQL",
        driver = "org.h2.Driver",
        username = "sa",
        password = ""
    )

    @Test
    fun `cli applies migrations end-to-end`() {
        cleanDatabaseFiles()

        val cli = TodoMigrationCli(
            configProvider = { config },
            scriptDirectory = Paths.get("build/cli-sql")
        )

        cli.run()

        com.ead.katalyst.database.DatabaseFactory.create(config).use { factory ->
            transaction(factory.database) {
                assertEquals(1, TodoListsTable.selectAll().count().toInt())
                assertEquals(2, TodoItemsTable.selectAll().count().toInt())
            }
        }
    }

    private fun cleanDatabaseFiles() {
        listOf(
            "${dbFilePrefix}.mv.db",
            "${dbFilePrefix}.trace.db"
        ).forEach { path ->
            runCatching { Files.deleteIfExists(Paths.get(path)) }
        }
    }
}
