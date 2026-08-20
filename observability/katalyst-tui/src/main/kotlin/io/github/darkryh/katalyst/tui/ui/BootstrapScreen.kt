package io.github.darkryh.katalyst.tui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.darkryh.dispatch.layout.Arrangement
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxSize
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.runtime.requireDispatchScope
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.ChipRow
import io.github.darkryh.dispatch.widget.Spinner
import io.github.darkryh.dispatch.widget.SpinnerStyle
import io.github.darkryh.dispatch.widget.TaskList
import io.github.darkryh.dispatch.widget.TaskStatus
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.katalyst.telemetry.model.BootPhase
import io.github.darkryh.katalyst.telemetry.model.PhaseStatus
import io.github.darkryh.katalyst.tui.viewmodel.InspectorUiState
import kotlinx.coroutines.delay

/**
 * Full-screen bootstrap progress view — what the terminal shows INSTEAD of the sequential log
 * flood while a backend is starting. Pure function of the polled snapshot: the 7-phase
 * [io.github.darkryh.katalyst.telemetry.model.BootTimeline] always exists (phases sit PENDING
 * before boot begins), so overall progress is simply completed-over-total, animated to 100%.
 *
 * States rendered honestly rather than hidden:
 *  - no snapshot yet → spinner + "waiting for backend telemetry"
 *  - phases running → "phase i/N" status line, live task list; the in-flight item carries the
 *    overall percentage as plain text where completed items show their duration
 *  - boot complete → the finished timeline holds while [countdown] ticks down to the dashboard
 *  - a phase FAILED → the phase name + captured message in error style at the bottom; the full
 *    stack trace prints beneath the final frame when the process exits (ERROR bypasses quiet mode)
 *
 * Deliberately border-free: plain lines only, no Panel rectangles — this is a splash, not a tile.
 */
@Composable
fun BootstrapScreen(
    state: InspectorUiState,
    theme: DispatchTheme,
    countdown: Int? = null,
    /** Paced reveal index from the host: phases below it show ✓, the one at it spins. */
    shownDone: Int = Int.MAX_VALUE,
) {
    val boot = state.snapshot?.boot
    val descriptor = state.selected

    // One spinner clock for the whole screen; boot is inherently in-flight, so always animate.
    var frame by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(120)
            frame++
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1)) {
        KatalystBanner(theme, minRowsForFullArt = 30)
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("starting ${descriptor?.appName ?: "backend"}", style = theme.secondary)
            descriptor?.let { Text("  pid ${it.pid}", style = theme.muted) }
        }

        if (boot == null) {
            Row {
                Spinner(frame = frame, style = SpinnerStyle.Circle, textStyle = theme.warning)
                Text(" waiting for backend telemetry…", style = theme.muted)
            }
        } else {
            val phases = boot.phases
            val total = phases.size.coerceAtLeast(1)
            // The reveal never runs ahead of reality: a phase only shows ✓ once it truly
            // completed AND the paced walk has reached it.
            val prefixDone = phases.takeWhile {
                it.status == PhaseStatus.COMPLETED || it.status == PhaseStatus.SKIPPED
            }.count()
            val shown = shownDone.coerceIn(0, prefixDone)
            val allShown = shown >= phases.size
            val focus = phases.getOrNull(shown)
            val failedVisible = focus?.status == PhaseStatus.FAILED
            // Overall progress as plain text on the in-flight item — no bar widget; the phase
            // list itself is the visualization.
            val percent = (((shown + if (!allShown && !failedVisible) 0.5f else 0f) / total) * 100)
                .toInt().coerceIn(0, 100)

            Row {
                when {
                    failedVisible -> Text("✗ boot failed at ${focus?.name}", style = theme.error)
                    countdown != null ->
                        Text("✓ boot complete — opening inspector in ${countdown}s", style = theme.success)
                    !allShown -> {
                        Spinner(frame = frame, style = SpinnerStyle.Circle, textStyle = theme.warning)
                        Text(" phase ${shown + 1}/$total · ${focus?.name ?: "…"}", style = theme.warning)
                    }
                    else -> Text("✓ all phases complete — starting server…", style = theme.success)
                }
            }

            // Frame-budget rule: Dispatch paints inline with absolute rows, so the splash must
            // never exceed terminalHeight — on short terminals the per-phase detail rows go.
            val showDetails = requireDispatchScope().terminalHeight >= 30
            TaskList(
                tasks = phases.mapIndexed { index, phase -> RevealedPhase(index, phase) },
                spinnerFrame = frame,
                status = { revealed ->
                    when {
                        revealed.index < shown ->
                            if (revealed.phase.status == PhaseStatus.SKIPPED) TaskStatus.Skipped
                            else TaskStatus.Completed
                        revealed.index == shown && failedVisible -> TaskStatus.Failed
                        revealed.index == shown && !allShown -> TaskStatus.InProgress
                        else -> TaskStatus.Pending
                    }
                },
                description = { it.phase.name },
                details = { revealed ->
                    when {
                        revealed.index == shown && !allShown && !failedVisible -> "$percent%"
                        !showDetails -> null
                        revealed.index < shown -> phaseDetail(revealed.phase)
                        else -> null
                    }
                },
            )

            if (boot.criticalCount > 0 || boot.warningCount > 0) {
                ChipRow(
                    tags = listOf(
                        "critical ${boot.criticalCount}",
                        "warning ${boot.warningCount}",
                        "info ${boot.infoCount}",
                    ),
                )
            }

            if (failedVisible && focus != null) {
                Spacer(Modifier.height(1))
                Text("✗ ${focus.name}", style = theme.error)
                Text("  ${focus.message ?: "No failure message captured for this phase."}", style = theme.error)
                Text(
                    "  The process will exit; the full stack trace prints below this screen.",
                    style = theme.muted,
                )
            }
        }
    }
}

/** A phase paired with its list position so the paced reveal can address it by index. */
private data class RevealedPhase(val index: Int, val phase: BootPhase)


/** Duration then message, whichever exist; null hides the detail row. */
private fun phaseDetail(phase: BootPhase): String? {
    val parts = buildList {
        phase.durationMs?.let { add("$it ms") }
        phase.message?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.joinToString(" · ").ifBlank { null }
}
