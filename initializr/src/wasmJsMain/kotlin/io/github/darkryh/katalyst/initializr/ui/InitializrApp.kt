package io.github.darkryh.katalyst.initializr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkryh.katalyst.initializr.generate.ProjectGenerator
import io.github.darkryh.katalyst.initializr.model.ConfigField
import io.github.darkryh.katalyst.initializr.model.FormState
import io.github.darkryh.katalyst.initializr.model.ProjectConfigValidator
import io.github.darkryh.katalyst.initializr.model.toProjectConfig
import io.github.darkryh.katalyst.initializr.platform.downloadZip
import io.github.darkryh.katalyst.initializr.platform.prefersDark
import io.github.darkryh.katalyst.initializr.template.StarterTemplate
import io.github.darkryh.katalyst.initializr.zip.ZipArchive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InitializrApp() {
    var dark by remember { mutableStateOf(prefersDark()) }
    val pal = if (dark) DarkPalette else LightPalette

    CompositionLocalProvider(
        LocalPalette provides pal,
        LocalMono provides FontFamily.Monospace,
    ) {
        var form by remember { mutableStateOf(FormState.DEFAULT) }
        var activeTab by remember { mutableStateOf(Tab.APPLICATION) }
        var generating by remember { mutableStateOf(false) }
        var finished by remember { mutableStateOf(false) }
        val log = remember { mutableStateListOf<String>() }
        val scope = rememberCoroutineScope()

        val config = form.toProjectConfig()
        val errors = ProjectConfigValidator.validate(form)
        val valid = errors.isEmpty()
        val files = ProjectGenerator.generate(config)

        fun generate() {
            if (generating || !valid) return
            generating = true
            finished = false
            log.clear()
            scope.launch {
                val generated = ProjectGenerator.generate(config)
                val zip = ZipArchive.create(generated)
                val name = ProjectGenerator.zipName(config)
                log.add("❯ katalyst :generate")
                val perLine = (650 / generated.size.coerceAtLeast(1)).coerceIn(20, 90).toLong()
                for (path in generated.map { it.path }.sorted()) {
                    log.add("✓ $path")
                    delay(perLine)
                }
                log.add("✓ $name · ${generated.size} files — downloading")
                delay(180)
                downloadZip(name, zip)
                finished = true
            }
        }

        Column(Modifier.fillMaxSize().background(pal.ground)) {
            TopBar(dark = dark, onToggleTheme = { dark = !dark })
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 1220.dp).padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 1220.dp).fillMaxWidth()) {
                        Lede()
                        VGap(6)
                        Console(
                            form = form,
                            config = config,
                            errors = errors,
                            valid = valid,
                            files = files,
                            activeTab = activeTab,
                            generating = generating,
                            finished = finished,
                            log = log,
                            onForm = { form = it },
                            onTab = { activeTab = it },
                            onReconfigure = { generating = false; finished = false; log.clear() },
                        )
                        VGap(90)
                    }
                }
            }
            ActionBar(
                config = config,
                errors = errors,
                valid = valid,
                fileCount = files.size,
                generating = generating,
                onExplore = { activeTab = Tab.TREE },
                onGenerate = { generate() },
            )
        }
    }
}

@Composable
private fun Console(
    form: FormState,
    config: io.github.darkryh.katalyst.initializr.model.ProjectConfig,
    errors: List<io.github.darkryh.katalyst.initializr.model.FieldError>,
    valid: Boolean,
    files: List<io.github.darkryh.katalyst.initializr.template.TemplateFile>,
    activeTab: Tab,
    generating: Boolean,
    finished: Boolean,
    log: List<String>,
    onForm: (FormState) -> Unit,
    onTab: (Tab) -> Unit,
    onReconfigure: () -> Unit,
) {
    val overlay: (@Composable () -> Unit)? =
        if (generating) {
            { BuildLog(log = log, finished = finished, onReconfigure = onReconfigure) }
        } else {
            null
        }

    @Composable
    fun left() {
        IdentityCard(form = form, config = config, errors = errors, onForm = onForm)
        VGap(20)
        EngineCard(selection = form.selection, onSelect = { onForm(form.copy(selection = form.selection.withEngine(it))) })
        VGap(20)
        FeaturesCard(selection = form.selection, onSelection = { onForm(form.copy(selection = it)) })
    }

    @Composable
    fun right() {
        AssemblyPane(
            config = config,
            valid = valid,
            files = files,
            activeTab = activeTab,
            buildOverlay = overlay,
            onTab = onTab,
        )
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 940.dp) {
            Column {
                left()
                VGap(20)
                right()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) { left() }
                Column(Modifier.weight(1.28f)) { right() }
            }
        }
    }
}

