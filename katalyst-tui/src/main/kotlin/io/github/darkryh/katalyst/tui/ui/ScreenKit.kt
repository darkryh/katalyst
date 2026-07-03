package io.github.darkryh.katalyst.tui.ui

import androidx.compose.runtime.Composable
import com.github.ajalt.mordant.rendering.TextStyle
import io.github.darkryh.dispatch.layout.Arrangement
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.weight
import io.github.darkryh.dispatch.runtime.LocalHibernation
import io.github.darkryh.dispatch.runtime.requireDispatchScope
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.ChipRow
import io.github.darkryh.dispatch.widget.Panel
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.TextOverflow
import io.github.darkryh.katalyst.tui.viewmodel.InspectorUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The shared "Drill Deck" screen kit. Every subsystem screen is built from the same four pieces so
 * the whole inspector answers to ONE keyboard model (type to filter · ↑↓ move · Enter drill ·
 * Esc back):
 *
 *  - [SubScreen]     — 2-row header: accent title + tagline, then the screen's headline numbers
 *                      as chips. First-glance answer to "is this subsystem healthy?".
 *  - master table    — each screen's own FilterableTable of its entities (jobs, routes, keys…).
 *  - [ContextPanel]  — a bordered detail card that FOLLOWS the highlighted table row live
 *                      (via FilterableTableState.selectedItem), so selection IS inspection.
 *  - [SubFooter]     — one hint line + the heap gauge (rendered by Main, not the screens).
 *
 * Frame-budget rule (CRITICAL): Dispatch paints inline with absolute cursor rows — any frame
 * taller than the terminal corrupts. Screens size their tables/panels from [subBodyRows] and
 * degrade (drop the context panel first, then shrink the table) instead of overflowing.
 */

/** Fixed rows Main.kt spends around a subsystem screen: chrome(2) + dividers(2) + footer(1). */
private const val GLOBAL_CHROME_ROWS = 5

/** Rows [SubScreen] itself spends before the body: title line + chip line. */
private const val SUB_HEADER_ROWS = 2

/** Rows a subsystem screen's body may fill below the [SubScreen] header. Never exceed this. */
@Composable
fun subBodyRows(): Int {
    val scope = requireDispatchScope()
    return scope.terminalHeight - bannerRows(scope.terminalHeight) - GLOBAL_CHROME_ROWS - SUB_HEADER_ROWS
}

/**
 * Standard subsystem screen frame: `▍Title  tagline` on one row, headline [stats] chips on the
 * next, then the body. Costs exactly [SUB_HEADER_ROWS] rows above the body.
 */
@Composable
fun SubScreen(
    title: String,
    tagline: String,
    stats: List<String>,
    theme: DispatchTheme,
    body: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("▍$title", style = theme.accent)
            Text("  $tagline", style = theme.muted, modifier = Modifier.weight(1f), maxLines = 1)
        }
        if (stats.isNotEmpty()) ChipRow(tags = stats) else Text("", maxLines = 1)
        body()
    }
}

/**
 * The detail card that follows the master table's highlighted row. Border costs 2 rows; keep
 * `2 + content rows` inside the screen's height budget.
 */
@Composable
fun ContextPanel(
    title: String,
    theme: DispatchTheme,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Panel(title = title, titleStyle = theme.secondary, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(0)) { content() }
    }
}

/** One `label   value` detail row inside a [ContextPanel]; the value clips with an ellipsis. */
@Composable
fun FieldLine(
    label: String,
    value: String,
    theme: DispatchTheme,
    valueStyle: TextStyle? = null,
    labelWidth: Int = 14,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label.padEnd(labelWidth).take(labelWidth), style = theme.muted)
        Text(value, style = valueStyle ?: theme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Honest gap marker: this metric exists in the model but nothing feeds it yet. Screens show this
 * instead of misleading zeros — a zero that means "not measured" must never look like a zero that
 * means "healthy".
 */
@Composable
fun NotInstrumentedNote(what: String, theme: DispatchTheme) {
    Text("◌ $what — not instrumented yet, shown as zero", style = theme.muted, maxLines = 1)
}

/** The subsystem-screen footer: one hint line + the heap gauge. The command prompt lives ONLY on the dashboard. */
@Composable
fun SubFooter(
    state: InspectorUiState,
    theme: DispatchTheme,
    hint: String = "type to filter · ↑↓ move · Enter open · Esc back · Ctrl+C quit",
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        val hibernating = LocalHibernation.current?.isHibernating == true
        if (hibernating) {
            Text(HIBERNATION_HINT, style = theme.warning, modifier = Modifier.weight(1f), maxLines = 1)
        } else {
            Text(hint, style = theme.muted, modifier = Modifier.weight(1f), maxLines = 1)
        }
        Text(heapGauge(state), style = theme.muted)
    }
}

