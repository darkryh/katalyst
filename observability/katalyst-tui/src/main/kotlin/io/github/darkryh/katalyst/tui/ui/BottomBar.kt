package io.github.darkryh.katalyst.tui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle
import io.github.darkryh.dispatch.layout.Arrangement
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.weight
import io.github.darkryh.dispatch.runtime.LocalHibernation
import io.github.darkryh.dispatch.runtime.requireDispatchScope
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.BasicTextFieldRenderer
import io.github.darkryh.dispatch.widget.CommandOption
import io.github.darkryh.dispatch.widget.CommandPalette
import io.github.darkryh.dispatch.widget.CommandPaletteTextStyles
import io.github.darkryh.dispatch.widget.Surface
import io.github.darkryh.dispatch.widget.SurfaceStyle
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.TextField
import io.github.darkryh.katalyst.telemetry.model.RunDescriptor
import io.github.darkryh.katalyst.tui.attach.ShutdownCoordinator
import io.github.darkryh.katalyst.tui.attach.ShutdownRequestOutcome
import io.github.darkryh.katalyst.tui.attach.ShutdownResult
import io.github.darkryh.katalyst.tui.embedded.EmbeddedTuiSession
import io.github.darkryh.katalyst.tui.viewmodel.InspectorUiState

/**
 * Focus state of the footer prompt. Tab (or `/`) moves the keyboard here; Tab or Esc returns it to
 * the menu. Screens that bind Enter/arrows must UNREGISTER those bindings while [active] — a
 * guarded no-op binding still consumes the key before the prompt sees it.
 */
object PaletteState {
    var active by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
}

/**
 * The persistent footer: a Claude-Code-style prompt (always visible, its own surface) with
 * Dispatch's [CommandPalette] popping under it while the input starts with `/` — arrows choose,
 * Enter runs. Below, one hint line with the compact JVM heap gauge pinned right.
 *
 * Commands: `exit` closes the inspector but keeps the backend running (console logging restored);
 * `shutdown` stops the backend and quits; `help` explains both inline.
 */
