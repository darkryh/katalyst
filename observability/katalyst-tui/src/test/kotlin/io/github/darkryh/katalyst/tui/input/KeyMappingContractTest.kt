package io.github.darkryh.katalyst.tui.input

import io.github.darkryh.dispatch.input.Key
import io.github.darkryh.dispatch.input.asKeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The key-name vocabulary, pinned.
 *
 * Key names are matched exactly against the W3C spellings, and anything unrecognized degrades
 * silently to [Key.Named.Unknown] rather than raising. That is a comfortable behaviour at runtime
 * and a dangerous one in a test: a suite written against `"Left"` or `"Esc"` builds an `Unknown`
 * event, matches nothing, asserts "not consumed", and passes — proving the opposite of what it
 * claims. These tests exist so that trap is caught here rather than in a screen suite.
 */
class KeyMappingContractTest {

    private fun keyOf(name: String): Key = KeyRoutingHarness.event(name).asKeyEvent().key

    @Test
    fun `the arrow keys the inspector binds map to their named keys`() {
        assertEquals(Key.ArrowLeft, keyOf("ArrowLeft"))
        assertEquals(Key.ArrowRight, keyOf("ArrowRight"))
        assertEquals(Key.ArrowUp, keyOf("ArrowUp"))
        assertEquals(Key.ArrowDown, keyOf("ArrowDown"))
    }

    @Test
    fun `the other keys the inspector binds map correctly`() {
        assertEquals(Key.Escape, keyOf("Escape"))
        assertEquals(Key.Enter, keyOf("Enter"))
        assertEquals(Key.Tab, keyOf("Tab"))
        assertEquals(Key.Backspace, keyOf("Backspace"))
    }

    @Test
    fun `plausible-looking aliases are NOT recognized`() {
        // Every one of these is a spelling a developer might reach for. All become Unknown.
        listOf("Left", "Right", "Up", "Down", "Esc", "Return", "Space", "Del", "Arrowleft")
            .forEach { alias ->
                assertEquals(
                    Key.Named.Unknown,
                    keyOf(alias),
                    "\"$alias\" must not be mistaken for a real key — use the W3C name",
                )
            }
    }

    @Test
    fun `a single printable character maps to a Char key`() {
        assertEquals(Key.char('d'), keyOf("d"))
        assertEquals(Key.char('7'), keyOf("7"))
        assertEquals(Key.char(' '), keyOf(" "))
    }

    @Test
    fun `a named key exposes no character`() {
        // This is precisely why ArrowLeft can never be mistaken for filter input.
        assertNull(KeyRoutingHarness.event("ArrowLeft").asKeyEvent().char)
        assertNull(KeyRoutingHarness.event("Escape").asKeyEvent().char)
    }

    @Test
    fun `a printable key exposes its character`() {
        assertEquals('d', KeyRoutingHarness.event("d").asKeyEvent().char)
    }

    @Test
    fun `function keys map through F12 and no further`() {
        (1..12).forEach { n ->
            assertEquals(
                Key.Named.valueOf("F$n"),
                keyOf("F$n"),
                "F$n must map to its named key",
            )
        }
        assertEquals(Key.Named.Unknown, keyOf("F13"))
    }

    @Test
    fun `modifiers are carried through to the event`() {
        val e = KeyRoutingHarness.event("ArrowLeft", ctrl = true, alt = true, shift = true).asKeyEvent()
        assertEquals(Key.ArrowLeft, e.key)
        assertEquals(true, e.ctrl)
        assertEquals(true, e.alt)
        assertEquals(true, e.shift)
    }

    @Test
    fun `a shift-only character counts as text but a control-modified one does not`() {
        assertEquals(true, KeyRoutingHarness.event("S", shift = true).asKeyEvent().isText)
        assertEquals(false, KeyRoutingHarness.event("s", ctrl = true).asKeyEvent().isText)
        assertEquals(false, KeyRoutingHarness.event("s", alt = true).asKeyEvent().isText)
        assertEquals(false, KeyRoutingHarness.event("ArrowLeft").asKeyEvent().isText)
    }
}
