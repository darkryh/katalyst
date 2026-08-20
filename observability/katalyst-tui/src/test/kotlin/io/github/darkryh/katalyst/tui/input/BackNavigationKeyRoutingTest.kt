package io.github.darkryh.katalyst.tui.input

import io.github.darkryh.dispatch.input.Key
// The REAL production constant from Main.kt — changing it there must break these tests.
import io.github.darkryh.katalyst.tui.ARROW_BACK_PRIORITY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The contract for going back in the inspector: **Escape and ArrowLeft both pop, except on Home,
 * where ArrowLeft belongs to the tile grid.**
 *
 * These tests reconstruct the exact interceptor layering `Main.kt` and `HomeScreen.kt` build, then
 * drive real key events through it and assert *which layer consumed each key*. That distinction is
 * the whole point: a binding that fires is easy to test, but the defect this feature can introduce
 * is a binding that fires when it should have stood aside and let a lower layer have the key.
 *
 * The failure mode being guarded is specific and silent. `KeyBindingsScope.handle` returns `true`
 * on any match — so a binding registered at a priority above the tile grid would swallow ArrowLeft
 * on Home and the grid would simply stop moving, with no error and no log. That is why the
 * inspector *withholds* the binding on Home (`enabled = !onHome`) instead of guarding its body.
 */
class BackNavigationKeyRoutingTest {

    /** What the inspector registers while the dashboard (Home) is showing. */
    private fun KeyRoutingHarness.layerHome(
        popped: MutableList<String>,
        tileMoves: MutableList<String>,
        paletteActive: Boolean = false,
    ) {
        bindings("escape-back", ESCAPE_PRIORITY) {
            on(Key.Escape, "back") { popped += "escape" }
        }
        // Main.kt registers the ArrowLeft binding with enabled = !onHome. On Home that is `false`,
        // so NOTHING is registered and the key falls straight through to the tile grid.
        bindings("arrowleft-back", ARROW_BACK_PRIORITY, enabled = false) {
            on(Key.ArrowLeft, "back") { popped += "arrowleft" }
        }
        bindings("home-tiles", ALWAYS_ON_WIDGET_PRIORITY, enabled = !paletteActive) {
            on(Key.ArrowLeft, "prev") { tileMoves += "prev" }
            on(Key.ArrowRight, "next") { tileMoves += "next" }
            on(Key.ArrowUp, "up") { tileMoves += "up" }
            on(Key.ArrowDown, "down") { tileMoves += "down" }
            on(Key.Enter, "open") { tileMoves += "open" }
        }
    }

    /** What the inspector registers on a subsystem screen that shows a FilterableTable. */
    private fun KeyRoutingHarness.layerSubsystemScreen(
        popped: MutableList<String>,
        tableLog: MutableList<String>,
        withTable: Boolean = true,
    ) {
        bindings("escape-back", ESCAPE_PRIORITY) {
            on(Key.Escape, "back") { popped += "escape" }
        }
        bindings("arrowleft-back", ARROW_BACK_PRIORITY, enabled = true) {
            on(Key.ArrowLeft, "back") { popped += "arrowleft" }
        }
        if (withTable) filterableTable("table", recordingSelector(tableLog))
    }

    /* ── Home: the tile grid keeps ArrowLeft ─────────────────────────────────────────────── */

    @Test
    fun `on Home ArrowLeft moves between tiles and never navigates back`() {
        val h = KeyRoutingHarness()
        val popped = mutableListOf<String>()
        val tiles = mutableListOf<String>()
        h.layerHome(popped, tiles)

        assertTrue(h.press("ArrowLeft"), "ArrowLeft must be consumed on Home")

        assertEquals(listOf("prev"), tiles, "the tile grid must receive ArrowLeft")
        assertTrue(popped.isEmpty(), "ArrowLeft must not pop the back stack on Home, got $popped")
        assertEquals(listOf("home-tiles"), h.consumed)
    }

