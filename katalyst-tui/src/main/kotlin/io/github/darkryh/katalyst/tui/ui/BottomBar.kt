package io.github.darkryh.katalyst.tui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
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
import io.github.darkryh.dispatch.widget.Surface
import io.github.darkryh.dispatch.widget.SurfaceStyle
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.TextField
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
fun BottomBar(state: InspectorUiState, theme: DispatchTheme) {
    val scope = requireDispatchScope()
    var value by remember { mutableStateOf("") }

    // Toggling focus always starts from a clean field; the command list needs no prefilled
    // trigger because a focused prompt is always in command mode (see inputValue below).
    LaunchedEffect(PaletteState.active) {
        value = ""
    }

    fun run(command: String) {
        PaletteState.active = false
        when (command) {
            "exit" -> {
                EmbeddedTuiSession.detachRequested = true
                scope.exit(0)
            }
            "shutdown" -> {
                EmbeddedTuiSession.detachRequested = false
                scope.exit(0)
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

private val COMMANDS = listOf(
    CommandOption(label = "exit", description = "Close the inspector — the server keeps running", data = "exit"),
    CommandOption(label = "shutdown", description = "Stop the server and quit", data = "shutdown"),
    CommandOption(label = "help", description = "Show what each command does", data = "help"),
)
