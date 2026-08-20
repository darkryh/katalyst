package io.github.darkryh.katalyst.tui.ui.screens

import androidx.compose.runtime.Composable
import com.github.ajalt.mordant.rendering.TextAlign
import io.github.darkryh.dispatch.navigation.LocalNavigator
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.FilterableTable
import io.github.darkryh.dispatch.widget.TableColumn
import io.github.darkryh.dispatch.widget.TableColumnWidth
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.rememberTableState
import io.github.darkryh.katalyst.telemetry.model.JobEntry
import io.github.darkryh.katalyst.telemetry.model.JobKind
import io.github.darkryh.katalyst.telemetry.model.JobLiveness
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.github.darkryh.katalyst.tui.navigation.SchedulerJobRoute
import io.github.darkryh.katalyst.tui.ui.ContextPanel
import io.github.darkryh.katalyst.tui.ui.FieldLine
import io.github.darkryh.katalyst.tui.ui.SubScreen
import io.github.darkryh.katalyst.tui.ui.formatAgo
import io.github.darkryh.katalyst.tui.ui.formatCountdown
import io.github.darkryh.katalyst.tui.ui.formatMs
import io.github.darkryh.katalyst.tui.ui.subBodyRows

/**
 * Scheduler master screen: every registered job as one filterable row — schedule, next fire,
 * run/fail tallies, live state — with a context card that follows the highlighted job (last
 * outcome, latency percentiles, recent-outcome strip). Enter drills into the job's full run
 * history ([SchedulerJobScreen]); that is where individual failures are diagnosed.
 */
@Composable
fun SchedulerScreen(snapshot: TelemetrySnapshot?, theme: DispatchTheme, onBack: () -> Unit) {
    val scheduler = snapshot?.scheduler ?: run {
        SectionMissing(
            "Scheduler",
            "katalyst-scheduler is off the classpath or enableScheduler() was never called.",
            theme,
        )
        return
    }
    val navigator = LocalNavigator.current
    val now = snapshot.capturedAtEpochMs
    val jobs = scheduler.jobs.sortedBy { it.name }
    val failures = jobs.sumOf { it.failureCount + it.timeoutCount }

    SubScreen(
        title = "Scheduler",
        tagline = "every scheduled job, its next fire, and how its runs are ending",
        stats = buildList {
            add("jobs ${jobs.size}")
            add("running ${scheduler.dispatcherInFlight}")
            add("dispatcher ${scheduler.dispatcherInFlight}/${scheduler.dispatcherParallelism}")
            add("failures $failures")
            if (scheduler.rejectedCandidates.isNotEmpty()) add("rejected ${scheduler.rejectedCandidates.size}")
        },
        theme = theme,
    ) {
        if (jobs.isEmpty()) {
            ContextPanel("No scheduled jobs", theme) {
                Text("Nothing is registered with the scheduler yet.", style = theme.secondary)
                Text("Jobs appear here the moment a scheduled component registers.", style = theme.muted)
                scheduler.rejectedCandidates.entries.take(3).forEach { (name, reason) ->
                    Text("✗ $name — $reason", style = theme.error, maxLines = 1)
                }
            }
            return@SubScreen
        }

        // Height budget: table chrome(2) + rows, context panel when it fits, alert line when shown.
        val body = subBodyRows()
        val alertRows = if (scheduler.rejectedCandidates.isNotEmpty()) 1 else 0
        val showContext = body - alertRows >= 15
        val visible = (body - alertRows - 2 - (if (showContext) CONTEXT_ROWS else 0)).coerceIn(3, 14)

        if (alertRows == 1) {
            val (name, reason) = scheduler.rejectedCandidates.entries.first()
            Text(
                "✗ ${scheduler.rejectedCandidates.size} candidate(s) rejected — $name: $reason",
                style = theme.error,
                maxLines = 1,
            )
        }

        val tableState = rememberTableState<JobEntry>()
        FilterableTable(
            items = jobs,
            columns = jobColumns(now),
            onRowSelected = { job -> navigator.navigate(SchedulerJobRoute(job.name)) },
            onExit = onBack,
            visibleCount = visible,
            noResultsText = "no job matches the filter",
            state = tableState,
        )

        if (showContext) {
            val job = tableState.selectedItem ?: jobs.first()
            ContextPanel(job.name, theme) {
                FieldLine("schedule", "${job.scheduleDescriptor}${job.timeZone?.let { " · $it" } ?: ""}", theme)
                FieldLine(
                    "last run",
                    job.lastOutcome?.let {
                        "${outcomeGlyph(it)} $it · ${formatAgo(now, job.lastRunEpochMs)} · ${job.lastDurationMs?.let(::formatMs) ?: "—"}"
                    } ?: "never ran",
                    theme,
                    valueStyle = outcomeStyle(job.lastOutcome, theme),
                )
                FieldLine(
                    "latency",
                    "p50 ${formatMs(job.p50Ms.toLong())} · p95 ${formatMs(job.p95Ms.toLong())} · max ${formatMs(job.maxMs.toLong())}",
                    theme,
                )
                FieldLine(
                    "state",
                    "${livenessLabel(job)} · next ${formatCountdown(now, job.nextFireEpochMs)}" +
                        (if (job.consecutiveFailures > 0) " · ${job.consecutiveFailures} consecutive failures" else ""),
                    theme,
                    valueStyle = if (job.consecutiveFailures > 0) theme.warning else null,
                )
                FieldLine(
                    "history",
                    "${job.recentRuns.take(10).joinToString("") { outcomeGlyph(it.outcome) }.ifEmpty { "—" }} · Enter for full run history",
                    theme,
                )
            }
        }
    }
}

