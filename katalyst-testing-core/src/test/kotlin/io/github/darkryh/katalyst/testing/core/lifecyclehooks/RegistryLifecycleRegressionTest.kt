package io.github.darkryh.katalyst.testing.core.lifecyclehooks

import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression coverage for registry lifecycle across repeated bootstraps (issue #16).
 *
 * Registries are JVM-global singletons. Before this was fixed, `stopKatalystStandalone()`
 * left them fully populated, so a second bootstrap in the same JVM unioned stale hook
 * instances (bound to a dead container) with the fresh ones. Every hook then executed
 * twice — and the stale instances outlived the container they were built for.
 *
 * The bug was invisible only because container lookups for hooks returned nothing, so the
 * *stale* instances were the only ones that ever ran.
 */
class RegistryLifecycleRegressionTest {

    @BeforeTest
    fun setUp() {
        HookExecutionLog.reset()
    }

    @AfterTest
    fun tearDown() {
        HookExecutionLog.reset()
    }

    private fun bootstrapAndClose() {
        katalystTestEnvironment {
            scan("io.github.darkryh.katalyst.testing.core.lifecyclehooks")
            disableScheduler()
        }.close()
    }

    @Test
    fun `hooks execute exactly once per bootstrap across repeated bootstraps`() {
        bootstrapAndClose()
        val firstRun = HookExecutionLog.executed.toList()
        assertTrue(firstRun.isNotEmpty(), "first bootstrap should have executed hooks")

        HookExecutionLog.reset()

        bootstrapAndClose()
        val secondRun = HookExecutionLog.executed.toList()

        val duplicates = secondRun.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue(
            duplicates.isEmpty(),
            "second bootstrap re-ran hooks from the first: $duplicates"
        )
        assertEquals(
            firstRun.sorted(),
            secondRun.sorted(),
            "each bootstrap should execute the same hook set exactly once"
        )
    }

    /**
     * The assertion that actually detects the leak. Counting ids cannot: a stale hook
     * reports the same id as the fresh instance it displaced, so the counts look correct
     * while the *wrong objects* — bound to an already-stopped container — are running.
     */
    @Test
    fun `second bootstrap runs its own hook instances not the first bootstrap's`() {
        bootstrapAndClose()
        val firstIdentities = HookExecutionLog.executedIdentities.toSet()
        assertTrue(firstIdentities.isNotEmpty(), "first bootstrap should have executed hooks")

        HookExecutionLog.reset()

        bootstrapAndClose()
        val secondIdentities = HookExecutionLog.executedIdentities.toSet()
        assertTrue(secondIdentities.isNotEmpty(), "second bootstrap should have executed hooks")

        val stale = firstIdentities intersect secondIdentities
        assertTrue(
            stale.isEmpty(),
            "second bootstrap re-executed ${stale.size} hook instance(s) left over in a " +
                "global registry by the first bootstrap"
        )
    }

    @Test
    fun `three consecutive bootstraps never accumulate hook executions`() {
        val counts = (1..3).map {
            HookExecutionLog.reset()
            bootstrapAndClose()
            HookExecutionLog.executed.size
        }

        // Without this, "all counts equal" would also hold if no hook ever ran.
        assertTrue(counts.first() > 0, "hooks should have executed on every bootstrap: $counts")

        assertEquals(
            listOf(counts.first(), counts.first(), counts.first()),
            counts,
            "hook execution count grew across bootstraps (registry state leaked): $counts"
        )
    }
}