/**
 * Footer bar shown while Dispatch hibernates the idle inspector: the poll is paused, the frame is
 * frozen as-is, and the backend is completely unaffected. Any keypress wakes and resumes.
 * Kept short: it shares the footer row with the heap gauge and must NEVER wrap — a frozen frame
 * that grows a row would breach the height budget.
 */
internal const val HIBERNATION_HINT = "⏾ hibernating — frozen, poll paused · any key wakes"

/** Compact textual heap gauge: `heap ██░░░░░░░░ 12% · 56M/6144M`. Shared by both footers. */
internal fun heapGauge(state: InspectorUiState): String {
    val jvm = state.snapshot?.health?.jvm ?: return "heap —"
    if (jvm.heapMaxBytes <= 0L) return "heap —"
    val ratio = (jvm.heapUsedBytes.toDouble() / jvm.heapMaxBytes.toDouble()).coerceIn(0.0, 1.0)
    val filled = (ratio * HEAP_BAR_CELLS).toInt().coerceIn(0, HEAP_BAR_CELLS)
    val bar = "█".repeat(filled) + "░".repeat(HEAP_BAR_CELLS - filled)
    val mb = 1024L * 1024L
    return "heap $bar ${(ratio * 100).toInt()}% · ${jvm.heapUsedBytes / mb}M/${jvm.heapMaxBytes / mb}M"
}

private const val HEAP_BAR_CELLS = 10

/* ── formatting helpers shared by the screens ────────────────────────────────────────────── */

/** `348 ms`, `2.4 s`, or `3m 12s`. */
internal fun formatMs(ms: Long): String = when {
    ms < 1_000 -> "$ms ms"
    ms < 60_000 -> "%.1f s".format(ms / 1000.0)
    else -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
}

/** Local wall-clock `HH:mm:ss` of an epoch instant. */
internal fun formatClock(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(CLOCK)

/** `12s ago`, `4m ago`, `2h ago` relative to [nowMs]; "—" for null. */
internal fun formatAgo(nowMs: Long, epochMs: Long?): String {
    epochMs ?: return "—"
    val s = ((nowMs - epochMs) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s ago"
        s < 3_600 -> "${s / 60}m ago"
        else -> "${s / 3_600}h ago"
    }
}

/** Countdown to a future instant: `38s`, `2m 10s`, `now`; "—" for null. */
internal fun formatCountdown(nowMs: Long, epochMs: Long?): String {
    epochMs ?: return "—"
    val s = (epochMs - nowMs) / 1000
    return when {
        s <= 0 -> "now"
        s < 60 -> "${s}s"
        else -> "${s / 60}m ${s % 60}s"
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Greedy word-wrap honoring embedded newlines, capped at [maxLines] (last line ellipsized when
 * content is dropped). For multi-line detail text (failure messages, stack traces) in panels.
 */
internal fun wrapText(text: String, width: Int, maxLines: Int): List<String> {
    if (width <= 1 || maxLines <= 0) return emptyList()
    val out = ArrayList<String>(maxLines)
    outer@ for (raw in text.trim().split('\n')) {
        var line = StringBuilder()
        for (word in raw.trim().split(' ')) {
            val fits = line.isEmpty() || line.length + 1 + word.length <= width
            if (fits) {
                if (line.isNotEmpty()) line.append(' ')
                line.append(word.take(width))
            } else {
                out.add(line.toString()); line = StringBuilder(word.take(width))
                if (out.size == maxLines) break@outer
            }
        }
        if (line.isNotEmpty()) { out.add(line.toString()); if (out.size == maxLines) break@outer }
    }
    if (out.size == maxLines && out.last().length >= width - 1) {
        out[out.lastIndex] = out.last().take(width - 1) + "…"
    }
    return out
}
