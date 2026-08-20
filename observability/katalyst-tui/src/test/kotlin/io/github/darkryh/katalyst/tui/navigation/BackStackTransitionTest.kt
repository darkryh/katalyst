package io.github.darkryh.katalyst.tui.navigation

import io.github.darkryh.dispatch.navigation.NavBackStack
import io.github.darkryh.dispatch.navigation.NavKey
import io.github.darkryh.dispatch.navigation.canGoBack
import io.github.darkryh.dispatch.navigation.navigate
import io.github.darkryh.dispatch.navigation.popBackStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where "back" actually lands, for every route the inspector can reach.
 *
 * Both Escape and ArrowLeft resolve to the same `popBackStack()`, so this suite covers the
 * destination half of the feature while [io.github.darkryh.katalyst.tui.input.BackNavigationKeyRoutingTest]
 * covers the routing half. The drill-down case is the one worth having: Scheduler → a job → back
 * must return to Scheduler, not skip to Home.
 */
class BackStackTransitionTest {

    /** Every subsystem tile reachable from the dashboard. */
    private val subsystemRoutes: List<NavKey> = listOf(
        BootRoute, WiringRoute, HttpRoute, SchedulerRoute, PersistenceRoute,
        TransactionsRoute, MigrationsRoute, EventsRoute, WebSocketsRoute, ConfigRoute,
    )

    private fun stack() = NavBackStack<NavKey>(HomeRoute)

    @Test
    fun `every subsystem route returns to Home`() {
        subsystemRoutes.forEach { route ->
            val stack = stack()
            stack.navigate(route)
            assertEquals(listOf(HomeRoute, route), stack.toList(), "navigating to $route")

            assertTrue(stack.popBackStack(), "popping from $route must succeed")
            assertEquals(listOf(HomeRoute), stack.toList(), "back from $route lands on Home")
        }
    }

    @Test
    fun `the scheduler drill-down unwinds one level at a time`() {
        val stack = stack()
        stack.navigate(SchedulerRoute)
        stack.navigate(SchedulerJobRoute("nightly-report"))
        assertEquals(3, stack.size)

        assertTrue(stack.popBackStack())
        assertEquals(
            SchedulerRoute,
            stack.last(),
            "back from a job must land on Scheduler, not skip to Home",
        )

        assertTrue(stack.popBackStack())
        assertEquals(HomeRoute, stack.last())
    }

    @Test
    fun `popping at Home is a safe no-op and cannot exit the app`() {
        // Hammering the back key on the dashboard must never empty the stack: an empty NavBackStack
        // is an error condition, and the renderer has nothing to draw.
        val stack = stack()

        repeat(50) {
            assertFalse(stack.popBackStack(), "there is nothing to pop at the root")
        }

        assertEquals(listOf(HomeRoute), stack.toList())
    }

    @Test
    fun `canGoBack reports whether back has a destination`() {
        val stack = stack()
        assertFalse(stack.canGoBack(), "no destination from the root")

        stack.navigate(EventsRoute)
        assertTrue(stack.canGoBack())

        stack.popBackStack()
        assertFalse(stack.canGoBack())
    }

    @Test
    fun `deep navigation unwinds to exactly the root`() {
        val stack = stack()
        repeat(500) { i -> stack.navigate(subsystemRoutes[i % subsystemRoutes.size]) }
        assertEquals(501, stack.size)

        while (stack.popBackStack()) { /* unwind */ }

        assertEquals(listOf(HomeRoute), stack.toList(), "unwinding must terminate at the root")
    }

    @Test
    fun `drill-down routes with different arguments are distinct entries`() {
        // SchedulerJobRoute is the only parameterised route; its content key must vary by job or
        // two different jobs would share one entry.
        assertEquals(SchedulerJobRoute("a"), SchedulerJobRoute("a"))
        assertTrue(SchedulerJobRoute("a") != SchedulerJobRoute("b"))

        val stack = stack()
        stack.navigate(SchedulerRoute)
        stack.navigate(SchedulerJobRoute("a"))
        stack.navigate(SchedulerJobRoute("b"))

        assertEquals(4, stack.size)
        stack.popBackStack()
        assertEquals(SchedulerJobRoute("a"), stack.last())
    }

    @Test
    fun `navigating to the same route twice requires two pops`() {
        val stack = stack()
        stack.navigate(ConfigRoute)
        stack.navigate(ConfigRoute)

        assertEquals(3, stack.size)
        stack.popBackStack()
        assertEquals(ConfigRoute, stack.last())
        stack.popBackStack()
        assertEquals(HomeRoute, stack.last())
    }

    @Test
    fun `the stack always starts on Home`() {
        assertEquals(listOf(HomeRoute), stack().toList())
    }
}
