package io.github.darkryh.katalyst.tui.input

import io.github.darkryh.dispatch.widget.handleSelectorKeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What `FilterableTable` does and does not claim from the keyboard.
 *
 * Nine of the inspector's screens mount a `FilterableTable`, and it registers with no focus check —
 * it is live for every key while it is on screen. Back navigation therefore only works if the table
 * genuinely declines ArrowLeft. That is asserted here against the widget's real dispatch function,
 * not against a description of it, so a Dispatch upgrade that starts claiming ArrowLeft fails this
 * suite instead of silently disabling back navigation on those nine screens.
 */
class FilterableTableKeyContractTest {

    private fun dispatch(
        key: String,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        log: MutableList<String> = mutableListOf(),
    ): Pair<Boolean, List<String>> {
        val consumed = handleSelectorKeyEvent(
            KeyRoutingHarness.event(key, ctrl = ctrl, alt = alt, shift = shift),
            recordingSelector(log),
        )
        return consumed to log
    }

    /* ── What the table claims ───────────────────────────────────────────────────────────── */

    @Test
    fun `ArrowUp and ArrowDown move the selection`() {
        assertEquals(true to listOf("up"), dispatch("ArrowUp"))
        assertEquals(true to listOf("down"), dispatch("ArrowDown"))
    }

    @Test
    fun `Enter confirms the highlighted row`() {
        assertEquals(true to listOf("confirm"), dispatch("Enter"))
    }

    @Test
    fun `Escape exits the table`() {
        assertEquals(true to listOf("cancel"), dispatch("Escape"))
    }

    @Test
    fun `Backspace edits the filter`() {
        assertEquals(true to listOf("backspace"), dispatch("Backspace"))
    }

    @Test
    fun `printable characters append to the filter`() {
        assertEquals(true to listOf("char:d"), dispatch("d"))
        assertEquals(true to listOf("char:7"), dispatch("7"))
    }

    /* ── What the table declines — the basis of the feature ──────────────────────────────── */

    @Test
    fun `ArrowLeft is declined, so it can reach the back binding`() {
        val (consumed, log) = dispatch("ArrowLeft")
        assertFalse(consumed, "FilterableTable must not consume ArrowLeft")
        assertTrue(log.isEmpty(), "no table callback may fire for ArrowLeft, got $log")
    }

    @Test
    fun `ArrowRight is declined`() {
        val (consumed, log) = dispatch("ArrowRight")
        assertFalse(consumed)
        assertTrue(log.isEmpty())
    }

    @Test
    fun `ArrowLeft never reaches the filter as text`() {
        // The guard is `isText`, which requires a printable char. ArrowLeft is a Key.Named, so its
        // `char` is null and it cannot be mistaken for filter input.
        val (_, log) = dispatch("ArrowLeft")
        assertTrue(log.none { it.startsWith("char:") }, "ArrowLeft must never filter, got $log")
    }

    @Test
    fun `navigation and paging keys the inspector does not bind are all declined`() {
        listOf("Home", "End", "PageUp", "PageDown", "Insert", "Delete", "F1", "F12").forEach { k ->
            val (consumed, log) = dispatch(k)
            assertFalse(consumed, "$k must be declined by the table")
            assertTrue(log.isEmpty(), "$k must fire no callback, got $log")
        }
    }

    @Test
    fun `a control-modified character is not filter input`() {
        val (consumed, log) = dispatch("s", ctrl = true)
        assertFalse(consumed, "Ctrl+S is not text and must not filter")
        assertTrue(log.isEmpty())
    }

    @Test
    fun `an alt-modified character is not filter input`() {
        val (consumed, log) = dispatch("s", alt = true)
        assertFalse(consumed)
        assertTrue(log.isEmpty())
    }

    @Test
    fun `a shifted character is still text`() {
        // Shift alone produces printable input, so it must keep filtering.
        val (consumed, log) = dispatch("S", shift = true)
        assertTrue(consumed)
        assertEquals(listOf("char:S"), log)
    }
}
