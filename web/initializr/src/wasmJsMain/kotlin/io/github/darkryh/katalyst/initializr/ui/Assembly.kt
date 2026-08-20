package io.github.darkryh.katalyst.initializr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkryh.katalyst.initializr.model.ProjectConfig
import io.github.darkryh.katalyst.initializr.template.TemplateFile

enum class Tab(val label: String) {
    APP("Application.kt"),
    GRADLE("build.gradle.kts"),
    YAML("application.yaml"),
    TREE("Files"),
}

/** The Advanced "Live preview" panel — tabs + the generated file for the active tab (or the tree). */
@Composable
fun PreviewPanel(
    config: ProjectConfig,
    files: List<TemplateFile>,
    activeTab: Tab,
    onTab: (Tab) -> Unit,
) {
    val p = palette()
    Panel(icon = KIcons.Code, title = "Live preview", sub = "reflects your selection") {
        // Tab strip.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { tab ->
                val on = tab == activeTab
                Row(
                    Modifier
                        .clickable { onTab(tab) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tab.label,
                        color = if (on) p.accent else p.muted,
                        fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    )
                    if (tab == Tab.TREE) {
                        HGap(5)
                        Box(
                            Modifier.background(p.accentSoft, RoundedCornerShape(999.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text("${files.size}", color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = LocalMono.current)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        VGap(14)

        // Content.
        Box(
            Modifier
                .fillMaxWidth()
                .background(p.well, RoundedCornerShape(12.dp))
                .border(1.dp, p.line, RoundedCornerShape(12.dp))
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (activeTab == Tab.TREE) {
                    TreeView(config.artifactId, files.map { it.path })
                } else {
                    val raw = previewText(config, files, activeTab)
                    val text = if (activeTab == Tab.YAML) highlightYaml(raw, p) else highlightKotlin(raw, p)
                    Text(text, fontSize = 12.75.sp, fontFamily = LocalMono.current, lineHeight = 21.sp, color = p.ink2)
                }
            }
        }
    }
}

private fun previewText(config: ProjectConfig, files: List<TemplateFile>, tab: Tab): String {
    fun content(path: String) = files.firstOrNull { it.path == path }?.content ?: ""
    return when (tab) {
        Tab.APP -> content("src/main/kotlin/${config.packagePath}/Application.kt")
        Tab.GRADLE -> content("build.gradle.kts")
        Tab.YAML -> content("src/main/resources/application.yaml")
        Tab.TREE -> ""
    }
}

// ---- file tree (icon rows, no box-drawing glyphs) ----

private class TNode(val name: String, val kids: LinkedHashMap<String, TNode> = LinkedHashMap())

@Composable
private fun TreeView(root: String, paths: List<String>) {
    val rootNode = TNode(root)
    for (path in paths) {
        var node = rootNode
        for (seg in path.split('/')) node = node.kids.getOrPut(seg) { TNode(seg) }
    }
    val rows = ArrayList<Triple<Int, String, Boolean>>()
    rows.add(Triple(0, "$root/", true))
    fun walk(node: TNode, depth: Int) {
        node.kids.values.forEach { child ->
            val isDir = child.kids.isNotEmpty()
            rows.add(Triple(depth, child.name, isDir))
            if (isDir) walk(child, depth + 1)
        }
    }
    walk(rootNode, 1)
    Column {
        rows.forEach { (depth, name, isDir) -> TreeRow(depth, name, isDir) }
    }
}

@Composable
private fun TreeRow(depth: Int, name: String, isDir: Boolean) {
    val p = palette()
    Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width((depth * 16).dp))
        KIcon(if (isDir) KIcons.Folder else KIcons.File, 15.dp, if (isDir) p.accent else p.muted)
        HGap(8)
        Text(
            name,
            color = if (isDir) p.ink else p.ink2,
            fontSize = 12.75.sp,
            fontFamily = LocalMono.current,
            fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ---- lightweight syntax highlighting ----

private val KOTLIN_KEYWORDS = setOf(
    "package", "import", "fun", "val", "var", "class", "object", "data", "override", "private",
    "public", "internal", "protected", "return", "for", "while", "do", "if", "else", "when",
    "true", "false", "null", "by", "in", "is", "as", "enum", "interface", "sealed", "companion",
    "this", "super", "const", "vararg", "out",
)

private fun highlightKotlin(code: String, p: Palette): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = code.length
    while (i < n) {
        val c = code[i]
        when {
            c == '/' && i + 1 < n && code[i + 1] == '/' -> {
                val start = i
                while (i < n && code[i] != '\n') i++
                withStyle(SpanStyle(color = p.codeCmt)) { append(code.substring(start, i)) }
            }
            c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                val start = i
                i += 2
                while (i < n && !(code[i] == '*' && i + 1 < n && code[i + 1] == '/')) i++
                if (i < n) i += 2
                withStyle(SpanStyle(color = p.codeCmt)) { append(code.substring(start, i)) }
            }
            c == '*' && atLineStart(code, i) -> {
                val start = i
                while (i < n && code[i] != '\n') i++
                withStyle(SpanStyle(color = p.codeCmt)) { append(code.substring(start, i)) }
            }
            c == '"' -> {
                val start = i
                i++
                while (i < n && code[i] != '"') {
                    if (code[i] == '\\' && i + 1 < n) i++
                    i++
                }
                if (i < n) i++
                withStyle(SpanStyle(color = p.codeStr)) { append(code.substring(start, i)) }
            }
            c.isLetter() || c == '_' -> {
                val start = i
                while (i < n && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                val word = code.substring(start, i)
                if (word in KOTLIN_KEYWORDS) {
                    withStyle(SpanStyle(color = p.codeKw, fontWeight = FontWeight.SemiBold)) { append(word) }
                } else {
                    append(word)
                }
            }
            c.isDigit() -> {
                val start = i
                while (i < n && (code[i].isDigit() || code[i] == '.')) i++
                withStyle(SpanStyle(color = p.codeNum)) { append(code.substring(start, i)) }
            }
            else -> {
                append(c)
                i++
            }
        }
    }
}

private fun atLineStart(code: String, index: Int): Boolean {
    var j = index - 1
    while (j >= 0 && (code[j] == ' ' || code[j] == '\t')) j--
    return j < 0 || code[j] == '\n'
}

private fun highlightYaml(code: String, p: Palette): AnnotatedString = buildAnnotatedString {
    code.split('\n').forEachIndexed { idx, line ->
        if (idx > 0) append("\n")
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") -> withStyle(SpanStyle(color = p.codeCmt)) { append(line) }
            else -> {
                val colon = line.indexOf(':')
                val keyPart = if (colon >= 0) line.substring(0, colon) else ""
                if (colon >= 0 && keyPart.isNotBlank() && !keyPart.trimStart().startsWith("-")) {
                    withStyle(SpanStyle(color = p.codeKey, fontWeight = FontWeight.SemiBold)) { append(line.substring(0, colon)) }
                    append(line.substring(colon))
                } else {
                    append(line)
                }
            }
        }
    }
}
