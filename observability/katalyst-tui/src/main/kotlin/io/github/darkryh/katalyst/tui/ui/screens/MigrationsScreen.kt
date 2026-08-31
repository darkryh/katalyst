package io.github.darkryh.katalyst.tui.ui.screens

import androidx.compose.runtime.Composable
import com.github.ajalt.mordant.rendering.TextAlign
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.FilterableTable
import io.github.darkryh.dispatch.widget.TableColumn
import io.github.darkryh.dispatch.widget.TableColumnWidth
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.rememberTableState
import io.github.darkryh.katalyst.telemetry.model.MigrationEntry
import io.github.darkryh.katalyst.telemetry.model.MigrationFailure
import io.github.darkryh.katalyst.telemetry.model.MigrationSnapshot
import io.github.darkryh.katalyst.telemetry.model.MigrationState
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.github.darkryh.katalyst.tui.ui.ContextPanel
import io.github.darkryh.katalyst.tui.ui.FieldLine
import io.github.darkryh.katalyst.tui.ui.SubScreen
import io.github.darkryh.katalyst.tui.ui.formatAgo
import io.github.darkryh.katalyst.tui.ui.formatClock
import io.github.darkryh.katalyst.tui.ui.formatMs
import io.github.darkryh.katalyst.tui.ui.subBodyRows

/**
 * Migrations: one row per migration in its lifecycle state, checksum drift flagged per row and in
 * the context card (db checksum vs code checksum side by side — the fastest way to see WHICH side
 * changed). Validation problems and a live in-progress migration surface as alert lines above the
 * table, never hidden in a second pane.
 */
@Composable
fun MigrationsScreen(snapshot: TelemetrySnapshot?, theme: DispatchTheme, onBack: () -> Unit) {
    val migrations = snapshot?.migrations ?: run {
        SectionMissing("Migrations", "katalyst-migrations is off the classpath or has no migration source.", theme)
        return
    }

    if (!migrations.statusReady) {
        SubScreen(
            title = "Migrations",
            tagline = "schema history — what ran, what waits, and what drifted",
            stats = listOf("discovering migrations"),
            theme = theme,
        ) {
            ContextPanel("Discovering migrations", theme) {
                Text(
                    "Source migrations are still being registered. Status will appear when discovery completes.",
                    style = theme.secondary,
                )
            }
        }
        return
    }

    val now = snapshot.capturedAtEpochMs
    val drifted = migrations.entries.count { it.checksumDrift }
    val failedIds = migrations.recentFailures.mapTo(mutableSetOf()) { it.id }

    SubScreen(
        title = "Migrations",
        tagline = "schema history — what ran, what waits, and what drifted",
        stats = buildList {
            val nonZero = migrations.tallies.filterValues { it > 0 }
            if (nonZero.isEmpty()) add("history empty")
            nonZero.forEach { (state, count) -> add("${migrationTallyLabel(state)} $count") }
            if (drifted > 0) add("✗ drift $drifted")
            if (!migrations.historyReadable) add("✗ history unreadable")
            if (migrations.recentFailures.isNotEmpty()) add("✗ failed attempts ${migrations.recentFailures.size}")
            if (!migrations.runAtStartup) add("startup run off")
        },
        theme = theme,
    ) {
        // Alert lines: a running migration and validation problems always outrank layout polish.
        var alertRows = 0
        migrations.runningId?.let {
            Text(
                "▶ applying $it · ${migrations.runningElapsedMs?.let(::formatMs) ?: "…"} elapsed",
                style = theme.warning,
                maxLines = 1,
            )
            alertRows++
        }
        if (migrations.validationErrors.isNotEmpty()) {
            Text(
                "✗ ${migrations.validationErrors.size} validation error(s) — ${migrations.validationErrors.first()}",
                style = theme.error,
                maxLines = 1,
            )
            alertRows++
        }
        migrations.latestFailure()?.let { failure ->
            Text(
                migrationFailureAlert(failure),
                style = theme.error,
                maxLines = 1,
            )
            alertRows++
        }
        if (migrations.schemaDriftStatements > 0) {
            Text(
                "✗ schema drift: ${migrations.schemaDriftStatements} statement(s) differ from the migration history",
                style = theme.error,
                maxLines = 1,
            )
            alertRows++
        }

        if (!migrations.historyReadable) {
            ContextPanel("Migration history unavailable", theme) {
                Text(
                    migrations.historyError ?: "The migration history table could not be read.",
                    style = theme.error,
                )
            }
            return@SubScreen
        }

        if (migrations.entries.isEmpty()) {
            ContextPanel("No migrations", theme) {
                Text("No migration files are registered for this backend.", style = theme.secondary)
            }
            return@SubScreen
        }

        val body = subBodyRows() - alertRows
        val showContext = body >= 15
        val visible = (body - 2 - (if (showContext) CONTEXT_ROWS else 0)).coerceIn(3, 14)

        val tableState = rememberTableState<MigrationEntry>()
        FilterableTable(
            items = migrations.entries,
            columns = migrationColumns(failedIds),
            onRowSelected = { /* checksum detail follows in the context card */ },
            onExit = onBack,
            visibleCount = visible,
            noResultsText = "no migration matches the filter",
            state = tableState,
        )

        if (showContext) {
            val entry = tableState.selectedItem ?: migrations.entries.first()
            val lastFailure = migrations.recentFailures.lastOrNull { it.id == entry.id }
            ContextPanel(entry.id, theme) {
                FieldLine("state", migrationStateLabel(entry.state), theme, stateStyle(entry.state, theme))
                FieldLine(
                    "executed",
                    entry.executedAtEpochMs?.let {
                        "${formatClock(it)} · ${formatAgo(now, it)} · took ${entry.durationMs?.let(::formatMs) ?: "—"}"
                    } ?: "not executed yet",
                    theme,
                )
                FieldLine("checksum db", entry.checksumDb ?: "—", theme, if (entry.checksumDrift) theme.error else null)
                FieldLine("checksum code", entry.checksumCode ?: "—", theme, if (entry.checksumDrift) theme.error else null)
                FieldLine("mode", migrationMode(entry), theme, if (entry.transactional == false) theme.warning else null)
                lastFailure?.let {
                    FieldLine("last failure", migrationFailureDetail(it), theme, theme.error)
                }
            }
        }
    }
}

