package io.github.darkryh.katalyst.scheduler.config

import java.time.ZoneId
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

/**
 * Comprehensive tests for ScheduleConfig.
 *
 * Tests cover:
 * - Config creation with defaults
 * - Task naming and tagging
 * - Initial delay and timezone settings
 * - Max execution time
 * - Callbacks (onSuccess, onError)
 * - Data class behavior
 * - Practical usage scenarios
 */
class ScheduleConfigTest {

    // ========== CONSTRUCTION TESTS ==========

    @Test
    fun `ScheduleConfig should require taskName`() {
        val config = ScheduleConfig(taskName = "test-task")
        assertEquals("test-task", config.taskName)
    }

    @Test
    fun `ScheduleConfig should use empty tags by default`() {
        val config = ScheduleConfig(taskName = "task")
        assertTrue(config.tags.isEmpty())
    }

    @Test
    fun `ScheduleConfig should use ZERO initial delay by default`() {
        val config = ScheduleConfig(taskName = "task")
        assertEquals(Duration.ZERO, config.initialDelay)
    }

    @Test
    fun `ScheduleConfig should use system timezone by default`() {
        val config = ScheduleConfig(taskName = "task")
        assertEquals(ZoneId.systemDefault(), config.timeZone)
    }

    @Test
    fun `ScheduleConfig should have null maxExecutionTime by default`() {
        val config = ScheduleConfig(taskName = "task")
        assertNull(config.maxExecutionTime)
    }

    @Test
    fun `ScheduleConfig should have no-op onSuccess callback by default`() {
        val config = ScheduleConfig(taskName = "task")
        // Should not throw
        config.onSuccess("task", 100.milliseconds)
    }

    @Test
    fun `ScheduleConfig should have default onError callback returning true`() {
        val config = ScheduleConfig(taskName = "task")
        val result = config.onError("task", RuntimeException(), 1L)
        assertTrue(result)
    }

    // ========== TASK NAME TESTS ==========

    @Test
    fun `ScheduleConfig should support simple task names`() {
        val config = ScheduleConfig(taskName = "backup")
        assertEquals("backup", config.taskName)
    }

    @Test
    fun `ScheduleConfig should support hyphenated task names`() {
        val config = ScheduleConfig(taskName = "backup-database")
        assertEquals("backup-database", config.taskName)
    }

    @Test
    fun `ScheduleConfig should support namespaced task names`() {
        val config = ScheduleConfig(taskName = "auth.session-cleanup")
        assertEquals("auth.session-cleanup", config.taskName)
    }

    // ========== TAGS TESTS ==========

    @Test
    fun `ScheduleConfig should support single tag`() {
        val config = ScheduleConfig(
            taskName = "task",
            tags = setOf("critical")
        )
        assertEquals(1, config.tags.size)
        assertTrue(config.tags.contains("critical"))
    }

    @Test
    fun `ScheduleConfig should support multiple tags`() {
        val config = ScheduleConfig(
            taskName = "task",
            tags = setOf("critical", "database", "nightly")
        )
        assertEquals(3, config.tags.size)
        assertTrue(config.tags.containsAll(listOf("critical", "database", "nightly")))
    }

    @Test
    fun `ScheduleConfig tags should be a Set preventing duplicates`() {
        val config = ScheduleConfig(
            taskName = "task",
            tags = setOf("tag1", "tag2", "tag1")  // Duplicate
        )
        assertEquals(2, config.tags.size)
    }

    // ========== INITIAL DELAY TESTS ==========

    @Test
    fun `ScheduleConfig should support seconds delay`() {
        val config = ScheduleConfig(
            taskName = "task",
            initialDelay = 30.seconds
        )
        assertEquals(30.seconds, config.initialDelay)
    }

    @Test
    fun `ScheduleConfig should support minutes delay`() {
        val config = ScheduleConfig(
            taskName = "task",
            initialDelay = 5.minutes
        )
        assertEquals(5.minutes, config.initialDelay)
    }

    @Test
    fun `ScheduleConfig should support milliseconds delay`() {
        val config = ScheduleConfig(
            taskName = "task",
            initialDelay = 500.milliseconds
        )
        assertEquals(500.milliseconds, config.initialDelay)
    }

    @Test
    fun `ScheduleConfig should support zero delay`() {
        val config = ScheduleConfig(
            taskName = "task",
            initialDelay = Duration.ZERO
        )
        assertEquals(Duration.ZERO, config.initialDelay)
    }

    // ========== TIMEZONE TESTS ==========

