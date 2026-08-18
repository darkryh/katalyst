package io.github.darkryh.katalyst.tui.input

import com.github.ajalt.mordant.input.KeyboardEvent
import io.github.darkryh.dispatch.input.keyEvent
import io.github.darkryh.dispatch.runtime.KeyBindingsScope
import io.github.darkryh.dispatch.runtime.KeyboardInterceptor
import io.github.darkryh.dispatch.widget.SelectorKeyBindings
import io.github.darkryh.dispatch.widget.handleSelectorKeyEvent
// The REAL production priority, not a copy: a change to Main.kt is meant to break these tests.
import io.github.darkryh.katalyst.tui.ARROW_BACK_PRIORITY

/**
 * Headless harness for the inspector's keyboard model.
 *
 * There is no Dispatch test artifact — `dispatch-*` ships no `-test`/`-testing` module. There does
 * not need to be one: the pieces that decide *which* handler sees a key are plain Kotlin with
 * public constructors and no Compose dependency, and the `@Composable KeyBindings(...)` shim does
 * nothing more than `KeyBindingsScope().apply(block)` followed by
 * `interceptor.register(priority) { scope.handle(it) }`. Wiring those two calls here is therefore
 * not a simulation of production — it executes the identical code path, minus the renderer.
 *
 * What this harness deliberately does NOT cover: frame layout, ANSI emission, and anything reading
 * `requireDispatchScope()`. Those need a real TTY and are out of scope for CI by design.
 */
internal class KeyRoutingHarness {

    val interceptor = KeyboardInterceptor()

    /** Labels of the layers that consumed a key, in dispatch order, across the whole run. */
    val consumed = mutableListOf<String>()

    private val disposers = mutableListOf<() -> Unit>()

    /**
     * Register a `KeyBindings`-equivalent layer.
     *
     * [enabled] models conditional composition: `KeyBindings(enabled = false)` registers no
     * interceptor at all (it returns before `register`), which is why the inspector can *withhold*
     * a binding on a screen rather than registering one that no-ops. A guarded no-op would still
     * consume the key — `KeyBindingsScope.handle` returns `true` on any match, regardless of what
     * the action does.
     */
    fun bindings(
        label: String,
        priority: Int = 0,
        enabled: Boolean = true,
        block: KeyBindingsScope.() -> Unit,
    ): () -> Unit {
        if (!enabled) return {}
        val scope = KeyBindingsScope().apply(block)
        val dispose = interceptor.register(priority) { event ->
            scope.handle(event).also { if (it) consumed += label }
        }
        disposers += dispose
        return dispose
    }

    /**
     * Register the real `FilterableTable` key handler. The widget registers with the no-priority
     * overload (priority 0) and performs NO focus check — it is live whenever it is mounted.
     */
    fun filterableTable(label: String, bindings: SelectorKeyBindings): () -> Unit {
        val dispose = interceptor.register(0) { event ->
            handleSelectorKeyEvent(event, bindings).also { if (it) consumed += label }
        }
        disposers += dispose
        return dispose
    }

    /**
     * Dispatch a key and report whether anything consumed it.
     *
     * A fresh [KeyboardEvent] is built per call on purpose. `KeyboardInterceptor.tryIntercept`
     * short-circuits on reference identity (`event === lastEvent`) and replays the cached verdict
     * without running a single handler, so reusing one event object across dispatches would make a
     * test pass while proving nothing.
     */
    fun press(
        key: String,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
    ): Boolean = interceptor.tryIntercept(event(key, ctrl, alt, shift))

    fun disposeAll() {
        disposers.forEach { it() }
        disposers.clear()
    }

    companion object {
        /**
         * Build a raw event. Key names are the W3C spellings and are matched exactly — `"ArrowLeft"`,
         * not `"Left"`; `"Escape"`, not `"Esc"`. An unrecognized multi-character name silently
         * becomes `Key.Named.Unknown`, which is why [KeyMappingContractTest] pins the vocabulary.
         */
        fun event(
            key: String,
            ctrl: Boolean = false,
            alt: Boolean = false,
            shift: Boolean = false,
        ): KeyboardEvent = keyEvent(key, ctrl = ctrl, alt = alt, shift = shift).raw
    }
}

/** Priority of the inspector's Escape binding (`Main.kt`). */
internal const val ESCAPE_PRIORITY = 10

/** Priority Dispatch's always-on widgets register at (`FilterableTable`, `Tree`, `CommandPalette`). */
internal const val ALWAYS_ON_WIDGET_PRIORITY = 0

/** Priority Dispatch's focus-gated widgets register at (`TextField`, `Button`, `SelectMenu`). */
internal const val FOCUS_GATED_WIDGET_PRIORITY = -1

/** A `SelectorKeyBindings` that records which callback fired, for table-contract assertions. */
internal fun recordingSelector(log: MutableList<String>): SelectorKeyBindings =
    SelectorKeyBindings(
        onMoveUp = { log += "up" },
        onMoveDown = { log += "down" },
        onConfirm = { log += "confirm"; true },
        onCancel = { log += "cancel"; true },
        onBackspace = { log += "backspace"; true },
        onCharacter = { c -> log += "char:$c"; true },
    )