private const val CONTEXT_ROWS = 8

private fun migrationColumns(failedIds: Set<String>): List<TableColumn<MigrationEntry>> = listOf(
    TableColumn("Migration", TableColumnWidth.Weight(1f)) { it.id },
    TableColumn("State", TableColumnWidth.Fixed(11)) { migrationStateLabel(it.state) },
    TableColumn("Took", TableColumnWidth.Fixed(8), TextAlign.RIGHT) { it.durationMs?.let(::formatMs) ?: "—" },
    TableColumn("At", TableColumnWidth.Fixed(9)) { it.executedAtEpochMs?.let(::formatClock) ?: "—" },
    TableColumn("Risk", TableColumnWidth.Fixed(8)) {
        when {
            it.checksumDrift -> "✗ drift"
            it.id in failedIds -> "✗ failed"
            else -> ""
        }
    },
)

internal fun migrationStateLabel(state: MigrationState): String = when (state) {
    MigrationState.APPLIED -> "applied"
    MigrationState.PENDING -> "pending"
    MigrationState.BASELINED -> "baselined"
    MigrationState.FILTERED -> "filtered"
    MigrationState.UNKNOWN_APPLIED -> "orphaned!"
}

internal fun migrationTallyLabel(raw: String): String =
    raw.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .replace('_', ' ')
        .lowercase()

internal fun migrationFailureAlert(failure: MigrationFailure): String =
    "✗ migration attempt failed — ${migrationFailureDetail(failure)}"

internal fun migrationFailureDetail(failure: MigrationFailure): String =
    failure.message?.takeIf { it.isNotBlank() }?.let { "${failure.id}: $it" } ?: failure.id

internal fun migrationMode(entry: MigrationEntry): String {
    val mode = when (entry.transactional) {
        true -> "transactional"
        false -> "NON-transactional — partial failure cannot roll back"
        null -> "source unavailable — execution mode unknown"
    }
    return mode + (entry.versionKey?.let { " · version $it" } ?: "")
}

private fun MigrationSnapshot.latestFailure(): MigrationFailure? = recentFailures.lastOrNull()

private fun stateStyle(state: MigrationState, theme: DispatchTheme) = when (state) {
    MigrationState.APPLIED -> theme.success
    MigrationState.PENDING -> theme.warning
    MigrationState.BASELINED -> theme.secondary
    MigrationState.FILTERED -> theme.muted
    MigrationState.UNKNOWN_APPLIED -> theme.error
}
