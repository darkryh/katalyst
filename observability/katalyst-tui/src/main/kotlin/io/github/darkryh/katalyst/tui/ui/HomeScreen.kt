package io.github.darkryh.katalyst.tui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.darkryh.dispatch.input.Key
import io.github.darkryh.dispatch.layout.Alignment
import io.github.darkryh.dispatch.layout.Arrangement
import io.github.darkryh.dispatch.layout.Box
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.BorderStyle
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.modifier.width
import io.github.darkryh.dispatch.navigation.LocalNavigator
import io.github.darkryh.dispatch.navigation.NavKey
import io.github.darkryh.dispatch.runtime.KeyBindings
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.runtime.requireDispatchScope
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.Panel
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.katalyst.tui.navigation.BootRoute
import io.github.darkryh.katalyst.tui.navigation.ConfigRoute
import io.github.darkryh.katalyst.tui.navigation.EventsRoute
import io.github.darkryh.katalyst.tui.navigation.HttpRoute
import io.github.darkryh.katalyst.tui.navigation.MigrationsRoute
import io.github.darkryh.katalyst.tui.navigation.PersistenceRoute
import io.github.darkryh.katalyst.tui.navigation.SchedulerRoute
import io.github.darkryh.katalyst.tui.navigation.TransactionsRoute
import io.github.darkryh.katalyst.tui.navigation.WebSocketsRoute
import io.github.darkryh.katalyst.tui.navigation.WiringRoute

/** One tile on the home grid: a subsystem entry that opens [route] on Enter. */
private data class TuiTile(val title: String, val description: String, val route: NavKey)

/**
 * The 10 subsystem tiles, in reading order. Copy rules: the title is the subsystem's plain name;
 * the description says exactly what you will see inside, in full words — no abbreviations, no
 * implementation names (engines are abstractions).
 */
private val homeTiles: List<TuiTile> = listOf(
    TuiTile("Boot", "Every startup phase with its duration and warnings", BootRoute),
    TuiTile("Wiring", "Registered beans and the dependency graph", WiringRoute),
    TuiTile("HTTP", "Routes with request rates and latency percentiles", HttpRoute),
    TuiTile("Scheduler", "Scheduled jobs, run history, and next fire times", SchedulerRoute),
    TuiTile("Persistence", "Connection pool usage and query activity", PersistenceRoute),
    TuiTile("Transactions", "Commits, rollbacks, and transaction latency", TransactionsRoute),
    TuiTile("Migrations", "Applied and pending schema migrations", MigrationsRoute),
    TuiTile("Events", "Event bus traffic, handlers, and dead events", EventsRoute),
    TuiTile("WebSockets", "Active sessions and message frame traffic", WebSocketsRoute),
    TuiTile("Config", "Resolved configuration keys and their sources", ConfigRoute),
)

/** Every tile is exactly this size — the grid owes its calm to the uniformity. */
private const val TILE_WIDTH = 38
private const val FULL_TILE_HEIGHT = 5
private const val TILE_INNER = TILE_WIDTH - 4
private const val TILE_GAP = 2

/**
 * The landing grid. Identical fixed-size tiles (title + description, both centered), rows centered
 * in the terminal, column count adapted to the terminal width so wide terminals don't scatter the
 * tiles. Arrows move the selection, Enter opens — unless the command palette owns the keyboard.
 */
