package io.github.darkryh.katalyst.migrations

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.migrations.service.SchemaDiffService
import org.jetbrains.exposed.v1.core.Table
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SchemaDiffServiceTest {

    private lateinit var databaseFactory: DatabaseFactory
    private lateinit var service: SchemaDiffService
    private lateinit var scriptDir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        databaseFactory = DatabaseFactory.create(
            DatabaseConfig(
                url = "jdbc:h2:mem:migration-utils-test;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            )
        )
        scriptDir = Files.createTempDirectory("katalyst-migrations")
        service = SchemaDiffService(databaseFactory, scriptDir)
    }

    @AfterTest
    fun tearDown() {
        runCatching { databaseFactory.close() }
        runCatching { scriptDir.toFile().deleteRecursively() }
    }

    @Test
    fun `generates statements for missing tables`() {
        val statements = service.statementsRequiredForDatabaseMigration(TempTable)
        assertTrue(statements.isNotEmpty(), "expected at least one statement to align schema")
    }

    @Test
    fun `writes migration script to configured directory`() {
        val script = service.generateMigrationScript("V1__temp.sql", TempTable)
        assertTrue(script.toFile().exists(), "expected generated script ${script.toAbsolutePath()}")
    }
}

private object TempTable : Table("schema_diff_temp") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 64)
    override val primaryKey = PrimaryKey(id)
}
