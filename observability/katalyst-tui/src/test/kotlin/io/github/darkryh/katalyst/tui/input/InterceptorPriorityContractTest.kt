package io.github.darkryh.katalyst.tui.input

import io.github.darkryh.dispatch.input.Key
import io.github.darkryh.dispatch.runtime.KeyboardInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The framework guarantees the inspector's keyboard model is built on.
 *
 * These are Dispatch's rules, not Katalyst's — pinned here deliberately. The back-navigation design
 * chooses its priority on the strength of them, so if a Dispatch upgrade changed any one of these,
 * the inspector would misroute keys and every screen-level test would still pass. This suite is the
 * tripwire for that.
 */
class InterceptorPriorityContractTest {

    @Test
    fun `higher priority runs first`() {
        val order = mutableListOf<String>()
        val i = KeyboardInterceptor()
        i.register(priority = 0) { order += "low"; false }
        i.register(priority = 100) { order += "high"; false }

        i.tryIntercept(KeyRoutingHarness.event("Escape"))

        assertEquals(listOf("high", "low"), order)
    }

    @Test
    fun `equal priority runs most recently registered first`() {
        val order = mutableListOf<String>()
        val i = KeyboardInterceptor()
        i.register(priority = 0) { order += "first"; false }
        i.register(priority = 0) { order += "second"; false }

        i.tryIntercept(KeyRoutingHarness.event("Escape"))

        assertEquals(listOf("second", "first"), order, "ties break by registration recency")
    }

    @Test
    fun `the first handler returning true stops the chain`() {
        val order = mutableListOf<String>()
        val i = KeyboardInterceptor()
        i.register(priority = 0) { order += "lower"; false }
        i.register(priority = 10) { order += "consumer"; true }

        assertTrue(i.tryIntercept(KeyRoutingHarness.event("Escape")))

        assertEquals(listOf("consumer"), order, "a consumed key never reaches lower layers")
    }

    @Test
    fun `an unconsumed key reports false`() {
        val i = KeyboardInterceptor()
        i.register(priority = 0) { false }

        assertFalse(i.tryIntercept(KeyRoutingHarness.event("ArrowLeft")))
    }

    @Test
    fun `disposing a registration removes exactly that handler`() {
        // This is what conditional composition does when a screen leaves the tree. The inspector
        // relies on it: leaving Home must surrender ArrowLeft to the back binding, and entering
        // Home must take it away again.
        val order = mutableListOf<String>()
        val i = KeyboardInterceptor()
        val disposeA = i.register(priority = 0) { order += "a"; false }
        i.register(priority = 0) { order += "b"; false }

        disposeA()
        i.tryIntercept(KeyRoutingHarness.event("Escape"))

        assertEquals(listOf("b"), order)
    }

    @Test
    fun `hasInterceptors reflects registration and disposal`() {
        val i = KeyboardInterceptor()
        assertFalse(i.hasInterceptors())
        val dispose = i.register(priority = 0) { false }
        assertTrue(i.hasInterceptors())
        dispose()
        assertFalse(i.hasInterceptors())
    }

    @Test
    fun `dispatching the same event object twice replays the cached verdict`() {
        // tryIntercept short-circuits on reference identity. Pinned because it silently invalidates
        // any test that reuses one event object across dispatches — the handler simply never runs
        // the second time, and the test passes while proving nothing.
        var calls = 0
        val i = KeyboardInterceptor()
        i.register(priority = 0) { calls++; true }

        val event = KeyRoutingHarness.event("Escape")
        assertTrue(i.tryIntercept(event))
        assertTrue(i.tryIntercept(event))

        assertEquals(1, calls, "the second dispatch of the same instance must not re-run handlers")
    }

    @Test
    fun `reset clears the identity cache so the same event dispatches again`() {
        var calls = 0
        val i = KeyboardInterceptor()
        i.register(priority = 0) { calls++; true }

        val event = KeyRoutingHarness.event("Escape")
        i.tryIntercept(event)
        i.reset()
        i.tryIntercept(event)

        assertEquals(2, calls)
    }

    @Test
    fun `distinct event instances for the same key both dispatch`() {
        // The harness builds a fresh event per press for exactly this reason.
        var calls = 0
        val i = KeyboardInterceptor()
        i.register(priority = 0) { calls++; true }

        i.tryIntercept(KeyRoutingHarness.event("ArrowLeft"))
        i.tryIntercept(KeyRoutingHarness.event("ArrowLeft"))

        assertEquals(2, calls)
    }

    @Test
    fun `a child interceptor falls back to its parent only when nothing in the child consumes`() {
        val order = mutableListOf<String>()
        val parent = KeyboardInterceptor()
        parent.register(priority = 0) { order += "parent"; true }
        val child = KeyboardInterceptor(parent)
        child.register(priority = 0) { order += "child"; false }

        assertTrue(child.tryIntercept(KeyRoutingHarness.event("Escape")))

        assertEquals(listOf("child", "parent"), order)
    }

    @Test
    fun `a consuming child never reaches the parent`() {
        val order = mutableListOf<String>()
        val parent = KeyboardInterceptor()
        parent.register(priority = 0) { order += "parent"; true }
        val child = KeyboardInterceptor(parent)
        child.register(priority = 0) { order += "child"; true }

        child.tryIntercept(KeyRoutingHarness.event("Escape"))

        assertEquals(listOf("child"), order)
    }

    @Test
    fun `negative priorities are ordered below zero, not treated as unset`() {
        // The back binding lives at -100. If negatives were clamped or ignored it would tie with
        // the widget tier and be decided by composition order.
        val order = mutableListOf<String>()
        val i = KeyboardInterceptor()
        i.register(priority = -100) { order += "back"; false }
        i.register(priority = -1) { order += "focus-gated"; false }
        i.register(priority = 0) { order += "always-on"; false }

        i.tryIntercept(KeyRoutingHarness.event("ArrowLeft"))

        assertEquals(listOf("always-on", "focus-gated", "back"), order)
    }

    @Test
    fun `a KeyBindings layer consumes a matched key even when its action does nothing`() {
        // The property that makes "withhold the binding" the only safe way to yield a key.
        val h = KeyRoutingHarness()
        h.bindings("no-op", priority = 0) { on(Key.ArrowLeft, "nothing") { } }

        assertTrue(h.press("ArrowLeft"), "a matched binding always reports consumed")
    }

    @Test
    fun `a KeyBindings layer declines keys it did not bind`() {
        val h = KeyRoutingHarness()
        h.bindings("only-escape", priority = 0) { on(Key.Escape, "back") { } }

        assertFalse(h.press("ArrowLeft"))
    }
}
