package io.github.darkryh.katalyst.tui.ui.screens

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.github.darkryh.katalyst.tui.ui.ContextPanel
import io.github.darkryh.katalyst.tui.ui.FieldLine
import io.github.darkryh.katalyst.tui.ui.NotInstrumentedNote
import io.github.darkryh.katalyst.tui.ui.SubScreen
import io.github.darkryh.katalyst.tui.ui.formatMs
import io.github.darkryh.katalyst.tui.ui.subBodyRows

/**
 * Transactions: outcome tallies and latency percentiles (live from the transaction manager),
 * the effective configuration with configured-vs-effective isolation surfaced as a mismatch
 * warning, and the adapter chain in priority order. In-flight tracking exists in the model but
 * is not fed yet — labeled, not zero-faked.
 */
@Composable
fun TransactionsScreen(snapshot: TelemetrySnapshot?, theme: DispatchTheme, onBack: () -> Unit) {
    val tx = snapshot?.transactions ?: run {
        SectionMissing("Transactions", "No transaction manager is active on the attached backend.", theme)
        return
    }
    val total = tx.committed + tx.rolledBack + tx.timedOut + tx.failed

    SubScreen(
        title = "Transactions",
        tagline = "how transactions are ending, and the rules they run under",
        stats = buildList {
            add("committed ${tx.committed}")
            add(if (tx.rolledBack > 0) "⚠ rolled back ${tx.rolledBack}" else "rolled back 0")
            add(if (tx.timedOut > 0) "✗ timed out ${tx.timedOut}" else "timed out 0")
            add(if (tx.failed > 0) "✗ failed ${tx.failed}" else "failed 0")
            add("p95 ${formatMs(tx.p95Ms.toLong())}")
        },
        theme = theme,
    ) {
        ContextPanel("Outcomes", theme) {
            val cells = 24
            val ok = if (total > 0) (tx.committed.toDouble() / total * cells).toInt().coerceIn(0, cells) else 0
            FieldLine(
                "commit rate",
                if (total == 0L) "no transactions yet"
                else "█".repeat(ok) + "░".repeat(cells - ok) + "  ${tx.committed} of $total committed",
                theme,
                if (total > 0 && tx.committed < total) theme.warning else theme.success,
            )
            FieldLine(
                "latency",
                "p50 ${formatMs(tx.p50Ms.toLong())} · p95 ${formatMs(tx.p95Ms.toLong())} · p99 ${formatMs(tx.p99Ms.toLong())}",
                theme,
            )
            FieldLine(
                "in flight",
                if (tx.inFlight.isEmpty()) "none at capture" else "${tx.inFlight.size} open",
                theme,
            )
            NotInstrumentedNote("in-flight transaction list", theme)
        }

        ContextPanel("Configuration", theme) {
            // Only a REPORTED effective isolation can contradict the configured one — "not
            // reported" is an instrumentation gap, not a mismatch alarm.
            val mismatch = tx.isolationConfigured != null && tx.isolationEffective != null &&
                tx.isolationConfigured != tx.isolationEffective
            FieldLine(
                "isolation",
                "${tx.isolationConfigured ?: "driver default"} → effective ${tx.isolationEffective ?: "not reported"}" +
                    if (mismatch) "  (MISMATCH)" else "",
                theme,
                if (mismatch) theme.warning else null,
            )
            FieldLine("timeout", tx.timeoutMs?.let { "${formatMs(it)} per transaction" } ?: "none configured", theme)
            FieldLine("retries", tx.maxRetries?.let { "up to $it" } ?: "none configured", theme)
            FieldLine(
                "metrics",
                if (tx.metricsCollectorAttached) "collector attached" else "no metrics collector attached",
                theme,
            )
        }

        if (subBodyRows() >= 14 + tx.adapters.size.coerceAtMost(4)) {
            ContextPanel("Adapter chain (${tx.adapters.size})", theme) {
                if (tx.adapters.isEmpty()) {
                    // Boot telemetry proves adapters register; the list itself is not fed yet.
                    NotInstrumentedNote("adapter chain details", theme)
                } else {
                    tx.adapters.sortedBy { it.priority }.take(4).forEach { adapter ->
                        Text(
                            "${adapter.priority.toString().padStart(3)}  ${adapter.name}" +
                                if (adapter.critical) "  · critical (failure rolls back)" else "",
                            style = if (adapter.critical) theme.secondary else theme.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