@Composable
private fun BuildLog(log: List<String>, finished: Boolean, onReconfigure: () -> Unit) {
    val p = palette()
    Column {
        log.forEach { line ->
            val color = if (line.startsWith("✓")) p.good else p.accent
            Text(line, color = color, fontSize = 12.5.sp, fontFamily = LocalMono.current, lineHeight = 21.sp)
        }
        if (finished) {
            VGap(12)
            GhostButton("↺ Reconfigure", onClick = onReconfigure)
        }
    }
}

@Composable
private fun TopBar(dark: Boolean, onToggleTheme: () -> Unit) {
    val p = palette()
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.surface)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⬢", color = p.accent, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Row {
            Text("K", color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text("ATALYST", color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text("/ new project", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Pill("1.0.0-alpha03", p.accent, p.accentSoft, p.accent.copy(alpha = 0.35f))
        Spacer(Modifier.width(12.dp))
        GhostButton(if (dark) "☀ Light" else "☾ Dark", onClick = onToggleTheme)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
}

@Composable
private fun Lede() {
    val p = palette()
    Column(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 16.dp)) {
        Text("COMPOSE YOUR BACKEND", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        VGap(8)
        Text(
            "Pick your starters — Katalyst wires the rest at boot.",
            color = p.ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        VGap(6)
        Text(
            "Everything is on by default and validated as you type — the same fail-fast discipline Katalyst " +
                "applies at startup. Toggle a feature and the dependency graph re-forms: that graph is exactly " +
                "what Katalyst discovers, validates, and injects when your service starts.",
            color = p.ink2,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun ActionBar(
    config: io.github.darkryh.katalyst.initializr.model.ProjectConfig,
    errors: List<io.github.darkryh.katalyst.initializr.model.FieldError>,
    valid: Boolean,
    fileCount: Int,
    generating: Boolean,
    onExplore: () -> Unit,
    onGenerate: () -> Unit,
) {
    val p = palette()
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.surface)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "${config.groupId}:${config.artifactId} · ${config.appVersion}",
                color = p.ink,
                fontSize = 13.sp,
                fontFamily = LocalMono.current,
            )
            VGap(2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(7.dp).height(7.dp).background(if (valid) p.good else p.bad, RoundedCornerShape(100.dp)))
                Spacer(Modifier.width(7.dp))
                val statusText =
                    if (valid) {
                        "4 checks passed · $fileCount files · katalyst ${StarterTemplate.KATALYST_VERSION}"
                    } else {
                        "${errors.size} to fix — " + errors.joinToString(", ") { labelOf(it.field) }
                    }
                Text(statusText, color = if (valid) p.good else p.bad, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        GhostButton("Explore files", onClick = onExplore)
        Spacer(Modifier.width(12.dp))
        PrimaryButton(
            text = if (valid) "Generate & download  ↓" else "Fix to generate",
            enabled = valid && !generating,
            onClick = onGenerate,
        )
    }
}

private fun labelOf(field: ConfigField): String =
    when (field) {
        ConfigField.PROJECT_NAME -> "name"
        ConfigField.PACKAGE_NAME -> "package"
        ConfigField.APP_VERSION -> "version"
        ConfigField.GROUP_ID -> "group id"
        ConfigField.ARTIFACT_ID -> "artifact id"
    }