    @Test
    fun `ScheduleConfig should support UTC timezone`() {
        val config = ScheduleConfig(
            taskName = "task",
            timeZone = ZoneId.of("UTC")
        )
        assertEquals(ZoneId.of("UTC"), config.timeZone)
    }

    @Test
    fun `ScheduleConfig should support different timezones`() {
        val config1 = ScheduleConfig(taskName = "t1", timeZone = ZoneId.of("America/New_York"))
        val config2 = ScheduleConfig(taskName = "t2", timeZone = ZoneId.of("Europe/London"))
        val config3 = ScheduleConfig(taskName = "t3", timeZone = ZoneId.of("Asia/Tokyo"))

        assertEquals(ZoneId.of("America/New_York"), config1.timeZone)
        assertEquals(ZoneId.of("Europe/London"), config2.timeZone)
        assertEquals(ZoneId.of("Asia/Tokyo"), config3.timeZone)
    }

    // ========== MAX EXECUTION TIME TESTS ==========

    @Test
    fun `ScheduleConfig should support max execution time`() {
        val config = ScheduleConfig(
            taskName = "task",
            maxExecutionTime = 5.minutes
        )
        assertEquals(5.minutes, config.maxExecutionTime)
    }

    @Test
    fun `ScheduleConfig should allow null max execution time`() {
        val config = ScheduleConfig(
            taskName = "task",
            maxExecutionTime = null
        )
        assertNull(config.maxExecutionTime)
    }

    @Test
    fun `ScheduleConfig should support short execution timeouts`() {
        val config = ScheduleConfig(
            taskName = "task",
            maxExecutionTime = 30.seconds
        )
        assertEquals(30.seconds, config.maxExecutionTime)
    }

    // ========== CALLBACK TESTS ==========

    @Test
    fun `onSuccess callback should receive taskName and executionTime`() {
        var receivedTask: String? = null
        var receivedTime: Duration? = null

        val config = ScheduleConfig(
            taskName = "test-task",
            onSuccess = { task, time ->
                receivedTask = task
                receivedTime = time
            }
        )

        config.onSuccess("test-task", 150.milliseconds)

        assertEquals("test-task", receivedTask)
        assertEquals(150.milliseconds, receivedTime)
    }

    @Test
    fun `onSuccess callback should be invoked for successful tasks`() {
        var successCount = 0

        val config = ScheduleConfig(
            taskName = "task",
            onSuccess = { _, _ -> successCount++ }
        )

        config.onSuccess("task", 100.milliseconds)
        config.onSuccess("task", 200.milliseconds)
        config.onSuccess("task", 300.milliseconds)

        assertEquals(3, successCount)
    }

    @Test
    fun `onError callback should receive taskName, exception, and executionCount`() {
        var receivedTask: String? = null
        var receivedException: Throwable? = null
        var receivedCount: Long? = null

        val config = ScheduleConfig(
            taskName = "test-task",
            onError = { task, exception, count ->
                receivedTask = task
                receivedException = exception
                receivedCount = count
                true
            }
        )

        val exception = RuntimeException("test error")
        config.onError("test-task", exception, 5L)

        assertEquals("test-task", receivedTask)
        assertEquals(exception, receivedException)
        assertEquals(5L, receivedCount)
    }

    @Test
    fun `onError callback should return true to continue scheduling`() {
        val config = ScheduleConfig(
            taskName = "task",
            onError = { _, _, _ -> true }
        )

        val shouldContinue = config.onError("task", RuntimeException(), 1L)
        assertTrue(shouldContinue)
    }

    @Test
    fun `onError callback should return false to stop scheduling`() {
        val config = ScheduleConfig(
            taskName = "task",
            onError = { _, _, _ -> false }
        )

        val shouldContinue = config.onError("task", RuntimeException(), 1L)
        assertFalse(shouldContinue)
    }

