package io.github.darkryh.katalyst.tui.ui.screens

import androidx.compose.runtime.Composable
import com.github.ajalt.mordant.rendering.TextAlign
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.FilterableTable
import io.github.darkryh.dispatch.widget.TableColumn
import io.github.darkryh.dispatch.widget.TableColumnWidth
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.rememberTableState
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.github.darkryh.katalyst.telemetry.model.WebSocketSession
import io.github.darkryh.katalyst.tui.ui.ContextPanel
import io.github.darkryh.katalyst.tui.ui.FieldLine
import io.github.darkryh.katalyst.tui.ui.SubScreen
import io.github.darkryh.katalyst.tui.ui.formatAgo
import io.github.darkryh.katalyst.tui.ui.formatMs
import io.github.darkryh.katalyst.tui.ui.subBodyRows

/**
 * WebSockets, live: configuration ground truth with its two production footguns named in red,
 * then the running truth — every open session with its frame and byte traffic (master table),
 * or the registered socket routes while nothing is connected. Session detail is bounded at the
 * recording site (first 250 concurrent itemized, all counted), and the screen says so when the
 * cap is in play.
 */
@Composable
fun WebSocketsScreen(snapshot: TelemetrySnapshot?, theme: DispatchTheme, onBack: () -> Unit) {
    val ws = snapshot?.webSockets ?: run {
        SectionMissing("WebSockets", "WebSockets are not configured on the attached backend.", theme)
        return
    }
    val now = snapshot.capturedAtEpochMs
    val mismatch = ws.enabledFlag != ws.pluginInstalled

    SubScreen(
        title = "WebSockets",
        tagline = "socket configuration, its footguns, and every session currently open",
        stats = buildList {
            add(if (ws.pluginInstalled) "plugin ✓" else "plugin ✗")
            if (mismatch) add("✗ flag/plugin mismatch")
            add("sessions ${ws.activeSessions}")
            add("opened ${ws.opened}")
            add(if (ws.handlerErrors > 0) "✗ errors ${ws.handlerErrors}" else "errors 0")
            if (ws.keepaliveDisabled) add("✗ no keepalive")
            if (ws.frameSizeUnbounded) add("✗ unbounded frames")
        },
        theme = theme,
    ) {
        ContextPanel("Effective options", theme) {
            FieldLine(
                "keepalive",
                if (ws.keepaliveDisabled) "DISABLED — half-open connections will leak until TCP gives up"
                else "ping every ${ws.pingPeriodMs?.let(::formatMs) ?: "?"} · timeout ${ws.timeoutMs?.let(::formatMs) ?: "?"}",
                theme,
                if (ws.keepaliveDisabled) theme.error else theme.success,
            )
            FieldLine(
                "frame size",
                if (ws.frameSizeUnbounded) "UNBOUNDED — one huge frame can take the heap down"
                else "max ${ws.maxFrameSizeBytes ?: 0} bytes",
                theme,
                if (ws.frameSizeUnbounded) theme.error else null,
            )
            FieldLine("masking", if (ws.masking) "on" else "off", theme)
            FieldLine(
                "lifetime",
                "${ws.opened} opened · ${ws.closed} closed · ${ws.handlerErrors} handler errors" +
                    (ws.closeCodeCounts.takeIf { it.isNotEmpty() }
                        ?.entries?.joinToString(" · ", prefix = " · closes: ") { "${it.key} ${it.value}" }
                        ?: ""),
                theme,
                if (ws.handlerErrors > 0) theme.warning else null,
            )
        }

        val body = subBodyRows()
        val visible = (body - OPTIONS_ROWS - 2 - 1).coerceIn(2, 10)

        when {
            ws.sessions.isNotEmpty() -> {
                FilterableTable(
                    items = ws.sessions.sortedByDescending { it.openedAtEpochMs },
                    columns = sessionColumns(now),
                    onRowSelected = { /* the row already carries the whole session story */ },
                    onExit = onBack,
                    visibleCount = visible,
                    noResultsText = "no session matches the filter",
                    state = rememberTableState<WebSocketSession>(),
                )
                if (ws.sessions.size < ws.activeSessions) {
                    Text(
                        "showing ${ws.sessions.size} itemized of ${ws.activeSessions} active — detail capped, all counted",
                        style = theme.muted,
                        maxLines = 1,
                    )
                } else {
                    Text("live: every open session above updates each poll", style = theme.muted, maxLines = 1)
                }
            }

            ws.routePaths.isNotEmpty() -> {
                FilterableTable(
                    items = ws.routePaths.sorted().map { path -> WsRoute(path, ws.sessionsPerRoute[path] ?: 0) },
                    columns = wsRouteColumns(),
                    onRowSelected = { /* sessions appear here the moment a client connects */ },
                    onExit = onBack,
                    visibleCount = visible,
                    noResultsText = "no route matches the filter",
                    state = rememberTableState<WsRoute>(),
                )
                Text("no sessions open right now — connect a client and this table goes live", style = theme.muted, maxLines = 1)
            }

            else -> Text(
                "no WebSocket routes registered through katalystWebSockets { }",
                style = theme.muted,
                maxLines = 1,
            )
        }
    }
}

/** Border(2) + the 4 option field lines. */
private const val OPTIONS_ROWS = 6

private data class WsRoute(val path: String, val sessions: Int)

private fun wsRouteColumns(): List<TableColumn<WsRoute>> = listOf(
    TableColumn("Route", TableColumnWidth.Weight(1f)) { it.path },
    TableColumn("Sessions", TableColumnWidth.Fixed(9), TextAlign.RIGHT) { it.sessions.toString() },
)

private fun sessionColumns(now: Long): List<TableColumn<WebSocketSession>> = listOf(
    TableColumn("Route", TableColumnWidth.Weight(1f)) { it.path },
    TableColumn("Remote", TableColumnWidth.Fixed(15)) { it.remote ?: "?" },
    TableColumn("Open", TableColumnWidth.Fixed(8), TextAlign.RIGHT) {
        formatAgo(now, it.openedAtEpochMs).removeSuffix(" ago")
    },
    TableColumn("In", TableColumnWidth.Fixed(6), TextAlign.RIGHT) { it.framesIn.toString() },
    TableColumn("Out", TableColumnWidth.Fixed(6), TextAlign.RIGHT) { it.framesOut.toString() },
    TableColumn("Bytes", TableColumnWidth.Fixed(14), TextAlign.RIGHT) {
        "↓${formatBytes(it.bytesIn)} ↑${formatBytes(it.bytesOut)}"
    },
)

/** `512B`, `1.4K`, `2.1M` — session traffic fits a narrow column. */
private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "${bytes}B"
    bytes < 1_048_576 -> "%.1fK".format(bytes / 1024.0)
    else -> "%.1fM".format(bytes / 1_048_576.0)
}
