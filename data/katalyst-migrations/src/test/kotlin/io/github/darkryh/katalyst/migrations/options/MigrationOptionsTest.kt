package io.github.darkryh.katalyst.migrations.options

import java.nio.file.Paths
import kotlin.test.*

/**
 * Comprehensive tests for MigrationOptions.
 *
 * Tests cover:
 * - Default values
 * - Schema table configuration
 * - Startup behavior
 * - Tag filtering (include/exclude)
 * - Dry-run mode
 * - Stop on failure behavior
 * - Baseline and target versioning
 * - Script directory configuration
 * - Data class behavior
 * - Practical usage scenarios
 */
class MigrationOptionsTest {

    // ========== DEFAULT VALUES TESTS ==========

    @Test
    fun `MigrationOptions should use default schema table name`() {
        val options = MigrationOptions()
        assertEquals("katalyst_schema_migrations", options.schemaTable)
    }

    @Test
    fun `MigrationOptions should run at startup by default`() {
        val options = MigrationOptions()
        assertTrue(options.runAtStartup)
    }

    @Test
    fun `MigrationOptions should have empty includeTags by default`() {
        val options = MigrationOptions()
        assertTrue(options.includeTags.isEmpty())
    }

    @Test
    fun `MigrationOptions should have empty excludeTags by default`() {
        val options = MigrationOptions()
        assertTrue(options.excludeTags.isEmpty())
    }

    @Test
    fun `MigrationOptions should not be in dry-run mode by default`() {
        val options = MigrationOptions()
        assertFalse(options.dryRun)
    }

    @Test
    fun `MigrationOptions should stop on failure by default`() {
        val options = MigrationOptions()
        assertTrue(options.stopOnFailure)
    }

    @Test
    fun `MigrationOptions should have null baseline version by default`() {
        val options = MigrationOptions()
        assertNull(options.baselineVersion)
    }

    @Test
    fun `MigrationOptions should have null target version by default`() {
        val options = MigrationOptions()
        assertNull(options.targetVersion)
    }

    @Test
    fun `MigrationOptions should use default script directory`() {
        val options = MigrationOptions()
        assertEquals(Paths.get("db/migrations"), options.scriptDirectory)
    }

    // ========== SCHEMA TABLE TESTS ==========

    @Test
    fun `MigrationOptions should support custom schema table name`() {
        val options = MigrationOptions(schemaTable = "custom_migrations")
        assertEquals("custom_migrations", options.schemaTable)
    }

    @Test
    fun `MigrationOptions should support schema-qualified table names`() {
        val options = MigrationOptions(schemaTable = "public.migrations")
        assertEquals("public.migrations", options.schemaTable)
    }

    // ========== STARTUP BEHAVIOR TESTS ==========

    @Test
    fun `MigrationOptions should support disabling startup migrations`() {
        val options = MigrationOptions(runAtStartup = false)
        assertFalse(options.runAtStartup)
    }

    @Test
    fun `MigrationOptions should support enabling startup migrations`() {
        val options = MigrationOptions(runAtStartup = true)
        assertTrue(options.runAtStartup)
    }

    // ========== TAG FILTERING TESTS ==========

    @Test
    fun `MigrationOptions should support single include tag`() {
        val options = MigrationOptions(includeTags = setOf("production"))
        assertEquals(1, options.includeTags.size)
        assertTrue(options.includeTags.contains("production"))
    }

    @Test
    fun `MigrationOptions should support multiple include tags`() {
        val options = MigrationOptions(
            includeTags = setOf("production", "hotfix", "critical")
        )
        assertEquals(3, options.includeTags.size)
        assertTrue(options.includeTags.containsAll(listOf("production", "hotfix", "critical")))
    }

    @Test
    fun `MigrationOptions should support single exclude tag`() {
        val options = MigrationOptions(excludeTags = setOf("experimental"))
        assertEquals(1, options.excludeTags.size)
        assertTrue(options.excludeTags.contains("experimental"))
    }

