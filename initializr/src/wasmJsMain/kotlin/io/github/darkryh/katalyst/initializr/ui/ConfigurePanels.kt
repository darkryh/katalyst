package io.github.darkryh.katalyst.initializr.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkryh.katalyst.initializr.model.ConfigField
import io.github.darkryh.katalyst.initializr.model.Engine
import io.github.darkryh.katalyst.initializr.model.Feature
import io.github.darkryh.katalyst.initializr.model.FeatureSelection
import io.github.darkryh.katalyst.initializr.model.FieldError
import io.github.darkryh.katalyst.initializr.model.FormState
import io.github.darkryh.katalyst.initializr.model.ProjectConfig
import io.github.darkryh.katalyst.initializr.model.deriveArtifactId
import io.github.darkryh.katalyst.initializr.model.deriveGroupId

private data class Preset(val name: String, val caption: String, val features: Set<Feature>)

private val PRESETS =
    listOf(
        Preset("Minimal", "web only", emptySet()),
        Preset("Standard", "data + insight", setOf(Feature.PERSISTENCE, Feature.MIGRATIONS, Feature.OBSERVABILITY)),
        Preset("Full-stack", "everything", Feature.entries.toSet()),
    )

@Composable
fun IdentityCard(
    form: FormState,
    config: ProjectConfig,
    errors: List<FieldError>,
    onForm: (FormState) -> Unit,
) {
    val p = palette()
    fun errorFor(field: ConfigField) = errors.firstOrNull { it.field == field }?.message

    SectionCard(index = "01", title = "Identity", hint = "coordinates & package") {
        FieldRow(
            label = "Project name",
            value = form.projectName,
            onValueChange = { onForm(form.copy(projectName = it)) },
            error = errorFor(ConfigField.PROJECT_NAME),
        )
        VGap(11)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                FieldRow(
                    label = "Package",
                    value = form.packageName,
                    onValueChange = { onForm(form.copy(packageName = it)) },
                    error = errorFor(ConfigField.PACKAGE_NAME),
                    mono = true,
                )
            }
            Box(Modifier.weight(1f)) {
                FieldRow(
                    label = "Version",
                    value = form.appVersion,
                    onValueChange = { onForm(form.copy(appVersion = it)) },
                    error = errorFor(ConfigField.APP_VERSION),
                    mono = true,
                )
            }
        }
        VGap(8)
        Text(
            "↳ group ${config.groupId.ifEmpty { "—" }} · artifact ${config.artifactId.ifEmpty { "—" }}",
            color = p.muted,
            fontSize = 12.sp,
            fontFamily = LocalMono.current,
        )

        VGap(10)
        val advOpen = form.advancedOpen
        Text(
            text = "${if (advOpen) "▾" else "▸"}  Advanced — override group & artifact id",
            color = p.ink2,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { onForm(form.copy(advancedOpen = !advOpen)) },
        )
        AnimatedVisibility(visible = advOpen) {
            Column {
                VGap(10)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        FieldRow(
                            label = "Group id",
                            value = form.groupIdOverride ?: "",
                            onValueChange = { onForm(form.copy(groupIdOverride = it.ifEmpty { null })) },
                            error = errorFor(ConfigField.GROUP_ID),
                            mono = true,
                            placeholder = deriveGroupId(form.packageName),
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        FieldRow(
                            label = "Artifact id",
                            value = form.artifactIdOverride ?: "",
                            onValueChange = { onForm(form.copy(artifactIdOverride = it.ifEmpty { null })) },
                            error = errorFor(ConfigField.ARTIFACT_ID),
                            mono = true,
                            placeholder = deriveArtifactId(form.projectName, form.packageName),
                        )
                    }
                }
            }
        }

        VGap(14)
        ValidationStrip(errors)
    }
}

@Composable
private fun ValidationStrip(errors: List<FieldError>) {
    val p = palette()
    fun bad(vararg fields: ConfigField) = errors.any { it.field in fields }
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("VALIDATION", color = p.muted, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
        vchip("name", bad(ConfigField.PROJECT_NAME), p)
        vchip("package", bad(ConfigField.PACKAGE_NAME), p)
        vchip("version", bad(ConfigField.APP_VERSION), p)
        vchip("coordinates", bad(ConfigField.GROUP_ID, ConfigField.ARTIFACT_ID), p)
    }
}

@Composable
private fun vchip(label: String, bad: Boolean, p: Palette) {
    Pill(
        text = (if (bad) "✗ " else "✓ ") + label,
        fg = if (bad) p.bad else p.good,
        bg = if (bad) p.badSoft else p.goodSoft,
        border = (if (bad) p.bad else p.good).copy(alpha = 0.4f),
    )
}

