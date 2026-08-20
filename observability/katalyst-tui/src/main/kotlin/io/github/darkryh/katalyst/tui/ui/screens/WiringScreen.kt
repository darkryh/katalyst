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
import io.github.darkryh.katalyst.telemetry.model.ValidationErrorEntry
import io.github.darkryh.katalyst.tui.ui.ContextPanel
import io.github.darkryh.katalyst.tui.ui.FieldLine
import io.github.darkryh.katalyst.tui.ui.SubScreen
import io.github.darkryh.katalyst.tui.ui.subBodyRows

/**
 * Wiring: the dependency graph made searchable. Healthy container → the master table is every
 * bean in instantiation order (type-to-filter finds YOUR bean instantly) with a discovery summary
 * card. Broken container → the master table becomes the validation errors, and the context card
 * shows the highlighted error's suggestion and cycle path — the fix, not just the failure.
 */
@Composable
fun WiringScreen(snapshot: TelemetrySnapshot?, theme: DispatchTheme, onBack: () -> Unit) {
    val wiring = snapshot?.wiring ?: run {
        SectionMissing("Wiring", "Dependency-graph analysis did not run on the attached backend.", theme)
        return
    }
    val broken = wiring.validationErrors.isNotEmpty()

    SubScreen(
        title = "Wiring",
        tagline = if (broken) "the container is invalid — errors first, each with its suggested fix"
        else "every bean in instantiation order — type to find yours",
        stats = buildList {
            add("nodes ${wiring.nodeCount}")
            add("edges ${wiring.edgeCount}")
            add(if (wiring.isValid) "valid ✓" else "✗ ${wiring.totalErrors} errors")
            add("secondary ${wiring.secondaryBindings}")
            wiring.analysisMs?.let { add("analysis ${it}ms") }
        },
        theme = theme,
    ) {
        val body = subBodyRows()
        val showContext = body >= 15
        val visible = (body - 2 - (if (showContext) CONTEXT_ROWS else 0)).coerceIn(3, 14)

        if (broken) {
            val tableState = rememberTableState<ValidationErrorEntry>()
            FilterableTable(
                items = wiring.validationErrors,
                columns = errorColumns(),
                onRowSelected = { /* fix guidance follows in the context card */ },
                onExit = onBack,
                visibleCount = visible,
                noResultsText = "no error matches the filter",
                state = tableState,
            )
            if (showContext) {
                val error = tableState.selectedItem ?: wiring.validationErrors.first()
                ContextPanel(error.kind, theme) {
                    FieldLine("component", error.component, theme)
                    FieldLine("needs", error.requiredType ?: "—", theme)
                    FieldLine("message", error.message, theme, theme.error)
                    FieldLine("fix", error.suggestion ?: "no suggestion available", theme, theme.success)
                    FieldLine(
                        "cycle",
                        if (error.cyclePath.isEmpty()) "—" else error.cyclePath.joinToString(" → "),
                        theme,
                    )
                }
            }
            return@SubScreen
        }

        // A healthy container with NO analysis output is its own state — an empty table with
        // placeholder dashes would read as "zero beans", which is not what happened.
        if (wiring.instantiationOrder.isEmpty()) {
            val registries = wiring.registries
            ContextPanel("No graph analysis reported", theme) {
                Text("The container is valid, but no instantiation-order analysis arrived.", style = theme.secondary)
                Text("Graph analysis runs at boot when the analyzer is enabled for the app.", style = theme.muted)
                FieldLine(
                    "container",
                    registries?.let {
                        "${if (it.containerReady) "ready" else "NOT READY"} · engine ${it.activeEngineId ?: "?"}" +
                            " · features ${it.features.size}"
                    } ?: "live registry counters not instrumented yet",
                    theme,
                    if (registries?.containerReady == false) theme.error else null,
                )
            }
            return@SubScreen
        }

        val beans = wiring.instantiationOrder.mapIndexed { index, fqn -> BeanRow(index + 1, fqn) }
        FilterableTable(
            items = beans,
            columns = beanColumns(),
            onRowSelected = { /* order + name is the whole story for a healthy bean */ },
            onExit = onBack,
            visibleCount = visible,
            noResultsText = "no bean matches the filter",
            state = rememberTableState<BeanRow>(),
        )
        if (showContext) {
            val discovery = wiring.discovery
            val registries = wiring.registries
            ContextPanel("Discovery & container", theme) {
                FieldLine(
                    "discovered",
                    discovery?.let { "${it.totalTypes} types across ${it.scans.size} scans" } ?: "—",
                    theme,
                )
                FieldLine(
                    "categories",
                    discovery?.perCategoryCounts?.entries?.joinToString(" · ") { "${it.key} ${it.value}" } ?: "—",
                    theme,
                )
                val slowest = discovery?.scans?.maxByOrNull { it.durationMs }
                FieldLine(
                    "slowest scan",
                    slowest?.let { "${it.baseType} — ${it.durationMs}ms, ${it.matchCount} matches" } ?: "—",
                    theme,
                )
                FieldLine(
                    "found nothing",
                    discovery?.emptyDiscoveries?.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "every scan matched",
                    theme,
                )
                FieldLine(
                    "container",
                    registries?.let {
                        "${if (it.containerReady) "ready" else "NOT READY"} · engine ${it.activeEngineId ?: "?"} · features ${it.features.size}"
                    } ?: "live registry counters not instrumented yet",
                    theme,
                    if (registries?.containerReady == false) theme.error else null,
                )
            }
        }
    }
}

private const val CONTEXT_ROWS = 7

private data class BeanRow(val position: Int, val fqn: String)

private fun beanColumns(): List<TableColumn<BeanRow>> = listOf(
    TableColumn("#", TableColumnWidth.Fixed(4), TextAlign.RIGHT) { it.position.toString() },
    TableColumn("Bean (instantiation order)", TableColumnWidth.Weight(1f)) { it.fqn },
)

private fun errorColumns(): List<TableColumn<ValidationErrorEntry>> = listOf(
    TableColumn("Kind", TableColumnWidth.Fixed(16)) { it.kind },
    TableColumn("Component", TableColumnWidth.Weight(1f)) { it.component },
    TableColumn("Message", TableColumnWidth.Weight(1f)) { it.message },
)