    @Test
    fun `MigrationOptions should support multiple exclude tags`() {
        val options = MigrationOptions(
            excludeTags = setOf("experimental", "dev-only", "test")
        )
        assertEquals(3, options.excludeTags.size)
        assertTrue(options.excludeTags.containsAll(listOf("experimental", "dev-only", "test")))
    }

    @Test
    fun `MigrationOptions should support both include and exclude tags`() {
        val options = MigrationOptions(
            includeTags = setOf("production", "hotfix"),
            excludeTags = setOf("experimental")
        )
        assertEquals(2, options.includeTags.size)
        assertEquals(1, options.excludeTags.size)
    }

    // ========== DRY-RUN MODE TESTS ==========

    @Test
    fun `MigrationOptions should support enabling dry-run mode`() {
        val options = MigrationOptions(dryRun = true)
        assertTrue(options.dryRun)
    }

    @Test
    fun `MigrationOptions should support disabling dry-run mode`() {
        val options = MigrationOptions(dryRun = false)
        assertFalse(options.dryRun)
    }

    // ========== STOP ON FAILURE TESTS ==========

    @Test
    fun `MigrationOptions should support stopping on failure`() {
        val options = MigrationOptions(stopOnFailure = true)
        assertTrue(options.stopOnFailure)
    }

    @Test
    fun `MigrationOptions should support continuing on failure`() {
        val options = MigrationOptions(stopOnFailure = false)
        assertFalse(options.stopOnFailure)
    }

    // ========== BASELINE VERSION TESTS ==========

    @Test
    fun `MigrationOptions should support baseline version`() {
        val options = MigrationOptions(baselineVersion = "1.0.0")
        assertEquals("1.0.0", options.baselineVersion)
    }

    @Test
    fun `MigrationOptions should support semantic versioning baseline`() {
        val options = MigrationOptions(baselineVersion = "2.5.3")
        assertEquals("2.5.3", options.baselineVersion)
    }

    @Test
    fun `MigrationOptions should support numeric baseline`() {
        val options = MigrationOptions(baselineVersion = "001")
        assertEquals("001", options.baselineVersion)
    }

    // ========== TARGET VERSION TESTS ==========

    @Test
    fun `MigrationOptions should support target version`() {
        val options = MigrationOptions(targetVersion = "2.0.0")
        assertEquals("2.0.0", options.targetVersion)
    }

    @Test
    fun `MigrationOptions should support semantic versioning target`() {
        val options = MigrationOptions(targetVersion = "3.1.4")
        assertEquals("3.1.4", options.targetVersion)
    }

    @Test
    fun `MigrationOptions should support numeric target`() {
        val options = MigrationOptions(targetVersion = "010")
        assertEquals("010", options.targetVersion)
    }

    // ========== SCRIPT DIRECTORY TESTS ==========

    @Test
    fun `MigrationOptions should support custom script directory`() {
        val options = MigrationOptions(scriptDirectory = Paths.get("migrations"))
        assertEquals(Paths.get("migrations"), options.scriptDirectory)
    }

    @Test
    fun `MigrationOptions should support nested script directory`() {
        val options = MigrationOptions(scriptDirectory = Paths.get("src/main/resources/db/migration"))
        assertEquals(Paths.get("src/main/resources/db/migration"), options.scriptDirectory)
    }

