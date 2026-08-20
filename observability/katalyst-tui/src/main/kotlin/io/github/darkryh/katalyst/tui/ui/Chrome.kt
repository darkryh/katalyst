package io.github.darkryh.katalyst.tui.ui

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Arrangement
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.weight
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.ChipRow
import io.github.darkryh.dispatch.widget.LinearProgressIndicator
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.katalyst.telemetry.model.HealthLevel
import io.github.darkryh.katalyst.tui.viewmodel.AttachStatus
import io.github.darkryh.katalyst.tui.viewmodel.InspectorUiState

/**
 * Cross-cutting top chrome shown above every screen (below the banner). Pure function of the
 * inspector state + theme:
 *  - an attach indicator (Discovering / Attached / Detached),
 *  - a health strip of chips (container/engine, boot phase, health level, host:port).
 * The JVM heap gauge deliberately lives in the [BottomBar], not here.
 */
@Composable
fun Chrome(state: InspectorUiState, theme: DispatchTheme) {
    val health = state.snapshot?.health

    val attachText = when (val attach = state.attach) {
        AttachStatus.Discovering -> "Discovering…"
        is AttachStatus.Attached -> "Attached  ${attach.descriptor.appName}  pid ${attach.descriptor.pid}"
        AttachStatus.Detached -> state.lastError ?: "Detached — no backend"
    }
    val attachStyle = when (state.attach) {
        AttachStatus.Discovering -> theme.warning
        is AttachStatus.Attached -> theme.success
        AttachStatus.Detached -> theme.error
    }

    val chips = buildList {
        add(if (health?.containerReady == true) "container ready" else "container down")
        health?.activeEngineId?.let { add("engine $it") }
        add(
            when {
                health == null -> "boot ?"
                health.bootComplete -> "boot complete"
                health.bootFailedPhase != null -> "boot failed: ${health.bootFailedPhase}"
                else -> "boot in progress"
            },
        )
        add("health ${health?.level?.name ?: "?"}")
        if (health != null && (health.criticalCount > 0 || health.warningCount > 0)) {
            add("crit ${health.criticalCount} / warn ${health.warningCount}")
        }
        state.selected?.let { add("${it.host}:${it.telemetryPort}") }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Katalyst Inspector", style = theme.primary)
            Text("   ", style = theme.muted)
            Text(attachText, style = attachStyle, modifier = Modifier.weight(1f))
            Text(healthGlyph(health?.level), style = attachStyle)
        }
        ChipRow(tags = chips)
    }
}

private fun healthGlyph(level: HealthLevel?): String = when (level) {
    HealthLevel.OK -> "●"
    HealthLevel.DEGRADED -> "◐"
    HealthLevel.ERROR -> "○"
    null -> "·"
}
