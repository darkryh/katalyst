package io.github.darkryh.katalyst.tui.ui.screens

import androidx.compose.runtime.Composable
import com.github.ajalt.mordant.rendering.TextAlign
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.FilterableTable
import io.github.darkryh.dispatch.widget.TableColumn
import io.github.darkryh.dispatch.widget.TableColumnWidth
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.TextOverflow
import io.github.darkryh.dispatch.widget.rememberTableState
import io.github.darkryh.katalyst.telemetry.model.PhaseStatus
import io.github.darkryh.katalyst.telemetry.model.StartupWarning
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.github.darkryh.katalyst.tui.ui.ContextPanel
import io.github.darkryh.katalyst.tui.ui.FieldLine
import io.github.darkryh.katalyst.tui.ui.SubScreen
import io.github.darkryh.katalyst.tui.ui.formatMs
import io.github.darkryh.katalyst.tui.ui.subBodyRows

/**
 * Boot record: the finished startup timeline. All phases with durations at a glance (the splash
 * already played them live; this is the post-mortem view), then the startup warnings as the
 * master table — the context card shows the highlighted warning's full message and hint.
 */
@Composable
fun BootScreen(snapshot: TelemetrySnapshot?, theme: DispatchTheme, onBack: () -> Unit) {
    val boot = snapshot?.boot ?: run {
        SectionMissing("Boot", "Boot telemetry is captured at startup; the backend reported none.", theme)
        return
    }

    SubScreen(
        title = "Boot",
        tagline = "how startup went — every phase, its cost, and what it warned about",
        stats = buildList {
            add("total ${boot.totalBootstrapTimeMs?.let(::formatMs) ?: "—"}")
            add("wall ${boot.actualWallClockMs?.let(::formatMs) ?: "—"}")
            add("critical ${boot.criticalCount}")
            add("warning ${boot.warningCount}")
            add("info ${boot.infoCount}")
            boot.phases.firstOrNull { it.status == PhaseStatus.FAILED }?.let { add("✗ failed: ${it.name}") }
        },
        theme = theme,
    ) {
        // Fixed cost: one row per phase (always 7). The warnings table gets what remains.
        boot.phases.forEach { phase ->
            val (glyph, style) = when (phase.status) {
                PhaseStatus.COMPLETED -> "✓" to theme.success
                PhaseStatus.FAILED -> "✗" to theme.error
                PhaseStatus.RUNNING -> "▶" to theme.warning
                PhaseStatus.SKIPPED -> "↷" to theme.muted
                PhaseStatus.PENDING -> "◌" to theme.muted
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("$glyph ${phase.name.padEnd(36).take(36)}", style = style)
                Text((phase.durationMs?.let(::formatMs) ?: "—").padStart(9), style = theme.secondary)
                Text("  ${phase.message ?: ""}", style = theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        val body = subBodyRows()
        val remaining = body - boot.phases.size
        if (boot.warnings.isEmpty()) {
            Text("no startup warnings — clean boot", style = theme.muted, maxLines = 1)
            return@SubScreen
        }

        val showContext = remaining >= 11
        val visible = (remaining - 2 - (if (showContext) WARNING_CONTEXT_ROWS else 0)).coerceIn(2, 8)
        val tableState = rememberTableState<StartupWarning>()
        FilterableTable(
            items = boot.warnings,
            columns = warningColumns(),
            onRowSelected = { /* full detail follows in the context card */ },
            onExit = onBack,
            visibleCount = visible,
            noResultsText = "no warning matches the filter",
            state = tableState,
        )
        if (showContext) {
            val warning = tableState.selectedItem ?: boot.warnings.first()
            ContextPanel("${warning.severity.name.lowercase()} · ${warning.category}", theme) {
                FieldLine("message", warning.message, theme)
                FieldLine("hint", warning.hint ?: "no hint provided", theme, theme.secondary)
            }
        }
    }
}

/** Border(2) + message + hint. */
private const val WARNING_CONTEXT_ROWS = 4

private fun warningColumns(): List<TableColumn<StartupWarning>> = listOf(
    TableColumn("Sev", TableColumnWidth.Fixed(8)) { it.severity.name.lowercase() },
    TableColumn("Category", TableColumnWidth.Fixed(16)) { it.category },
    TableColumn("Message", TableColumnWidth.Weight(1f)) { it.message },
    TableColumn("Hint", TableColumnWidth.Fixed(4), TextAlign.RIGHT) { if (it.hint != null) "…" else "" },
)