    @Test
    fun `MigrationOptions should support absolute path for script directory`() {
        val options = MigrationOptions(scriptDirectory = Paths.get("/var/lib/migrations"))
        assertEquals(Paths.get("/var/lib/migrations"), options.scriptDirectory)
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `MigrationOptions should support toString`() {
        val options = MigrationOptions(schemaTable = "migrations")
        val string = options.toString()
        assertTrue(string.contains("MigrationOptions"))
        assertTrue(string.contains("migrations"))
    }

    @Test
    fun `MigrationOptions should support copy`() {
        val original = MigrationOptions(
            schemaTable = "original",
            runAtStartup = true,
            dryRun = false
        )

        val copied = original.copy(schemaTable = "modified")

        assertEquals("modified", copied.schemaTable)
        assertTrue(copied.runAtStartup)
        assertFalse(copied.dryRun)
        assertEquals("original", original.schemaTable)
    }

    @Test
    fun `MigrationOptions should support copy with multiple changes`() {
        val original = MigrationOptions(
            schemaTable = "table1",
            runAtStartup = true,
            dryRun = false
        )

        val copied = original.copy(
            schemaTable = "table2",
            dryRun = true
        )

        assertEquals("table2", copied.schemaTable)
        assertTrue(copied.runAtStartup)
        assertTrue(copied.dryRun)
    }

    @Test
    fun `MigrationOptions should support equality`() {
        val options1 = MigrationOptions(
            schemaTable = "migrations",
            runAtStartup = true
        )

        val options2 = MigrationOptions(
            schemaTable = "migrations",
            runAtStartup = true
        )

        assertEquals(options1, options2)
    }

    @Test
    fun `MigrationOptions should not be equal with different values`() {
        val options1 = MigrationOptions(schemaTable = "table1")
        val options2 = MigrationOptions(schemaTable = "table2")

        assertNotEquals(options1, options2)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `production migration configuration`() {
        val options = MigrationOptions(
            schemaTable = "schema_migrations",
            runAtStartup = true,
            includeTags = setOf("production"),
            excludeTags = setOf("experimental", "dev-only"),
            dryRun = false,
            stopOnFailure = true,
            scriptDirectory = Paths.get("/opt/app/migrations")
        )

        assertEquals("schema_migrations", options.schemaTable)
        assertTrue(options.runAtStartup)
        assertTrue(options.stopOnFailure)
        assertEquals(1, options.includeTags.size)
        assertEquals(2, options.excludeTags.size)
        assertFalse(options.dryRun)
    }

    @Test
    fun `development migration configuration with dry-run`() {
        val options = MigrationOptions(
            schemaTable = "dev_migrations",
            runAtStartup = false,
            dryRun = true,
            stopOnFailure = false,
            scriptDirectory = Paths.get("db/dev")
        )

        assertEquals("dev_migrations", options.schemaTable)
        assertFalse(options.runAtStartup)
        assertTrue(options.dryRun)
        assertFalse(options.stopOnFailure)
    }

    @Test
    fun `baseline migration for existing database`() {
        val options = MigrationOptions(
            baselineVersion = "1.5.0",
            targetVersion = "2.0.0",
            runAtStartup = true
        )

        assertEquals("1.5.0", options.baselineVersion)
        assertEquals("2.0.0", options.targetVersion)
        assertTrue(options.runAtStartup)
    }

    @Test
    fun `hotfix migration with specific tags`() {
        val options = MigrationOptions(
            includeTags = setOf("hotfix", "critical"),
            stopOnFailure = true,
            dryRun = false
        )

        assertTrue(options.includeTags.contains("hotfix"))
        assertTrue(options.includeTags.contains("critical"))
        assertTrue(options.stopOnFailure)
    }

    @Test
    fun `CI pipeline migration configuration`() {
        val options = MigrationOptions(
            schemaTable = "ci_migrations",
            runAtStartup = false,  // Run via CLI
            dryRun = false,
            stopOnFailure = true,
            baselineVersion = null,
            targetVersion = null
        )

        assertFalse(options.runAtStartup)
        assertTrue(options.stopOnFailure)
        assertNull(options.baselineVersion)
        assertNull(options.targetVersion)
    }

    @Test
    fun `testing migration configuration`() {
        val options = MigrationOptions(
            schemaTable = "test_migrations",
            runAtStartup = true,
            includeTags = setOf("test", "dev"),
            dryRun = false,
            stopOnFailure = true
        )

        assertEquals("test_migrations", options.schemaTable)
        assertTrue(options.runAtStartup)
        assertEquals(2, options.includeTags.size)
    }

    @Test
    fun `selective migration execution`() {
        val options = MigrationOptions(
            baselineVersion = "1.0.0",
            targetVersion = "1.5.0",
            includeTags = setOf("data-migration"),
            dryRun = true  // Test first
        )

        assertEquals("1.0.0", options.baselineVersion)
        assertEquals("1.5.0", options.targetVersion)
        assertTrue(options.includeTags.contains("data-migration"))
        assertTrue(options.dryRun)
    }
}