@Composable
fun BottomBar(
    state: InspectorUiState,
    theme: DispatchTheme,
    shutdownCoordinator: ShutdownCoordinator,
) {
    val scope = requireDispatchScope()
    var value by remember { mutableStateOf("") }

    // Toggling focus always starts from a clean field; the command list needs no prefilled
    // trigger because a focused prompt is always in command mode (see inputValue below).
    LaunchedEffect(PaletteState.active) {
        value = ""
    }

    /**
     * `/shutdown` outside the backend's own process: ask it to stop, watch until it has, and only
     * then quit.
     *
     * Embedded, this is not needed — the inspector IS the backend's console, so `scope.exit(0)` ends
     * the process and with it the server. Standalone the two are separate processes, and quitting
     * the inspector used to be all that happened while the menu promised otherwise.
     *
     * Runs on the Dispatch background scope because it waits: the shutdown takes as long as the
     * backend's own drain, and the UI has to keep painting the progress line while it does.
     */
    fun requestBackendShutdown(descriptor: RunDescriptor?) {
        PaletteState.message = when (descriptor) {
            null -> NO_BACKEND_MESSAGE
            else -> "stopping ${descriptor.appName} (pid ${descriptor.pid})…"
        }
        scope.launch {
            when (val result = shutdownCoordinator.shutdown(descriptor)) {
                // Confirmed gone. Quitting now is the honest end of "stop the server and quit".
                // detachRequested is left alone: it only means anything to the embedded feature,
                // which by definition is not running in this branch.
                ShutdownResult.Stopped -> scope.exit(0)
                ShutdownResult.NoBackend -> PaletteState.message = NO_BACKEND_MESSAGE
                is ShutdownResult.StillRunning -> PaletteState.message =
                    "${result.descriptor.appName} (pid ${result.descriptor.pid}) took the request but " +
                        "is still answering — the inspector stays open so you can watch it"
                is ShutdownResult.Refused -> PaletteState.message = refusalMessage(result)
            }
        }
    }

    fun run(command: String) {
        PaletteState.active = false
        when (command) {
            "exit" -> {
                EmbeddedTuiSession.detachRequested = true
                scope.exit(0)
            }
            "shutdown" ->
                if (EmbeddedTuiSession.embedded) {
                    // One process: quitting the inspector runs the JVM's shutdown and stops the
                    // server with it. Asking over the loopback socket would be the same process
                    // asking itself.
                    EmbeddedTuiSession.detachRequested = false
                    scope.exit(0)
                } else {
                    requestBackendShutdown(state.selected)
                }
            "help", "" -> PaletteState.message =
                "/exit — close the inspector, keep the server running   " +
                    "/shutdown — stop the server and quit"
            else -> PaletteState.message =
                "unknown command \"/$command\" — try /exit, /shutdown, or /help"
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0)) {
        Surface(style = SurfaceStyle.fill(rgb("#303846"))) {
            if (PaletteState.active) {
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    icon = "› ",
                    placeholder = "type a command…",
                    onSubmit = { raw -> run(raw.trim().removePrefix("/").lowercase()) },
                )
            } else {
                // Display-only twin of the field: identical look, zero key handling, so the menu
                // keeps the keyboard until Tab or `/` hands it over.
                BasicTextFieldRenderer(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    icon = "› ",
                    placeholder = "Tab or / for commands",
                    enabled = false,
                    showCursor = false,
                )
            }
        }

        if (PaletteState.active) {
            CommandPalette(
                options = COMMANDS,
                // A focused prompt is ALWAYS in command mode: an empty field shows the full list,
                // and plain typed text ("ex") filters it exactly like "/ex" — the widget itself
                // only triggers on a leading '/', so it is prepended here.
                inputValue = "/" + value.removePrefix("/"),
                onOptionSelected = { option -> run(option.data) },
                onInputTransform = { value = it },
                modifier = Modifier.fillMaxWidth(),
                selectionIndicator = SELECTION_INDICATOR,
                textStyles = COMMAND_STYLES,
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            val message = PaletteState.message
            val hibernating = LocalHibernation.current?.isHibernating == true
            when {
                hibernating -> Text(
                    HIBERNATION_HINT,
                    style = theme.warning,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                PaletteState.active -> Text(
                    "↑↓ choose · Enter run · Tab/Esc back to menu",
                    style = theme.muted,
                    modifier = Modifier.weight(1f),
                )
                message != null -> Text(message, style = theme.warning, modifier = Modifier.weight(1f))
                else -> Text(
                    "Tab or / commands · ↑↓←→ move · Enter open · Esc back · Ctrl+C quit",
                    style = theme.muted,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(heapGauge(state), style = theme.muted)
        }
    }
}

/**
 * Why a refusal is spelled out rather than reduced to "failed": each of these needs a different
 * thing done about it, and the user is the only one who can do it.
 */
private fun refusalMessage(refused: ShutdownResult.Refused): String {
    val target = "${refused.descriptor.appName} (pid ${refused.descriptor.pid})"
    return when (refused.outcome) {
        ShutdownRequestOutcome.Disabled ->
            "$target has remote shutdown turned off " +
                "(-Dkatalyst.telemetry.shutdownControl=false) — stop it where it runs"
        ShutdownRequestOutcome.Unsupported ->
            "$target does not accept a shutdown request — it may predate the endpoint, or run " +
                "without the katalystApplication entry point"
        ShutdownRequestOutcome.Unauthorized ->
            "$target rejected the token — its discovery descriptor is stale; restart the inspector"
        ShutdownRequestOutcome.Unreachable ->
            "$target did not answer — it may already be gone, or its telemetry port has closed"
        ShutdownRequestOutcome.Accepted ->
            // Unreachable by construction: an accepted request is not a refusal. Kept total rather
            // than thrown, so a future outcome can never take the footer down with it.
            "$target accepted the shutdown request"
    }
}

private const val NO_BACKEND_MESSAGE =
    "no backend attached — nothing to stop. Use /exit to close the inspector"

private val COMMANDS = listOf(
    CommandOption(label = "exit", description = "Close the inspector — the server keeps running", data = "exit"),
    CommandOption(label = "shutdown", description = "Stop the server and quit", data = "shutdown"),
    CommandOption(label = "help", description = "Show what each command does", data = "help"),
)

/**
 * Marks the row Enter will run. Two columns wide, and the widget reserves the same two columns on
 * every other row, so turning selection on and off never shifts the list sideways.
 */
private const val SELECTION_INDICATOR = "❯ "

/**
 * Three commands of nearly equal length, one keystroke apart, all reachable by Enter: the palette
 * has to answer "which one am I about to run?" at a glance, and the widget's plain-white defaults
 * did not — every label rendered identically, leaving only two adjacent greys on the description
 * column to carry the selection.
 *
 * So selection is stated three times over, each redundant with the others:
 *  - the `❯` indicator, which works with no colour at all (a piped terminal, a monochrome profile);
 *  - a full-width blue bar, keyed to the same family as the prompt surface directly above it so it
 *    reads as part of the footer chrome rather than a stray highlight;
 *  - the label itself, white and bold on the selected row against a muted grey on the rest, so the
 *    contrast direction matches everything else in the inspector (bright = live, grey = context).
 *
 * The unselected rows are deliberately dimmed rather than the selected one merely brightened: with
 * one row per command and no other content nearby, dimming is what makes the chosen row pop without
 * having to shout.
 */
private val COMMAND_STYLES = CommandPaletteTextStyles(
    prefix = rgb("#6E7681"),
    selectedPrefix = rgb("#58A6FF") + TextStyle(bold = true),
    label = rgb("#8B949E"),
    selectedLabel = rgb("#FFFFFF") + TextStyle(bold = true),
    description = rgb("#6E7681"),
    selectedDescription = rgb("#C9D1D9"),
    disabledLabel = rgb("#484F58"),
    noResultsText = rgb("#8B949E"),
    selectedRowFill = rgb("#1F3A5F"),
)
