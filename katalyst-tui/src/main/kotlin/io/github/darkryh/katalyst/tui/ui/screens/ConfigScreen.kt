package io.github.darkryh.katalyst.tui.ui.screens

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.FilterableTable
import io.github.darkryh.dispatch.widget.TableColumn
import io.github.darkryh.dispatch.widget.TableColumnWidth
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.rememberTableState
import io.github.darkryh.katalyst.telemetry.model.ConfigEntry
import io.github.darkryh.katalyst.telemetry.model.EnvOutcome
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.github.darkryh.katalyst.tui.ui.ContextPanel
import io.github.darkryh.katalyst.tui.ui.FieldLine
import io.github.darkryh.katalyst.tui.ui.SubScreen
import io.github.darkryh.katalyst.tui.ui.subBodyRows

/**
 * Config: every resolved key with its masked value and provenance as the master table — "where
 * did this value actually come from?" answered by typing the key name. Missing required keys and
 * unresolved env placeholders are alert lines above the table; secrets stay masked, always.
 */
@Composable
fun ConfigScreen(snapshot: TelemetrySnapshot?, theme: DispatchTheme, onBack: () -> Unit) {
    val config = snapshot?.config ?: run {
        SectionMissing("Config", "The attached backend reported no configuration snapshot.", theme)
        return
    }
    val missing = config.requiredKeys.filter { !it.present }
    val emptyEnv = config.envLedger.filter { it.outcome == EnvOutcome.USED_EMPTY }

    SubScreen(
        title = "Config",
        tagline = "every resolved key, its value, and where it came from",
        stats = buildList {
            add("profile ${config.activeProfile ?: "default"}")
            add("keys ${config.totalKeys}")
            add(if (config.bindingActive) "binding ✓" else "binding off")
            if (missing.isNotEmpty()) add("✗ missing ${missing.size}")
            if (config.unresolvedEnvVars.isNotEmpty()) add("✗ unresolved ${config.unresolvedEnvVars.size}")
        },
        theme = theme,
    ) {
        var alertRows = 0
        if (missing.isNotEmpty()) {
            Text(
                "✗ required key(s) missing: ${missing.joinToString(", ") { it.key }}",
                style = theme.error,
                maxLines = 1,
            )
            alertRows++
        }
        if (config.unresolvedEnvVars.isNotEmpty()) {
            Text(
                "✗ unresolved env placeholder(s): ${config.unresolvedEnvVars.joinToString(", ")}",
                style = theme.error,
                maxLines = 1,
            )
            alertRows++
        }
        if (config.bindingErrors.isNotEmpty()) {
            Text("✗ binding: ${config.bindingErrors.first()}", style = theme.error, maxLines = 1)
            alertRows++
        }

        if (config.entries.isEmpty()) {
            ContextPanel("No configuration keys", theme) {
                Text("No configuration files were loaded and no keys are set.", style = theme.secondary)
            }
            return@SubScreen
        }

        val body = subBodyRows() - alertRows
        val showContext = body >= 15
        val visible = (body - 2 - (if (showContext) CONTEXT_ROWS else 0)).coerceIn(3, 14)

        val tableState = rememberTableState<ConfigEntry>()
        FilterableTable(
            items = config.entries.sortedBy { it.key },
            columns = configColumns(),
            onRowSelected = { /* provenance detail follows in the context card */ },
            onExit = onBack,
            visibleCount = visible,
            noResultsText = "no key matches the filter",
            state = tableState,
        )

        if (showContext) {
            val entry = tableState.selectedItem ?: config.entries.first()
            ContextPanel(entry.key, theme) {
                FieldLine(
                    "value",
                    entry.maskedValue + if (entry.masked) "  (masked — secrets never render)" else "",
                    theme,
                    if (entry.masked) theme.warning else theme.accent,
                )
                FieldLine("source", entry.provenance ?: "unknown", theme)
                FieldLine(
                    "files",
                    config.filesLoaded.entries.joinToString(" · ") { "${it.key} ${if (it.value) "✓" else "✗"}" }
                        .ifEmpty { "no config files" },
                    theme,
                )
                FieldLine(
                    "env ledger",
                    "${config.envLedger.count { it.outcome == EnvOutcome.FROM_ENV }} from env · " +
                        "${config.envLedger.count { it.outcome == EnvOutcome.USED_DEFAULT }} defaulted · " +
                        "${emptyEnv.size} empty",
                    theme,
                    if (emptyEnv.isNotEmpty()) theme.warning else null,
                )
                FieldLine(
                    "profile",
                    "${config.activeProfile ?: "default"} (selected by ${config.profileSource ?: "default"})",
                    theme,
                )
            }
        }
    }
}

private const val CONTEXT_ROWS = 7

private fun configColumns(): List<TableColumn<ConfigEntry>> = listOf(
    TableColumn("Key", TableColumnWidth.Weight(1f)) { it.key },
    TableColumn("Value", TableColumnWidth.Fixed(26)) { it.maskedValue },
    TableColumn("Source", TableColumnWidth.Fixed(15)) { it.provenance ?: "?" },
)
