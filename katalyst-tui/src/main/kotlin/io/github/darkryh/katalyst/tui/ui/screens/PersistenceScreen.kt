package io.github.darkryh.katalyst.tui.ui.screens

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.github.darkryh.katalyst.tui.ui.ContextPanel
import io.github.darkryh.katalyst.tui.ui.FieldLine
import io.github.darkryh.katalyst.tui.ui.NotInstrumentedNote
import io.github.darkryh.katalyst.tui.ui.SubScreen

/**
 * Persistence: the connection pool is the live truth here (HikariCP MXBean), so it leads — a
 * saturation bar plus the pressure signals that predict outages (pending acquisitions, timeouts,
 * leak alarms). Repository operation counters exist in the model but are not fed yet; they are
 * labeled as such rather than shown as reassuring zeros.
 */
@Composable
fun PersistenceScreen(snapshot: TelemetrySnapshot?, theme: DispatchTheme, onBack: () -> Unit) {
    val persistence = snapshot?.persistence ?: run {
        SectionMissing("Persistence", "No persistence module is active on the attached backend.", theme)
        return
    }
    if (!persistence.databaseConfigured) {
        SectionMissing("Persistence", "No database is configured — persistence features are off.", theme)
        return
    }
    val pool = persistence.pool

    SubScreen(
        title = "Persistence",
        tagline = "connection-pool pressure and repository activity",
        stats = buildList {
            if (pool != null) {
                add("pool ${pool.active}/${pool.total}")
                add("util ${(pool.utilization * 100).toInt()}%")
                add(if (pool.pending > 0) "⚠ pending ${pool.pending}" else "pending 0")
                add("timeouts ${pool.connectionTimeouts}")
                add(if (pool.leakAlarms > 0) "✗ leaks ${pool.leakAlarms}" else "leaks 0")
            } else {
                add("pool not started")
            }
            if (persistence.sqlFailures > 0) add("✗ sql failures ${persistence.sqlFailures}")
        },
        theme = theme,
    ) {
        if (pool == null) {
            ContextPanel("Pool not started", theme) {
                Text("The datasource has not opened its pool yet.", style = theme.secondary)
            }
            return@SubScreen
        }

        ContextPanel("Connection pool", theme) {
            val cells = 24
            val filled = (pool.utilization * cells).toInt().coerceIn(0, cells)
            FieldLine(
                "saturation",
                "█".repeat(filled) + "░".repeat(cells - filled) +
                    "  ${pool.active} busy of ${pool.total} open (max ${pool.maxPoolSize})",
                theme,
                when {
                    pool.utilization >= 0.9 -> theme.error
                    pool.utilization >= 0.7 -> theme.warning
                    else -> theme.success
                },
            )
            FieldLine("idle", "${pool.idle} idle · min idle ${pool.minIdle}", theme)
            FieldLine(
                "pressure",
                "${pool.pending} waiting for a connection · ${pool.connectionTimeouts} acquisition timeouts",
                theme,
                if (pool.pending > 0 || pool.connectionTimeouts > 0) theme.warning else null,
            )
            FieldLine(
                "leaks",
                if (pool.leakAlarms > 0) "${pool.leakAlarms} leak alarms — a connection was held past the threshold"
                else "no leak alarms",
                theme,
                if (pool.leakAlarms > 0) theme.error else null,
            )
            FieldLine(
                "datasources",
                "${pool.liveDataSourceCount} live${if (pool.closed) " · POOL CLOSED" else ""}",
                theme,
                if (pool.closed) theme.error else null,
            )
        }

        ContextPanel("Repository activity", theme) {
            FieldLine(
                "writes",
                "${persistence.saveCount} saves (${persistence.insertBranch} inserts · ${persistence.updateBranch} updates · " +
                    "${persistence.updateToInsertFallback} update→insert)",
                theme,
            )
            FieldLine(
                "reads",
                "findById ${persistence.findByIdHits} hits · ${persistence.findByIdMisses} misses · ${persistence.deleteCount} deletes",
                theme,
            )
            FieldLine(
                "failures",
                "${persistence.sqlFailures} sql · ${persistence.mappingValidationFailures} mapping validation",
                theme,
                if (persistence.sqlFailures > 0 || persistence.mappingValidationFailures > 0) theme.error else null,
            )
            NotInstrumentedNote("repository counters above", theme)
        }

        if (persistence.audit.isNotEmpty()) {
            persistence.audit.take(3).forEach { dist ->
                Text(
                    "audit ${dist.table}: " + dist.counts.entries.joinToString(" · ") { "${it.key} ${it.value}" },
                    style = theme.secondary,
                    maxLines = 1,
                )
            }
        } else {
            Text("audit backlog: none reported", style = theme.muted, maxLines = 1)
        }
    }
}