/** Border(2) + the 5 field lines above. */
private const val CONTEXT_ROWS = 7

private fun jobColumns(now: Long): List<TableColumn<JobEntry>> = listOf(
    TableColumn("Job", TableColumnWidth.Weight(1f)) { it.name },
    TableColumn("Kind", TableColumnWidth.Fixed(6)) { kindLabel(it.kind) },
    TableColumn("Schedule", TableColumnWidth.Fixed(14)) { it.scheduleDescriptor },
    TableColumn("Next", TableColumnWidth.Fixed(7), TextAlign.RIGHT) { formatCountdown(now, it.nextFireEpochMs) },
    TableColumn("Runs", TableColumnWidth.Fixed(5), TextAlign.RIGHT) { it.runCount.toString() },
    TableColumn("Fail", TableColumnWidth.Fixed(5), TextAlign.RIGHT) { (it.failureCount + it.timeoutCount).toString() },
    TableColumn("Live", TableColumnWidth.Fixed(7)) { livenessLabel(it) },
)

private fun kindLabel(kind: JobKind): String = when (kind) {
    JobKind.CRON -> "cron"
    JobKind.FIXED_RATE -> "rate"
    JobKind.FIXED_DELAY -> "delay"
    JobKind.ONE_TIME -> "once"
    JobKind.UNKNOWN -> "?"
}

internal fun livenessLabel(job: JobEntry): String = when {
    job.currentlyRunning -> "▶ run"
    else -> when (job.liveness) {
        JobLiveness.ACTIVE -> "active"
        JobLiveness.WAITING_INITIAL_DELAY -> "waiting"
        JobLiveness.COMPLETED -> "done"
        JobLiveness.CANCELLED -> "cancel"
        JobLiveness.UNKNOWN -> "?"
    }
}

internal fun outcomeGlyph(outcome: String): String = when (outcome) {
    "success" -> "✓"
    "timeout" -> "⏱"
    else -> "✗"
}

internal fun outcomeStyle(outcome: String?, theme: DispatchTheme) = when (outcome) {
    null -> theme.muted
    "success" -> theme.success
    "timeout" -> theme.warning
    else -> theme.error
}
