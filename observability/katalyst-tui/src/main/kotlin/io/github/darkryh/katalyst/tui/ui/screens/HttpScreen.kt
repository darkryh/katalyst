package io.github.darkryh.katalyst.tui.ui.screens

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.FilterableTable
import io.github.darkryh.dispatch.widget.TableColumn
import io.github.darkryh.dispatch.widget.TableColumnWidth
import io.github.darkryh.dispatch.widget.rememberTableState
import io.github.darkryh.katalyst.telemetry.model.RouteEntry
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.github.darkryh.katalyst.tui.ui.ContextPanel
import io.github.darkryh.katalyst.tui.ui.FieldLine
import io.github.darkryh.katalyst.tui.ui.NotInstrumentedNote
import io.github.darkryh.katalyst.tui.ui.SubScreen
import io.github.darkryh.katalyst.tui.ui.formatMs
import io.github.darkryh.katalyst.tui.ui.subBodyRows

/**
 * HTTP: the live router. Master table = every registered route (type to find one); the context
 * card carries the process-wide traffic truth — totals, in-flight, status classes, and the
 * all-requests latency rollup. Honest about its one gap: latency is not split per route yet.
 */
@Composable
fun HttpScreen(snapshot: TelemetrySnapshot?, theme: DispatchTheme, onBack: () -> Unit) {
    val http = snapshot?.http ?: run {
        SectionMissing("HTTP", "The HTTP server has not started or reported its router yet.", theme)
        return
    }
    val err5xx = http.statusClassCounts["5xx"] ?: 0

    SubScreen(
        title = "HTTP",
        tagline = "registered routes and the live request pressure on ${http.host ?: "?"}:${http.port}",
        stats = buildList {
            add("engine ${http.engineId ?: "?"}")
            add("requests ${http.totalRequests}")
            add("in-flight ${http.inFlight}")
            add(if (err5xx > 0) "✗ 5xx $err5xx" else "5xx 0")
            add("exceptions ${http.exceptionsHandled}")
        },
        theme = theme,
    ) {
        if (http.routes.isEmpty()) {
            ContextPanel("No routes registered", theme) {
                FieldLine("bind", "${http.host ?: "?"}:${http.port}", theme)
                FieldLine("hint", "routes appear once a Ktor module installs them", theme, theme.muted)
            }
            return@SubScreen
        }

        val body = subBodyRows()
        val showContext = body >= 15
        val visible = (body - 2 - (if (showContext) CONTEXT_ROWS else 0)).coerceIn(3, 14)

        val tableState = rememberTableState<RouteEntry>()
        FilterableTable(
            items = http.routes.sortedWith(compareBy({ it.path }, { it.method })),
            columns = routeColumns(),
            onRowSelected = { /* aggregate traffic context below; no per-route stats yet */ },
            onExit = onBack,
            visibleCount = visible,
            noResultsText = "no route matches the filter",
            state = tableState,
        )

        if (showContext) {
            val route = tableState.selectedItem
            // The telemetry HttpCapturer has no dedicated top-level field for process-wide latency,
            // so it surfaces the aggregate as a single RouteStats entry tagged with this sentinel
            // template (see its own "(all requests)" comment). Match on that tag explicitly rather
            // than assuming it is always the first entry — once real per-route stats land in this
            // list, a positional lookup would silently mislabel one route's latency as the global
            // rollup.
            val all = http.perRoute.firstOrNull { it.template == ALL_REQUESTS_TEMPLATE }
            ContextPanel("Traffic", theme) {
                FieldLine("selected", route?.let { "${it.method} ${it.path}" } ?: "—", theme, theme.accent)
                FieldLine(
                    "totals",
                    "${http.totalRequests} requests · ${http.inFlight} in flight · " +
                        "${http.abortedByMiddleware} aborted by middleware · ${http.exceptionsHandled} exceptions handled",
                    theme,
                )
                FieldLine(
                    "status",
                    listOf("2xx", "3xx", "4xx", "5xx")
                        .joinToString(" · ") { "$it ${http.statusClassCounts[it] ?: 0}" },
                    theme,
                    if (err5xx > 0) theme.warning else null,
                )
                FieldLine(
                    "latency (all)",
                    all?.let {
                        "p50 ${formatMs(it.p50Ms.toLong())} · p95 ${formatMs(it.p95Ms.toLong())} · max ${formatMs(it.maxMs.toLong())} over ${it.count} requests"
                    } ?: "no requests measured yet",
                    theme,
                )
                NotInstrumentedNote("per-route latency split", theme)
            }
        }
    }
}

private const val CONTEXT_ROWS = 7

/**
 * Must match the sentinel `io.github.darkryh.katalyst.telemetry.capture.HttpCapturer` tags the
 * process-wide latency aggregate with (`RouteStats("(all requests)", ...)`), since katalyst-tui only
 * depends on katalyst-telemetry-model and has no compile edge to the capturer that produces it.
 */
private const val ALL_REQUESTS_TEMPLATE = "(all requests)"

private fun routeColumns(): List<TableColumn<RouteEntry>> = listOf(
    TableColumn("Method", TableColumnWidth.Fixed(8)) { it.method },
    TableColumn("Path", TableColumnWidth.Weight(1f)) { it.path },
)
