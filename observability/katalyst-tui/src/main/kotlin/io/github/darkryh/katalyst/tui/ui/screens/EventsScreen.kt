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
import io.github.darkryh.katalyst.tui.ui.ContextPanel
import io.github.darkryh.katalyst.tui.ui.FieldLine
import io.github.darkryh.katalyst.tui.ui.NotInstrumentedNote
import io.github.darkryh.katalyst.tui.ui.SubScreen
import io.github.darkryh.katalyst.tui.ui.formatMs
import io.github.darkryh.katalyst.tui.ui.subBodyRows

/**
 * Events: one row per event TYPE — publish volume, handler latency, and the dead-event risk
 * column that names any event nobody listens to. The context card lists the highlighted type's
 * actual handler functions, so "who consumes this?" is answered without leaving the screen.
 */
@Composable
fun EventsScreen(snapshot: TelemetrySnapshot?, theme: DispatchTheme, onBack: () -> Unit) {
    val events = snapshot?.events ?: run {
        SectionMissing("Events", "The event bus is not active on the attached backend.", theme)
        return
    }

    // One row per event type: subscriptions (who listens) joined with per-type publish stats.
    val statsByType = events.perType.associateBy { it.eventType }
    val handlersByType = events.subscriptions.associate { it.eventType to it.handlers }
    val rows = (handlersByType.keys + statsByType.keys).sorted().map { type ->
        EventRow(
            type = type,
            handlers = handlersByType[type].orEmpty(),
            published = statsByType[type]?.published ?: 0,
            p50Ms = statsByType[type]?.p50Ms ?: 0.0,
            p95Ms = statsByType[type]?.p95Ms ?: 0.0,
        )
    }

    SubScreen(
        title = "Events",
        tagline = "who publishes what, who listens, and what lands on the floor",
        stats = buildList {
            add("published ${events.totalPublished}")
            add("handled ok ${events.handlersSucceeded}")
            add(if (events.handlersFailed > 0) "✗ failed ${events.handlersFailed}" else "failed 0")
            add(if (events.deadEvents > 0) "✗ dead ${events.deadEvents}" else "dead 0")
            add("types ${rows.size}")
        },
        theme = theme,
    ) {
        var alertRows = 0
        if (events.deadEvents > 0) {
            Text(
                "✗ ${events.deadEvents} event(s) were published with NO handler — they vanished silently",
                style = theme.error,
                maxLines = 1,
            )
            alertRows++
        }

        if (rows.isEmpty()) {
            ContextPanel("No event types", theme) {
                Text("No subscriptions are registered and nothing has been published.", style = theme.secondary)
            }
            return@SubScreen
        }

        val body = subBodyRows() - alertRows
        val showContext = body >= 15
        val visible = (body - 2 - (if (showContext) CONTEXT_ROWS else 0)).coerceIn(3, 14)

        val tableState = rememberTableState<EventRow>()
        FilterableTable(
            items = rows,
            columns = eventColumns(),
            onRowSelected = { /* handler list follows in the context card */ },
            onExit = onBack,
            visibleCount = visible,
            noResultsText = "no event type matches the filter",
            state = tableState,
        )

        if (showContext) {
            val row = tableState.selectedItem ?: rows.first()
            ContextPanel(row.type.substringAfterLast('.'), theme) {
                FieldLine(
                    "traffic",
                    "${row.published} published · p50 ${formatMs(row.p50Ms.toLong())} · p95 ${formatMs(row.p95Ms.toLong())}",
                    theme,
                )
                if (row.handlers.isEmpty()) {
                    Text("✗ no handlers — every publish of this event is a dead event", style = theme.error, maxLines = 1)
                } else {
                    row.handlers.take(3).forEach { handler -> Text("• $handler", style = theme.secondary, maxLines = 1) }
                    repeat((3 - row.handlers.size).coerceAtLeast(0)) { Text("", maxLines = 1) }
                    if (row.handlers.size > 3) Text("… and ${row.handlers.size - 3} more", style = theme.muted, maxLines = 1)
                }
                NotInstrumentedNote("dropped emits & async failure counters", theme)
            }
        }
    }
}

/** Border(2) + traffic + 3 handler lines + note. */
private const val CONTEXT_ROWS = 7

private data class EventRow(
    val type: String,
    val handlers: List<String>,
    val published: Long,
    val p50Ms: Double,
    val p95Ms: Double,
)

private fun eventColumns(): List<TableColumn<EventRow>> = listOf(
    TableColumn("Event", TableColumnWidth.Weight(1f)) { it.type },
    TableColumn("Handlers", TableColumnWidth.Fixed(9), TextAlign.RIGHT) { it.handlers.size.toString() },
    TableColumn("Published", TableColumnWidth.Fixed(10), TextAlign.RIGHT) { it.published.toString() },
    TableColumn("p95", TableColumnWidth.Fixed(8), TextAlign.RIGHT) { formatMs(it.p95Ms.toLong()) },
    TableColumn("Risk", TableColumnWidth.Fixed(6)) { if (it.handlers.isEmpty()) "dead!" else "" },
)
