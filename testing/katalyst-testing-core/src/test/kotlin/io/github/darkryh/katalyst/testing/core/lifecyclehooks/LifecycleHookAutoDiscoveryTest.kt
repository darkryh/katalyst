package io.github.darkryh.katalyst.testing.core.lifecyclehooks

import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Regression coverage for lifecycle-hook auto-discovery.
 *
 * Implementing [io.github.darkryh.katalyst.di.lifecycle.StartupHook] or
 * [io.github.darkryh.katalyst.di.lifecycle.ReadyHook] must be sufficient on its own:
 * hooks are scanned, dependency-validated and constructor-injected without also
 * having to implement `Component`/`Service`.
 *
 * Before lifecycle categories were scanned, a hook that implemented only the hook
 * interface was silently never discovered and never executed — no error, no warning.
 */
class LifecycleHookAutoDiscoveryTest {

    private var environment: KatalystTestEnvironment? = null

    @BeforeTest
    fun setUp() {
        HookExecutionLog.reset()
    }

    @AfterTest
    fun tearDown() {
        environment?.close()
        environment = null
    }

    private fun bootstrap(): KatalystTestEnvironment =
        katalystTestEnvironment {
            scan("io.github.darkryh.katalyst.testing.core.lifecyclehooks")
            disableScheduler()
        }.also { environment = it }

    @Test
    fun `startup hook is discovered and executed without a Component marker`() {
        bootstrap()

        assertContains(HookExecutionLog.executed, "bare-startup")
    }

    @Test
    fun `ready hook is discovered and executed without a Component marker`() {
        bootstrap()

        assertContains(HookExecutionLog.executed, "bare-ready")
    }

    @Test
    fun `startup hook receives constructor injection without a Component marker`() {
        bootstrap()

        assertContains(HookExecutionLog.executed, "injecting-startup:injected")
    }

    @Test
    fun `ready hook receives constructor injection without a Component marker`() {
        bootstrap()

        assertContains(HookExecutionLog.executed, "injecting-ready:injected")
    }

    @Test
    fun `hook that also implements Component keeps working`() {
        bootstrap()

        assertContains(HookExecutionLog.executed, "component-marked-startup:injected")
    }

    @Test
    fun `hook is registered in the container under its concrete type`() {
        val env = bootstrap()

        assertTrue(
            env.container.getOrNull(BareStartupHook::class) != null,
            "A bare StartupHook should be resolvable by its concrete type"
        )
    }

    @Test
    fun `startup hooks execute in order`() {
        bootstrap()

        val early = HookExecutionLog.executed.indexOf("early-startup")
        val late = HookExecutionLog.executed.indexOf("late-startup")

        assertTrue(early >= 0 && late >= 0, "both ordering hooks should have executed")
        assertTrue(early < late, "hook order should be respected: $early !< $late")
    }

    @Test
    fun `each discovered hook executes exactly once`() {
        bootstrap()

        val duplicates = HookExecutionLog.executed
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }

        assertTrue(duplicates.isEmpty(), "hooks executed more than once: $duplicates")
    }
}
