package io.github.darkryh.katalyst.initializr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkryh.katalyst.initializr.model.Capability
import io.github.darkryh.katalyst.initializr.model.ConfigField
import io.github.darkryh.katalyst.initializr.model.Engine
import io.github.darkryh.katalyst.initializr.model.Feature
import io.github.darkryh.katalyst.initializr.model.FeatureSelection
import io.github.darkryh.katalyst.initializr.model.FieldError
import io.github.darkryh.katalyst.initializr.model.FormState
import io.github.darkryh.katalyst.initializr.model.deriveArtifactId
import io.github.darkryh.katalyst.initializr.model.deriveGroupId
import io.github.darkryh.katalyst.initializr.model.isCapabilityOn
import io.github.darkryh.katalyst.initializr.model.toggleCapability

// ---------------- simple screen: capability cards ----------------

@Composable
fun CapabilityGrid(
    selection: FeatureSelection,
    onToggle: (Capability) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Capability.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { cap ->
                    Box(Modifier.weight(1f)) {
                        CapabilityCard(cap, selection.isCapabilityOn(cap)) { onToggle(cap) }
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f)) {}
            }
        }
    }
}

@Composable
private fun CapabilityCard(capability: Capability, on: Boolean, onToggle: () -> Unit) {
    val p = palette()
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (on) p.accentSoft else p.surface, RoundedCornerShape(14.dp))
            .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(38.dp).background(if (on) p.accent else p.well, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            KIcon(capIcon(capability), 20.dp, if (on) p.onAccent else p.ink2)
        }
        HGap(13)
        Column(Modifier.weight(1f)) {
            Text(capability.title, color = p.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            VGap(2)
            Text(capability.summary, color = p.muted, fontSize = 12.5.sp)
        }
        HGap(10)
        Box(
            Modifier
                .size(20.dp)
                .background(if (on) p.accent else p.surface, RoundedCornerShape(7.dp))
                .border(1.5.dp, if (on) p.accent else p.line, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (on) KIcon(KIcons.Check, 13.dp, p.onAccent)
        }
    }
}

private fun capIcon(capability: Capability): IconSpec = when (capability) {
    Capability.DATABASE -> KIcons.Database
    Capability.SCHEDULED_JOBS -> KIcons.Clock
    Capability.REALTIME -> KIcons.Broadcast
    Capability.MONITORING -> KIcons.Bars
}

// ---------------- advanced: engine ----------------

@Composable
fun EnginePanel(selected: Engine, onSelect: (Engine) -> Unit) {
    val p = palette()
    Panel(icon = KIcons.ServerPanel, title = "Server engine", sub = "pick exactly one") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Engine.entries.forEach { engine ->
                val on = engine == selected
                Column(
                    Modifier
                        .weight(1f)
                        .background(if (on) p.accentSoft else p.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(12.dp))
                        .clickable { onSelect(engine) }
                        .padding(13.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        KIcon(KIcons.Server, 22.dp, if (on) p.accent else p.ink2)
                        Spacer(Modifier.weight(1f))
                        if (on) KIcon(KIcons.CheckBold, 16.dp, p.accent)
                    }
                    VGap(9)
                    Text(engineName(engine), color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    VGap(2)
                    Text(engine.summary, color = p.muted, fontSize = 11.5.sp, fontFamily = LocalMono.current)
                }
            }
        }
    }
}

private fun engineName(engine: Engine): String = if (engine == Engine.CIO) "CIO" else engine.id.replaceFirstChar { it.uppercase() }

// ---------------- advanced: fine-grained starters ----------------

@Composable
fun StartersPanel(selection: FeatureSelection, onSelection: (FeatureSelection) -> Unit) {
    Panel(icon = KIcons.Sliders, title = "Starters", sub = "stay in sync with the cards above") {
        val persistenceOn = Feature.PERSISTENCE in selection.features
        StarterRow(
            title = "Persistence",
            desc = featureDesc(Feature.PERSISTENCE),
            checked = persistenceOn,
            enabled = true,
            child = false,
            showRequires = false,
            first = true,
        ) { onSelection(selection.toggled(Feature.PERSISTENCE)) }
        StarterRow(
            title = "Migrations",
            desc = featureDesc(Feature.MIGRATIONS),
            checked = Feature.MIGRATIONS in selection.features,
            enabled = persistenceOn,
            child = true,
            showRequires = !persistenceOn,
            first = false,
        ) { onSelection(selection.toggled(Feature.MIGRATIONS)) }
        StarterRow("Scheduler", featureDesc(Feature.SCHEDULER), Feature.SCHEDULER in selection.features, true, false, false, false) {
            onSelection(selection.toggled(Feature.SCHEDULER))
        }
        StarterRow("WebSockets", featureDesc(Feature.WEBSOCKETS), Feature.WEBSOCKETS in selection.features, true, false, false, false) {
            onSelection(selection.toggled(Feature.WEBSOCKETS))
        }
        StarterRow("Observability", featureDesc(Feature.OBSERVABILITY), Feature.OBSERVABILITY in selection.features, true, false, false, false) {
            onSelection(selection.toggled(Feature.OBSERVABILITY))
        }
    }
}