@Composable
fun HomeScreen() {
    val theme = LocalTheme.current
    val navigator = LocalNavigator.current
    val scope = requireDispatchScope()
    var selected by HomeSelection::index

    val columns = ((scope.terminalWidth + TILE_GAP) / (TILE_WIDTH + TILE_GAP)).coerceIn(1, 5)

    // HARD frame budget: Dispatch renders inline with absolute cursor rows — a frame taller than
    // the terminal paints corrupted (rows go missing). Fixed rows around the grid: banner +
    // chrome(2) + dividers(2) + prompt(1) + hints(1) + command list when open(3). The grid
    // degrades to fit: first the row gap goes, then tiles shrink 5 → 4 (one description line)
    // → 3 (title only).
    val gridRows = (homeTiles.size + columns - 1) / columns
    val budget = scope.terminalHeight - bannerRows(scope.terminalHeight) - 9
    val rowGap = if (gridRows * FULL_TILE_HEIGHT + (gridRows - 1) <= budget) 1 else 0
    val tileHeight = listOf(FULL_TILE_HEIGHT, 4, 3)
        .firstOrNull { gridRows * it + (gridRows - 1) * rowGap <= budget }
        ?: 3

    // KeyBindings consume their keys even when the handler no-ops, so while the palette owns the
    // keyboard these must be UNREGISTERED (conditional composition), not merely guarded — a
    // guarded Enter binding would still swallow the palette's submit.
    if (!PaletteState.active) {
        KeyBindings {
            on(Key.ArrowRight, "next") { selected = (selected + 1).coerceIn(0, homeTiles.lastIndex) }
            on(Key.ArrowLeft, "prev") { selected = (selected - 1).coerceIn(0, homeTiles.lastIndex) }
            on(Key.ArrowDown, "down") { selected = (selected + columns).coerceAtMost(homeTiles.lastIndex) }
            on(Key.ArrowUp, "up") { if (selected - columns >= 0) selected -= columns }
            on(Key.Enter, "open") { navigator.navigate(homeTiles[selected].route) }
        }
    }

    // spacedBy measures correctly since dispatch 1.0.0-beta02 (darkryh/dispatch#2).
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rowGap)) {
        homeTiles.chunked(columns).forEachIndexed { rowIndex, rowTiles ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    rowTiles.forEachIndexed { columnIndex, tile ->
                        Tile(tile, isSelected = selected == rowIndex * columns + columnIndex, tileHeight, theme)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(tile: TuiTile, isSelected: Boolean, tileHeight: Int, theme: DispatchTheme) {
    Panel(
        borderStyle = if (isSelected) BorderStyle.Double else BorderStyle.Rounded,
        modifier = Modifier.width(TILE_WIDTH).height(tileHeight),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0)) {
            Text(tile.title.centeredIn(TILE_INNER), style = if (isSelected) theme.accent else theme.primary)
            val lines = wrapToTwoLines(tile.description, TILE_INNER)
            when (tileHeight) {
                FULL_TILE_HEIGHT -> {
                    Text(lines.first().centeredIn(TILE_INNER), style = theme.muted)
                    Text((lines.getOrNull(1) ?: "").centeredIn(TILE_INNER), style = theme.muted)
                }
                4 -> {
                    val single = if (lines.size > 1) lines.first().dropLast(1) + "…" else lines.first()
                    Text(single.centeredIn(TILE_INNER), style = theme.muted)
                }
                // Height 3: the border + centered title only — every row must fit the budget.
            }
        }
    }
}

/**
 * The dashboard's selected tile, hoisted OUT of composition: `remember` state dies when Home
 * leaves composition on push, and popping back must land on the tile the user left from.
 * Process-wide is correct here — one terminal, one dashboard.
 */
private object HomeSelection {
    var index by mutableStateOf(0)
}

/** Pad-center [this] inside a [width]-cell line (clipping if longer). */
private fun String.centeredIn(width: Int): String {
    val text = take(width)
    val left = (width - text.length) / 2
    return " ".repeat(left) + text + " ".repeat(width - text.length - left)
}

/**
 * Greedy word-wrap into at most two lines of [width] cells, preserving word order: once a word
 * spills to the second line every following word stays there; overflow past line two is dropped.
 */
private fun wrapToTwoLines(text: String, width: Int): List<String> {
    val first = StringBuilder()
    val second = StringBuilder()
    var spilled = false
    for (word in text.split(' ')) {
        if (!spilled && (first.isEmpty() || first.length + 1 + word.length <= width)) {
            if (first.isNotEmpty()) first.append(' ')
            first.append(word)
        } else {
            spilled = true
            when {
                second.isEmpty() -> second.append(word)
                second.length + 1 + word.length <= width -> second.append(' ').append(word)
                else -> break
            }
        }
    }
    return if (second.isEmpty()) listOf(first.toString()) else listOf(first.toString(), second.toString())
}
