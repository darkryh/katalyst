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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkryh.katalyst.initializr.model.FeatureSelection
import io.github.darkryh.katalyst.initializr.model.ProjectConfig
import io.github.darkryh.katalyst.initializr.template.TemplateFile

enum class Tab(val label: String) {
    APPLICATION("Application.kt"),
    BUILD("build.gradle.kts"),
    TREE("tree"),
    YAML("application.yaml"),
}

@Composable
fun AssemblyPane(
    config: ProjectConfig,
    valid: Boolean,
    files: List<TemplateFile>,
    activeTab: Tab,
    buildOverlay: (@Composable () -> Unit)?,
    onTab: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette()
    val nodeCount = config.selection.enabled.size + 4 + 1
    Column(
        modifier
            .fillMaxWidth()
            .background(p.surface, RoundedCornerShape(10.dp))
            .border(1.dp, p.line, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp)),
    ) {
        // Graph.
        Box(
            Modifier
                .fillMaxWidth()
                .height(290.dp)
                .background(p.graphBg),
        ) {
            WiringGraph(selection = config.selection, valid = valid, modifier = Modifier)
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("katalystApplication", color = p.accent, fontSize = 11.sp, fontFamily = LocalMono.current)
                Text(" · $nodeCount nodes wired", color = p.muted, fontSize = 11.sp, fontFamily = LocalMono.current)
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LegendDot("core", p.node, filled = true)
                LegendDot("optional", p.node, filled = false)
                LegendDot("depends-on", p.edgeCat, filled = true)
            }
        }

        // Tabs.
        Row(
            Modifier.fillMaxWidth().background(p.surface).padding(start = 12.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val on = activeTab == tab
                Box(
                    Modifier
                        .clickable { onTab(tab) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        tab.label,
                        color = if (on) p.accent else p.muted,
                        fontSize = 12.5.sp,
                        fontFamily = LocalMono.current,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text("${files.size} files", color = p.muted, fontSize = 11.5.sp, fontFamily = LocalMono.current)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))

        // Preview (or the build overlay while generating).
        Box(Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
            if (buildOverlay != null) {
                Box(Modifier.padding(16.dp)) { buildOverlay() }
            } else {
                val text = previewText(config, files, activeTab)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp)) {
                        Text(
                            text,
                            color = p.ink,
                            fontSize = 12.5.sp,
                            fontFamily = LocalMono.current,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color, filled: Boolean) {
    val p = palette()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(9.dp)
                .height(9.dp)
                .background(if (filled) color else p.graphBg, RoundedCornerShape(100.dp))
                .border(if (filled) 0.dp else 1.5.dp, color, RoundedCornerShape(100.dp)),
        )
        Spacer(Modifier.width(5.dp))
        Text(label, color = p.muted, fontSize = 11.sp)
    }
}

private fun previewText(config: ProjectConfig, files: List<TemplateFile>, tab: Tab): String {
    fun content(path: String) = files.firstOrNull { it.path == path }?.content ?: ""
    val pkgPath = config.packagePath
    return when (tab) {
        Tab.APPLICATION -> content("src/main/kotlin/$pkgPath/Application.kt")
        Tab.BUILD -> content("build.gradle.kts")
        Tab.YAML -> content("src/main/resources/application.yaml")
        Tab.TREE -> renderTree(config.artifactId, files.map { it.path })
    }
}

/** A box-drawing file tree from the generated paths. */
private fun renderTree(root: String, paths: List<String>): String {
    // Build a nested structure keyed by path segments.
    data class Node(val name: String, val kids: LinkedHashMap<String, Node> = LinkedHashMap())
    val rootNode = Node(root)
    for (path in paths) {
        var node = rootNode
        for (seg in path.split('/')) node = node.kids.getOrPut(seg) { Node(seg) }
    }
    val sb = StringBuilder("$root/\n")
    fun walk(node: Node, prefix: String) {
        val entries = node.kids.values.toList()
        entries.forEachIndexed { i, child ->
            val last = i == entries.lastIndex
            val isDir = child.kids.isNotEmpty()
            sb.append(prefix).append(if (last) "└─ " else "├─ ").append(child.name).append(if (isDir) "/" else "").append('\n')
            walk(child, prefix + if (last) "   " else "│  ")
        }
    }
    walk(rootNode, "")
    return sb.toString().trimEnd('\n')
}
