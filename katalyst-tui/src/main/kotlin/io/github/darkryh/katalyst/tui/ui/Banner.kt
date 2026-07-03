package io.github.darkryh.katalyst.tui.ui

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Arrangement
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.runtime.requireDispatchScope
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.Text

/**
 * The Katalyst banner, rendered by the TUI on every screen. Mirrors the framework's console banner
 * (printKatalystBanner), which defers to the TUI via `katalyst.console.banner=false` when the
 * inspector owns the terminal.
 *
 * CRITICAL invariant behind [minRowsForFullArt]: Dispatch renders inline with absolute cursor
 * rows — any frame taller than the terminal cannot be addressed and paints corrupted (missing
 * rows). Every screen must budget its total lines under `terminalHeight`, so short terminals get
 * a one-line banner instead of the 6-row art.
 */
@Composable
fun KatalystBanner(theme: DispatchTheme, minRowsForFullArt: Int = DASHBOARD_FULL_BANNER_MIN_ROWS) {
    if (requireDispatchScope().terminalHeight >= minRowsForFullArt) {
        Column(verticalArrangement = Arrangement.spacedBy(0)) {
            KATALYST_BANNER.forEach { line -> Text(line, style = theme.primary) }
        }
    } else {
        Text("▌K A T A L Y S T ▐", style = theme.primary)
    }
}

/** Threshold used by the dashboard frame; [bannerRows] must stay in lockstep with [KatalystBanner]. */
const val DASHBOARD_FULL_BANNER_MIN_ROWS: Int = 34

/** How many rows the banner occupies at [terminalHeight] — for sibling height budgeting. */
fun bannerRows(terminalHeight: Int, minRowsForFullArt: Int = DASHBOARD_FULL_BANNER_MIN_ROWS): Int =
    if (terminalHeight >= minRowsForFullArt) KATALYST_BANNER.size else 1

private val KATALYST_BANNER = listOf(
    "██╗  ██╗  █████╗  ████████╗  █████╗  ██╗   ██╗   ██╗ ███████╗ ████████╗",
    "██║ ██╔╝ ██╔══██╗ ╚══██╔══╝ ██╔══██╗ ██║   ╚██╗ ██╔╝ ██╔════╝ ╚══██╔══╝",
    "█████╔╝  ███████║    ██║    ███████║ ██║    ╚████╔╝  ███████╗    ██║   ",
    "██╔═██╗  ██╔══██║    ██║    ██╔══██║ ██║     ╚██╔╝   ╚════██║    ██║   ",
    "██║  ██╗ ██║  ██║    ██║    ██║  ██║ ███████╗ ██║    ███████║    ██║   ",
    "╚═╝  ╚═╝ ╚═╝  ╚═╝    ╚═╝    ╚═╝  ╚═╝ ╚══════╝ ╚═╝    ╚══════╝    ╚═╝   ",
)