    @Test
    fun `on Home every grid direction still reaches the tiles`() {
        val h = KeyRoutingHarness()
        val popped = mutableListOf<String>()
        val tiles = mutableListOf<String>()
        h.layerHome(popped, tiles)

        listOf("ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "Enter").forEach { h.press(it) }

        assertEquals(listOf("prev", "next", "up", "down", "open"), tiles)
        assertTrue(popped.isEmpty(), "no grid key may pop the stack, got $popped")
    }

    @Test
    fun `on Home Escape still reaches the back binding`() {
        val h = KeyRoutingHarness()
        val popped = mutableListOf<String>()
        val tiles = mutableListOf<String>()
        h.layerHome(popped, tiles)

        assertTrue(h.press("Escape"))

        assertEquals(listOf("escape"), popped)
        assertTrue(tiles.isEmpty())
    }

    /* ── Subsystem screens: ArrowLeft goes back ──────────────────────────────────────────── */

    @Test
    fun `on a subsystem screen ArrowLeft navigates back`() {
        val h = KeyRoutingHarness()
        val popped = mutableListOf<String>()
        val table = mutableListOf<String>()
        h.layerSubsystemScreen(popped, table)

        assertTrue(h.press("ArrowLeft"), "ArrowLeft must be consumed off Home")

        assertEquals(listOf("arrowleft"), popped)
        assertTrue(table.isEmpty(), "the table must not see ArrowLeft, got $table")
        assertEquals(listOf("arrowleft-back"), h.consumed)
    }

    @Test
    fun `on a subsystem screen Escape and ArrowLeft both pop exactly once`() {
        val h = KeyRoutingHarness()
        val popped = mutableListOf<String>()
        h.layerSubsystemScreen(popped, mutableListOf())

        h.press("Escape")
        h.press("ArrowLeft")

        assertEquals(listOf("escape", "arrowleft"), popped, "both keys go back, neither twice")
    }

    @Test
    fun `a screen with no table still goes back with ArrowLeft`() {
        // Persistence, Transactions and every SectionMissing empty state mount no FilterableTable,
        // so they depend entirely on the global bindings.
        val h = KeyRoutingHarness()
        val popped = mutableListOf<String>()
        h.layerSubsystemScreen(popped, mutableListOf(), withTable = false)

        assertTrue(h.press("ArrowLeft"))
        assertEquals(listOf("arrowleft"), popped)
    }

    /* ── The table keeps every key it actually wants ─────────────────────────────────────── */

    @Test
    fun `table row navigation and confirm are unaffected by the back binding`() {
        val h = KeyRoutingHarness()
        val popped = mutableListOf<String>()
        val table = mutableListOf<String>()
        h.layerSubsystemScreen(popped, table)

        h.press("ArrowUp")
        h.press("ArrowDown")
        h.press("Enter")

        assertEquals(listOf("up", "down", "confirm"), table)
        assertTrue(popped.isEmpty(), "row keys must never pop, got $popped")
    }

    @Test
    fun `ArrowLeft is not swallowed as filter text while a filter is active`() {
        // The filter is an append-only buffer with no cursor: FilterableTable exposes only
        // appendFilter/removeLastFilter. ArrowLeft is a Key.Named, so `isText` is false and it can
        // never reach onCharacter — there is no cursor for it to move and no text for it to join.
        val h = KeyRoutingHarness()
        val popped = mutableListOf<String>()
        val table = mutableListOf<String>()
        h.layerSubsystemScreen(popped, table)

        h.press("d")
        h.press("b")
        h.press("ArrowLeft")

        assertEquals(listOf("char:d", "char:b"), table, "only real characters filter")
        assertEquals(listOf("arrowleft"), popped, "ArrowLeft still goes back mid-filter")
    }

    @Test
    fun `Backspace still edits the filter rather than navigating`() {
        val h = KeyRoutingHarness()
        val popped = mutableListOf<String>()
        val table = mutableListOf<String>()
        h.layerSubsystemScreen(popped, table)

        h.press("Backspace")

        assertEquals(listOf("backspace"), table)
        assertTrue(popped.isEmpty())
    }

    /* ── Modifier exactness ──────────────────────────────────────────────────────────────── */

    @Test
    fun `modified ArrowLeft does not navigate back`() {
        // KeyStroke matching for a Key.Named target requires all three modifiers to match exactly,
        // so a terminal reporting Shift+Left or Alt+Left must not trigger a surprise navigation.
        val h = KeyRoutingHarness()
        val popped = mutableListOf<String>()
        h.layerSubsystemScreen(popped, mutableListOf(), withTable = false)

        assertFalse(h.press("ArrowLeft", shift = true), "Shift+ArrowLeft must not be consumed")
        assertFalse(h.press("ArrowLeft", alt = true), "Alt+ArrowLeft must not be consumed")
        assertFalse(h.press("ArrowLeft", ctrl = true), "Ctrl+ArrowLeft must not be consumed")

        assertTrue(popped.isEmpty(), "no modified ArrowLeft may pop, got $popped")
    }

    /* ── The layering itself ─────────────────────────────────────────────────────────────── */

    @Test
    fun `the back binding sits below every Dispatch widget tier`() {
        // The ordering guarantee the design rests on. A widget that wants ArrowLeft — now or in a
        // future Dispatch release — consumes it first, and back navigation degrades to Esc-only
        // rather than fighting the widget.
        assertTrue(
            ARROW_BACK_PRIORITY < FOCUS_GATED_WIDGET_PRIORITY,
            "back must sit below focus-gated widgets ($FOCUS_GATED_WIDGET_PRIORITY)",
        )
        assertTrue(
            ARROW_BACK_PRIORITY < ALWAYS_ON_WIDGET_PRIORITY,
            "back must sit below always-on widgets ($ALWAYS_ON_WIDGET_PRIORITY)",
        )
    }

    @Test
    fun `a focus-gated widget wins ArrowLeft over the back binding`() {
        // A focused TextField moves its cursor with ArrowLeft. Because the back binding is below
        // the focus-gated tier, the cursor wins and the stack is untouched.
        val h = KeyRoutingHarness()
        val popped = mutableListOf<String>()
        val cursor = mutableListOf<String>()
        h.bindings("arrowleft-back", ARROW_BACK_PRIORITY) {
            on(Key.ArrowLeft, "back") { popped += "arrowleft" }
        }
        h.bindings("textfield-cursor", FOCUS_GATED_WIDGET_PRIORITY) {
            on(Key.ArrowLeft, "cursor-left") { cursor += "left" }
        }

        assertTrue(h.press("ArrowLeft"))

        assertEquals(listOf("left"), cursor)
        assertTrue(popped.isEmpty(), "a focused text cursor must beat back navigation, got $popped")
        assertEquals(listOf("textfield-cursor"), h.consumed)
    }

    @Test
    fun `withholding the binding is what protects Home, not guarding its body`() {
        // Pin the mechanism, not just the outcome. Registering the binding on Home — even with a
        // body that does nothing — consumes the key and freezes the tile grid. This test fails if
        // anyone converts `enabled = !onHome` into an `if` inside the action.
        val h = KeyRoutingHarness()
        val tiles = mutableListOf<String>()
        h.bindings("arrowleft-back-guarded", ESCAPE_PRIORITY) {
            on(Key.ArrowLeft, "back") { /* guarded no-op, as if `if (!onHome)` lived here */ }
        }
        h.bindings("home-tiles", ALWAYS_ON_WIDGET_PRIORITY) {
            on(Key.ArrowLeft, "prev") { tiles += "prev" }
        }

        assertTrue(h.press("ArrowLeft"), "the guarded binding still consumes the key")
        assertTrue(
            tiles.isEmpty(),
            "a guarded no-op ABOVE the grid swallows ArrowLeft — this is the bug the design avoids",
        )
    }
}
