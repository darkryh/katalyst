package io.github.darkryh.katalyst.tui.ui.screens

import androidx.compose.runtime.Composable
import com.github.ajalt.mordant.rendering.TextAlign
import io.github.darkryh.dispatch.runtime.requireDispatchScope
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.FilterableTable
import io.github.darkryh.dispatch.widget.TableColumn
import io.github.darkryh.dispatch.widget.TableColumnWidth
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.rememberTableState
import io.github.darkryh.katalyst.telemetry.model.JobRunEntry
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.github.darkryh.katalyst.tui.ui.ContextPanel
import io.github.darkryh.katalyst.tui.ui.FieldLine
import io.github.darkryh.katalyst.tui.ui.SubScreen
import io.github.darkryh.katalyst.tui.ui.formatAgo
import io.github.darkryh.katalyst.tui.ui.formatClock
import io.github.darkryh.katalyst.tui.ui.formatCountdown
import io.github.darkryh.katalyst.tui.ui.formatMs
import io.github.darkryh.katalyst.tui.ui.subBodyRows
import io.github.darkryh.katalyst.tui.ui.wrapText

/**
 * One job's drill-down: the run history the scheduler now records for every execution. The master
 * table lists recent runs newest-first (start, duration, outcome, error headline); the context card
 * shows the highlighted run in full — including the captured failure message and stack-trace
 * excerpt, which used to exist only as a log line that scrolled away.
 */
@Composable
fun SchedulerJobScreen(
    snapshot: TelemetrySnapshot?,
    theme: DispatchTheme,
    jobName: String,
    onBack: () -> Unit,
) {
    val job = snapshot?.scheduler?.jobs?.find { it.name == jobName } ?: run {
        SectionMissing(
            "Scheduler ▸ $jobName",
            "This job is no longer registered on the attached backend.",
            theme,
        )
        return
    }
    val now = snapshot.capturedAtEpochMs
    val runs = job.recentRuns

    SubScreen(
        title = "Scheduler ▸ ${job.name}",
        tagline = "${job.scheduleDescriptor} · next fire ${formatCountdown(now, job.nextFireEpochMs)}",
        stats = buildList {
            add("runs ${job.runCount}")
            add("ok ${job.successCount}")
            add("fail ${job.failureCount}")
            add("timeout ${job.timeoutCount}")
            add("p95 ${formatMs(job.p95Ms.toLong())}")
            if (job.consecutiveFailures > 0) add("streak ${job.consecutiveFailures}✗")
            if (job.currentlyRunning) add("▶ running ${job.currentRunElapsedMs?.let(::formatMs) ?: ""}")
        },
        theme = theme,
    ) {
        if (runs.isEmpty()) {
            ContextPanel("No runs recorded yet", theme) {
                Text("History records every execution from now on — durations, outcomes,", style = theme.secondary)
                Text("and the full failure detail when a run throws or times out.", style = theme.secondary)
                FieldLine("state", livenessLabel(job), theme)
                FieldLine("last known", job.lastOutcome ?: "never ran", theme, outcomeStyle(job.lastOutcome, theme))
            }
            return@SubScreen
        }

        // Budget: table chrome(2) + rows; the run-detail card takes the rest (border 2 + 3 fixed
        // fields + error lines). The card never drops — inspecting one run IS this screen's job.
        val body = subBodyRows()
        val visible = (body - 2 - MIN_DETAIL_ROWS).coerceIn(3, 10)
        val errorLines = (body - 2 - visible - 2 - 3).coerceIn(1, 12)

        val tableState = rememberTableState<JobRunEntry>()
        FilterableTable(
            items = runs,
            columns = runColumns(now),
            onRowSelected = { /* the context card below already shows the full run */ },
            onExit = onBack,
            visibleCount = visible,
            noResultsText = "no run matches the filter",
            state = tableState,
        )

        val run = tableState.selectedItem ?: runs.first()
        ContextPanel("run · ${formatClock(run.startedAtEpochMs)}", theme) {
            FieldLine("started", "${formatClock(run.startedAtEpochMs)} · ${formatAgo(now, run.startedAtEpochMs)}", theme)
            FieldLine("duration", formatMs(run.durationMs), theme)
            FieldLine("outcome", "${outcomeGlyph(run.outcome)} ${run.outcome}", theme, outcomeStyle(run.outcome, theme))
            val error = run.error
            if (error == null) {
                Text("completed cleanly — no error captured", style = theme.muted)
            } else {
                val width = (requireDispatchScope().terminalWidth - 6).coerceAtLeast(20)
                wrapText(error, width, errorLines).forEach { line -> Text(line, style = theme.error) }
            }
        }
    }
}

/** Detail card floor: border(2) + started/duration/outcome + one error line. */
private const val MIN_DETAIL_ROWS = 8

private fun runColumns(now: Long): List<TableColumn<JobRunEntry>> = listOf(
    TableColumn("Start", TableColumnWidth.Fixed(8)) { formatClock(it.startedAtEpochMs) },
    TableColumn("Ago", TableColumnWidth.Fixed(8), TextAlign.RIGHT) { formatAgo(now, it.startedAtEpochMs) },
    TableColumn("Duration", TableColumnWidth.Fixed(9), TextAlign.RIGHT) { formatMs(it.durationMs) },
    TableColumn("Outcome", TableColumnWidth.Fixed(9)) { "${outcomeGlyph(it.outcome)} ${it.outcome}" },
    TableColumn("Error", TableColumnWidth.Weight(1f)) { it.error?.lineSequence()?.firstOrNull() ?: "—" },
)
