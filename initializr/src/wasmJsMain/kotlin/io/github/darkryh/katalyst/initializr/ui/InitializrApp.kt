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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkryh.katalyst.initializr.generate.ProjectGenerator
import io.github.darkryh.katalyst.initializr.model.ProjectConfigValidator
import io.github.darkryh.katalyst.initializr.model.FormState
import io.github.darkryh.katalyst.initializr.model.toProjectConfig
import io.github.darkryh.katalyst.initializr.model.toggleCapability
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
        var activeTab by remember { mutableStateOf(Tab.APP) }
        var generating by remember { mutableStateOf(false) }
        var toast by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        val config = form.toProjectConfig()
        val errors = ProjectConfigValidator.validate(form)
        val valid = errors.isEmpty()
        val files = ProjectGenerator.generate(config)

        fun errorFor(field: io.github.darkryh.katalyst.initializr.model.ConfigField) =
            errors.firstOrNull { it.field == field }?.message

        fun generate() {
            if (generating || !valid) return
            generating = true
            scope.launch {
                val generated = ProjectGenerator.generate(config)
                val zip = ZipArchive.create(generated)
                val name = ProjectGenerator.zipName(config)
                delay(480)
                downloadZip(name, zip)
                toast = name
                generating = false
            }
        }

        LaunchedEffect(toast) {
            if (toast != null) {
                delay(3600)
                toast = null
            }
        }

        val p = palette()
        Box(Modifier.fillMaxSize().background(p.ground)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                    TopBar(dark = dark, onToggleTheme = { dark = !dark })

                    VGap(38)
                    Eyebrow("New service")
                    VGap(18)
                    Text("What are you building?", color = p.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 37.sp)
                    VGap(8)
                    Text(
                        "Name your service and pick what it should do. Katalyst wires the rest — no annotations, " +
                            "one bootstrap block, fail-fast at startup.",
                        color = p.ink2,
                        fontSize = 15.5.sp,
                        lineHeight = 23.sp,
                    )
                    VGap(30)

                    KField(
                        label = "Project name",
                        value = form.projectName,
                        onValueChange = { form = form.copy(projectName = it) },
                        error = errorFor(io.github.darkryh.katalyst.initializr.model.ConfigField.PROJECT_NAME),
                        big = true,
                        placeholder = "My Katalyst App",
                    )
                    VGap(14)
                    KField(
                        label = "Package",
                        value = form.packageName,
                        onValueChange = { form = form.copy(packageName = it) },
                        error = errorFor(io.github.darkryh.katalyst.initializr.model.ConfigField.PACKAGE_NAME),
                        big = true,
                        mono = true,
                        placeholder = "com.example.app",
                    )
                    VGap(6)
                    CoreNote()
                    VGap(30)

                    Text("What should it do?", color = p.ink2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    VGap(13)
                    CapabilityGrid(
                        selection = form.selection,
                        onToggle = { form = form.copy(selection = form.selection.toggleCapability(it)) },
                    )
                    VGap(30)

                    GenerateButton(
                        label = if (generating) "Generating…" else if (valid) "Generate project" else "Fix errors to generate",
                        enabled = valid,
                        busy = generating,
                        onClick = { generate() },
                    )

                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
                        AdvancedToggle(open = form.advancedOpen, onToggle = { form = form.copy(advancedOpen = !form.advancedOpen) })
                    }

                    AnimatedVisibility(visible = form.advancedOpen) {
                        Column {
                            VGap(22)
                            EnginePanel(selected = form.selection.engine, onSelect = { form = form.copy(selection = form.selection.withEngine(it)) })
                            VGap(16)
                            StartersPanel(selection = form.selection, onSelection = { form = form.copy(selection = it) })
                            VGap(16)
                            CoordinatesPanel(form = form, errors = errors, onForm = { form = it })
                            VGap(16)
                            PreviewPanel(config = config, files = files, activeTab = activeTab, onTab = { activeTab = it })
                            VGap(16)
                            ToolchainPanel(katalystVersion = StarterTemplate.KATALYST_VERSION)
                        }
                    }
                    VGap(80)
                }
            }

            if (toast != null) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)) {
                    Toast(zipName = toast!!)
                }
            }
        }
    }
}

@Composable
private fun TopBar(dark: Boolean, onToggleTheme: () -> Unit) {
    val p = palette()
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KIcon(KIcons.Logo, 30.dp, p.accent)
        HGap(10)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Katalyst", color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            HGap(7)
            Text("initializr", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(38.dp)
                .background(p.surface, RoundedCornerShape(10.dp))
                .border(1.dp, p.line, RoundedCornerShape(10.dp))
                .clickable { onToggleTheme() },
            contentAlignment = Alignment.Center,
        ) {
            KIcon(if (dark) KIcons.Sun else KIcons.Moon, 18.dp, p.ink2)
        }
    }
}

@Composable
private fun CoreNote() {
    val p = palette()
    Row(
        Modifier.fillMaxWidth().background(p.well, RoundedCornerShape(10.dp)).padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KIcon(KIcons.CheckCircle, 17.dp, p.good)
        HGap(9)
        val text = buildAnnotatedString {
            append("Every service ships with a ")
            withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight.SemiBold)) { append("REST API") }
            append(", ")
            withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight.SemiBold)) { append("YAML config") }
            append(" and an ")
            withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight.SemiBold)) { append("in-process event bus") }
            append(".")
        }
        Text(text, color = p.ink2, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun Toast(zipName: String) {
    val p = palette()
    Row(
        Modifier
            .background(p.surface, RoundedCornerShape(14.dp))
            .border(1.dp, p.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(p.goodSoft, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            KIcon(KIcons.Check, 19.dp, p.good)
        }
        HGap(13)
        Column {
            Text(zipName, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = LocalMono.current)
            Text("ready to download", color = p.muted, fontSize = 12.sp)
        }
    }
}