@Composable
fun EngineCard(
    selection: FeatureSelection,
    onSelect: (Engine) -> Unit,
) {
    val p = palette()
    SectionCard(index = "02", title = "Server engine", hint = "exactly one") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Engine.entries.forEach { engine ->
                val on = selection.engine == engine
                Column(
                    Modifier
                        .weight(1f)
                        .background(if (on) p.accentSoft else p.well, RoundedCornerShape(7.dp))
                        .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(7.dp))
                        .clickable { onSelect(engine) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    Text(engine.id, color = if (on) p.accent else p.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    VGap(2)
                    Text(engine.summary, color = p.muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun FeaturesCard(
    selection: FeatureSelection,
    onSelection: (FeatureSelection) -> Unit,
) {
    val p = palette()
    val onCount = selection.enabled.size
    SectionCard(
        index = "03",
        title = "Feature starters",
        hint = "",
        trailing = { Text("$onCount of ${Feature.entries.size} on", color = p.muted, fontSize = 12.sp) },
    ) {
        // Presets.
        val active = PRESETS.firstOrNull { it.features == selection.features }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PRESETS.forEach { preset ->
                val on = active == preset
                Box(
                    Modifier
                        .background(if (on) p.accent else p.well, RoundedCornerShape(100.dp))
                        .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(100.dp))
                        .clickable { onSelection(selection.copy(features = preset.features)) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(preset.name, color = if (on) p.onAccent else p.ink2, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                        Spacer(Modifier.width(5.dp))
                        Text(preset.caption, color = if (on) p.onAccent.copy(alpha = 0.8f) else p.muted, fontSize = 10.5.sp, fontFamily = LocalMono.current)
                    }
                }
            }
            if (active == null) {
                Box(
                    Modifier.background(p.accent, RoundedCornerShape(100.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("Custom", color = p.onAccent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        VGap(14)

        // Core (always-on) card.
        CoreRow()
        VGap(10)
        // Feature toggle cards, two per row.
        Feature.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { f ->
                    Box(Modifier.weight(1f)) {
                        FeatureToggle(
                            feature = f,
                            selection = selection,
                            onToggle = { onSelection(selection.toggled(f)) },
                        )
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f)) {}
            }
            VGap(10)
        }
    }
}

@Composable
private fun CoreRow() {
    val p = palette()
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.well, RoundedCornerShape(7.dp))
            .border(1.dp, p.line, RoundedCornerShape(7.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwitchGlyph(on = true, locked = true)
        Spacer(Modifier.width(9.dp))
        Column {
            Text("Web + config + events", color = p.ink2, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text("REST routing, YAML config, event bus — the core runtime.", color = p.muted, fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun FeatureToggle(
    feature: Feature,
    selection: FeatureSelection,
    onToggle: () -> Unit,
) {
    val p = palette()
    val blocked = feature.requires != null && !selection.isEnabled(feature.requires)
    val on = selection.isEnabled(feature)
    val border = if (on) p.accent else p.line
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (on) p.surface else p.well, RoundedCornerShape(7.dp))
            .border(1.dp, border, RoundedCornerShape(7.dp))
            .then(if (blocked) Modifier else Modifier.clickable { onToggle() })
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SwitchGlyph(on = on, locked = false)
            Spacer(Modifier.width(9.dp))
            Text(feature.display, color = if (on) p.ink else p.ink2, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            if (feature.requires != null) {
                Spacer(Modifier.weight(1f))
                Text("needs ${feature.requires!!.id}", color = p.amber, fontSize = 10.sp)
            }
        }
        VGap(5)
        Text(featureDesc(feature), color = p.ink2, fontSize = 11.5.sp)
        VGap(6)
        Text("…-${feature.starter.removePrefix("katalyst-starter-")}", color = p.muted, fontSize = 10.5.sp, fontFamily = LocalMono.current)
    }
}

@Composable
private fun SwitchGlyph(on: Boolean, locked: Boolean) {
    val p = palette()
    val track = if (on) (if (locked) p.accent.copy(alpha = 0.35f) else p.accent) else p.line
    Box(
        Modifier.width(34.dp).height(19.dp).background(track, RoundedCornerShape(100.dp)),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .width(15.dp)
                .height(15.dp)
                .background(if (on && locked) p.accent else p.surface, RoundedCornerShape(100.dp)),
        )
    }
}

private fun featureDesc(f: Feature): String =
    when (f) {
        Feature.PERSISTENCE -> "Exposed + HikariCP, repositories by interface."
        Feature.MIGRATIONS -> "Versioned, checksum-verified schema changes."
        Feature.SCHEDULER -> "Cron & fixed-rate background jobs."
        Feature.WEBSOCKETS -> "Live sockets with built-in instrumentation."
        Feature.OBSERVABILITY -> "Telemetry + embedded TUI inspector (adds run.sh)."
    }