    @Test
    fun `onError callback should allow conditional retry logic`() {
        val config = ScheduleConfig(
            taskName = "task",
            onError = { _, _, count -> count < 3 }  // Retry first 3 times
        )

        assertTrue(config.onError("task", RuntimeException(), 1L))
        assertTrue(config.onError("task", RuntimeException(), 2L))
        assertFalse(config.onError("task", RuntimeException(), 3L))
        assertFalse(config.onError("task", RuntimeException(), 4L))
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `ScheduleConfig should support toString`() {
        val config = ScheduleConfig(taskName = "test-task")
        val string = config.toString()
        assertTrue(string.contains("ScheduleConfig"))
        assertTrue(string.contains("test-task"))
    }

    @Test
    fun `ScheduleConfig should support copy`() {
        val original = ScheduleConfig(
            taskName = "original",
            tags = setOf("tag1"),
            initialDelay = 10.seconds
        )

        val copied = original.copy(taskName = "modified")

        assertEquals("modified", copied.taskName)
        assertEquals(setOf("tag1"), copied.tags)
        assertEquals(10.seconds, copied.initialDelay)
        assertEquals("original", original.taskName)  // Original unchanged
    }

    @Test
    fun `ScheduleConfig should support copy with multiple changes`() {
        val original = ScheduleConfig(
            taskName = "task",
            initialDelay = 10.seconds,
            timeZone = ZoneId.of("UTC")
        )

        val copied = original.copy(
            taskName = "new-task",
            initialDelay = 30.seconds
        )

        assertEquals("new-task", copied.taskName)
        assertEquals(30.seconds, copied.initialDelay)
        assertEquals(ZoneId.of("UTC"), copied.timeZone)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical database backup task configuration`() {
        val config = ScheduleConfig(
            taskName = "database-backup",
            tags = setOf("critical", "database", "nightly"),
            initialDelay = 1.minutes,
            timeZone = ZoneId.of("UTC"),
            maxExecutionTime = 30.minutes,
            onSuccess = { task, time ->
                println("$task completed in $time")
            },
            onError = { task, exception, count ->
                println("$task failed (attempt $count): ${exception.message}")
                count < 3  // Retry up to 3 times
            }
        )

        assertEquals("database-backup", config.taskName)
        assertEquals(3, config.tags.size)
        assertEquals(30.minutes, config.maxExecutionTime)
    }

    @Test
    fun `typical cache cleanup task configuration`() {
        val config = ScheduleConfig(
            taskName = "cache-cleanup",
            tags = setOf("maintenance", "cache"),
            initialDelay = Duration.ZERO,
            timeZone = ZoneId.systemDefault(),
            maxExecutionTime = 5.minutes
        )

        assertEquals("cache-cleanup", config.taskName)
        assertEquals(Duration.ZERO, config.initialDelay)
        assertEquals(5.minutes, config.maxExecutionTime)
    }

    @Test
    fun `session cleanup with error handling`() {
        val errors = mutableListOf<String>()

        val config = ScheduleConfig(
            taskName = "session-cleanup",
            tags = setOf("security", "session"),
            maxExecutionTime = 2.minutes,
            onError = { task, exception, count ->
                errors.add("Error in $task: ${exception.message}")
                count < 5  // Max 5 retries
            }
        )

        val exception = RuntimeException("Database connection lost")
        config.onError("session-cleanup", exception, 1L)
        config.onError("session-cleanup", exception, 2L)

        assertEquals(2, errors.size)
        assertTrue(errors[0].contains("session-cleanup"))
        assertTrue(errors[0].contains("Database connection lost"))
    }

    @Test
    fun `metrics collection task with success logging`() {
        val executionTimes = mutableListOf<Duration>()

        val config = ScheduleConfig(
            taskName = "collect-metrics",
            tags = setOf("monitoring", "metrics"),
            initialDelay = 30.seconds,
            onSuccess = { _, time ->
                executionTimes.add(time)
            }
        )

        config.onSuccess("collect-metrics", 100.milliseconds)
        config.onSuccess("collect-metrics", 150.milliseconds)
        config.onSuccess("collect-metrics", 120.milliseconds)

        assertEquals(3, executionTimes.size)
        assertEquals(100.milliseconds, executionTimes[0])
        assertEquals(150.milliseconds, executionTimes[1])
        assertEquals(120.milliseconds, executionTimes[2])
    }

    @Test
    fun `task with timezone-specific scheduling`() {
        val config = ScheduleConfig(
            taskName = "business-hours-report",
            tags = setOf("reporting", "business-hours"),
            timeZone = ZoneId.of("America/New_York"),
            initialDelay = 5.minutes,
            maxExecutionTime = 10.minutes
        )

        assertEquals(ZoneId.of("America/New_York"), config.timeZone)
        assertEquals("business-hours-report", config.taskName)
    }

    @Test
    fun `task tagging for filtering and monitoring`() {
        val criticalTasks = listOf(
            ScheduleConfig("backup", tags = setOf("critical", "database")),
            ScheduleConfig("payment-processing", tags = setOf("critical", "payment")),
            ScheduleConfig("cleanup", tags = setOf("maintenance"))
        )

        val critical = criticalTasks.filter { it.tags.contains("critical") }
        val maintenance = criticalTasks.filter { it.tags.contains("maintenance") }

        assertEquals(2, critical.size)
        assertEquals(1, maintenance.size)
    }
}
