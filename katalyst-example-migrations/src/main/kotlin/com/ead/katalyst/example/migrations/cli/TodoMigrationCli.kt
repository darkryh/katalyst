package com.ead.katalyst.example.migrations.cli

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.example.migrations.V2024102001CreateTodoSchema
import com.ead.katalyst.example.migrations.V2024102002SeedSampleTodoList
import com.ead.katalyst.migrations.KatalystMigration
import com.ead.katalyst.migrations.options.MigrationOptions
import com.ead.katalyst.migrations.runner.MigrationRunner
import com.ead.katalyst.migrations.service.SchemaDiffService
import java.nio.file.Paths
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Minimal CLI that bootstraps just enough of the Katalyst stack to run the
 * example migrations without launching the full Ktor server.
 *
 * Usage:
 * ```
 * ./gradlew :katalyst-example-migrations:run
 * ```
 *
 * Override the JDBC connection by setting the following environment variables:
 * - `TODO_DB_URL` (default: `jdbc:h2:file:./build/todo-example-db`)
 * - `TODO_DB_USER` (default: `sa`)
 * - `TODO_DB_PASSWORD` (default: empty)
 * - `TODO_DB_DRIVER` (default: `org.h2.Driver`)
 */
fun main() {
    TodoMigrationCli().run()
}

class TodoMigrationCli(
    private val configProvider: () -> DatabaseConfig = ::loadDatabaseConfigFromEnv,
    private val scriptDirectory: java.nio.file.Path = Paths.get("build/todo-example-sql")
) {

    private val logger = LoggerFactory.getLogger(TodoMigrationCli::class.java)

    fun run() {
        val dbConfig = configProvider()

        val koinApp = startKoin {
            modules(
                module {
                    single { dbConfig }
                    single { DatabaseFactory.create(get()) }
                    single { MigrationOptions() }
                    single { SchemaDiffService(get(), scriptDirectory) }
                    single { MigrationRunner(get(), get()) }
                    single { V2024102001CreateTodoSchema(get(), get()) } bind KatalystMigration::class
                    single { V2024102002SeedSampleTodoList(get()) } bind KatalystMigration::class
                }
            )
        }

        val koin = koinApp.koin
        val migrations = koin.getAll<KatalystMigration>()
        val runner = koin.get<MigrationRunner>()
        val databaseFactory = koin.get<DatabaseFactory>()

        try {
            logger.info("Running {} migration(s) via CLI", migrations.size)
            runner.runMigrations(migrations)
        } finally {
            databaseFactory.close()
            koinApp.close()
        }
    }

    companion object {
        private fun loadDatabaseConfigFromEnv(): DatabaseConfig {
            val url = env("TODO_DB_URL") ?: "jdbc:h2:file:./build/todo-example-db;AUTO_SERVER=TRUE;MODE=MYSQL"
            val user = env("TODO_DB_USER") ?: "sa"
            val password = env("TODO_DB_PASSWORD") ?: ""
            val driver = env("TODO_DB_DRIVER") ?: "org.h2.Driver"

            return DatabaseConfig(
                url = url,
                driver = driver,
                username = user,
                password = password
            )
        }

        private fun env(key: String): String? =
            System.getenv(key)?.takeIf { it.isNotBlank() }
    }
}