@Composable
private fun StarterRow(
    title: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean,
    child: Boolean,
    showRequires: Boolean,
    first: Boolean,
    onToggle: () -> Unit,
) {
    val p = palette()
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row(
            Modifier.fillMaxWidth().padding(start = if (child) 22.dp else 0.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                VGap(1)
                Text(desc, color = p.muted, fontSize = 12.sp)
                if (showRequires) {
                    VGap(5)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KIcon(KIcons.WarnTriangle, 13.dp, p.amber)
                        Text("Requires Persistence", color = p.amber, fontSize = 11.5.sp)
                    }
                }
            }
            HGap(14)
            ToggleSwitch(checked = checked && enabled, enabled = enabled, onToggle = onToggle)
        }
    }
}

private fun featureDesc(feature: Feature): String = when (feature) {
    Feature.PERSISTENCE -> "Exposed + HikariCP; repositories by interface."
    Feature.MIGRATIONS -> "Versioned, checksum-verified schema changes."
    Feature.SCHEDULER -> "Cron & fixed-rate background jobs."
    Feature.WEBSOCKETS -> "Live sockets with built-in instrumentation."
    Feature.OBSERVABILITY -> "Telemetry + embedded TUI inspector (adds run.sh)."
}

// ---------------- advanced: coordinates ----------------

@Composable
fun CoordinatesPanel(
    form: FormState,
    errors: List<FieldError>,
    onForm: (FormState) -> Unit,
) {
    fun errorFor(field: ConfigField) = errors.firstOrNull { it.field == field }?.message
    Panel(icon = KIcons.Lines, title = "Coordinates", sub = "group & artifact are derived") {
        KField(
            label = "Package",
            value = form.packageName,
            onValueChange = { onForm(form.copy(packageName = it)) },
            error = errorFor(ConfigField.PACKAGE_NAME),
            mono = true,
        )
        VGap(14)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1f)) {
                KField(
                    label = "Group id",
                    value = form.groupIdOverride ?: "",
                    onValueChange = { onForm(form.copy(groupIdOverride = it.ifEmpty { null })) },
                    error = errorFor(ConfigField.GROUP_ID),
                    mono = true,
                    placeholder = deriveGroupId(form.packageName),
                    labelTrailing = if (form.groupIdOverride == null) ({ AutoPill() }) else null,
                )
            }
            Box(Modifier.weight(1f)) {
                KField(
                    label = "Artifact id",
                    value = form.artifactIdOverride ?: "",
                    onValueChange = { onForm(form.copy(artifactIdOverride = it.ifEmpty { null })) },
                    error = errorFor(ConfigField.ARTIFACT_ID),
                    mono = true,
                    placeholder = deriveArtifactId(form.projectName, form.packageName),
                    labelTrailing = if (form.artifactIdOverride == null) ({ AutoPill() }) else null,
                )
            }
        }
        VGap(14)
        Box(Modifier.fillMaxWidth(0.5f)) {
            KField(
                label = "Version",
                value = form.appVersion,
                onValueChange = { onForm(form.copy(appVersion = it)) },
                error = errorFor(ConfigField.APP_VERSION),
                mono = true,
            )
        }
    }
}

@Composable
private fun AutoPill() {
    val p = palette()
    Row(
        Modifier.background(p.well, RoundedCornerShape(999.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KIcon(KIcons.Refresh, 11.dp, p.muted)
        Text("auto", color = p.muted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------- advanced: toolchain ----------------

@Composable
fun ToolchainPanel(katalystVersion: String) {
    Panel(icon = KIcons.Tag, title = "Toolchain", sub = "read-only") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Chip("Katalyst", katalystVersion)
            Chip("JDK", "21")
            Chip("Kotlin", "2.4.0")
        }
        VGap(9)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Chip("Ktor", "3.5.0")
            Chip("Gradle", "9.5.0")
        }
    }
}
