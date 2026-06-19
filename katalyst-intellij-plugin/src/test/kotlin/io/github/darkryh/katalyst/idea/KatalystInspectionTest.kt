package io.github.darkryh.katalyst.idea

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.darkryh.katalyst.idea.inspection.KatalystEventHandlerInspection
import io.github.darkryh.katalyst.idea.inspection.KatalystSchedulerInspection

/**
 * PSI-level tests for the scheduler and event-handler inspections. They exercise the real
 * inspection pass via [com.intellij.testFramework.fixtures.CodeInsightTestFixture.doHighlighting]
 * and filter by our own messages, so they don't depend on Ktor/Katalyst being on the classpath
 * (the recognition is resolve-light).
 */
class KatalystInspectionTest : BasePlatformTestCase() {

    private fun highlightsContaining(code: String, needle: String): List<String> {
        myFixture.configureByText("Sample.kt", code)
        return myFixture.doHighlighting().mapNotNull { it.description }.filter { it.contains(needle) }
    }

    // --- scheduler: cron validity ---

    fun testInvalidCronIsFlagged() {
        myFixture.enableInspections(KatalystSchedulerInspection())
        val found = highlightsContaining(
            """
            fun jobs() = scheduler.jobs {
                cron("nightly", "0 0 25 * * ?") { }
            }
            """.trimIndent(),
            "Invalid cron expression",
        )
        assertTrue("expected an invalid-cron warning", found.isNotEmpty())
    }

    fun testValidCronIsSilent() {
        myFixture.enableInspections(KatalystSchedulerInspection())
        val found = highlightsContaining(
            """
            fun jobs() = scheduler.jobs {
                cron("nightly", "0 0 2 * * ?") { }
            }
            """.trimIndent(),
            "Invalid cron expression",
        )
        assertEmpty(found)
    }

    fun testCronOutsideJobsBlockIsIgnored() {
        myFixture.enableInspections(KatalystSchedulerInspection())
        // A `cron(...)` call that is not inside a jobs { } block must not be validated.
        val found = highlightsContaining("""fun f() { cron("x", "0 0 25 * * ?") }""", "Invalid cron expression")
        assertEmpty(found)
    }

    // --- scheduler: duplicate names ---

    fun testDuplicateJobNameIsFlagged() {
        myFixture.enableInspections(KatalystSchedulerInspection())
        val found = highlightsContaining(
            """
            fun jobs() = scheduler.jobs {
                cron("dup", "0 0 2 * * ?") { }
                fixedDelay("dup", x) { }
            }
            """.trimIndent(),
            "Duplicate scheduler job name",
        )
        assertTrue("expected duplicate-name warnings", found.size >= 2)
    }

    fun testUniqueJobNamesAreSilent() {
        myFixture.enableInspections(KatalystSchedulerInspection())
        val found = highlightsContaining(
            """
            fun jobs() = scheduler.jobs {
                cron("a", "0 0 2 * * ?") { }
                cron("b", "0 0 3 * * ?") { }
            }
            """.trimIndent(),
            "Duplicate scheduler job name",
        )
        assertEmpty(found)
    }

    // --- events: eventType vs type argument ---

    fun testEventHandlerMismatchIsFlagged() {
        myFixture.enableInspections(KatalystEventHandlerInspection())
        val found = highlightsContaining(
            """
            class H : EventHandler<UserCreated> {
                override val eventType = UserDeleted::class
                override suspend fun handle(event: UserCreated) { }
            }
            """.trimIndent(),
            "must match the type argument",
        )
        assertTrue("expected an eventType mismatch warning", found.isNotEmpty())
    }

    fun testEventHandlerMatchIsSilent() {
        myFixture.enableInspections(KatalystEventHandlerInspection())
        val found = highlightsContaining(
            """
            class H : EventHandler<UserCreated> {
                override val eventType = UserCreated::class
                override suspend fun handle(event: UserCreated) { }
            }
            """.trimIndent(),
            "must match the type argument",
        )
        assertEmpty(found)
    }
}
