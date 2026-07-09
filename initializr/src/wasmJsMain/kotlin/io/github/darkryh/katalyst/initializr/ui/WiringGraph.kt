package io.github.darkryh.katalyst.initializr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.github.darkryh.katalyst.initializr.model.Feature
import io.github.darkryh.katalyst.initializr.model.FeatureSelection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private class GNode(
    val id: String,
    var label: String,
    var core: Boolean,
    var dep: String?,
    var x: Float,
    var y: Float,
    var r: Float,
    var tr: Float,
    var angle: Float,
)

private data class Desired(val id: String, val label: String, val core: Boolean, val dep: String?)

private fun desiredNodes(sel: FeatureSelection): List<Desired> =
    buildList {
        add(Desired("engine", sel.engine.id, true, null))
        add(Desired("web", "web", true, null))
        add(Desired("config", "config", true, null))
        add(Desired("events", "events", true, null))
        for (f in sel.enabled) {
            val dep = if (f.requires != null && sel.isEnabled(f.requires)) f.requires.id else null
            add(Desired(f.id, f.display.lowercase(), false, dep))
        }
    }

/**
 * The wiring-graph hero: `app` at the center with a wired satellite node per selected starter — the
 * dependency graph Katalyst discovers at boot. Core nodes are filled, optional nodes outlined, and a
 * `migrations → persistence` prerequisite is drawn as an amber dashed "depends-on" bond. When the
 * config won't assemble ([valid] false) the center drops its ready pulse and shows a warning ring.
 */
@Composable
fun WiringGraph(
    selection: FeatureSelection,
    valid: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = palette()
    val measurer = rememberTextMeasurer()
    val sel by rememberUpdatedState(selection)
    val isValid by rememberUpdatedState(valid)
    var size by remember { mutableStateOf(IntSize.Zero) }
    var now by remember { mutableStateOf(0L) }
    val nodes = remember { mutableListOf<GNode>().toMutableStateList() }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { t ->
                stepPhysics(nodes, desiredNodes(sel), size)
                now = t
            }
        }
    }

    Canvas(modifier.fillMaxSize().onSizeChanged { size = it }) {
        val seconds = now / 1_000_000_000.0
        drawGraph(nodes, measurer, p, isValid, seconds)
    }
}

private fun stepPhysics(
    nodes: SnapshotStateList<GNode>,
    desired: List<Desired>,
    size: IntSize,
) {
    if (size.width == 0 || size.height == 0) return
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val cx = w / 2f
    val cy = h / 2f
    val radius = min(w, h) * 0.34f

    // Center.
    var center = nodes.firstOrNull { it.id == "__app" }
    if (center == null) {
        center = GNode("__app", "app", true, null, cx, cy, 0f, 1f, 0f)
        nodes.add(center)
    }

    // Reconcile satellites.
    desired.forEachIndexed { i, d ->
        val angle = (-PI / 2 + i * (2 * PI / desired.size)).toFloat()
        val existing = nodes.firstOrNull { it.id == d.id }
        if (existing == null) {
            nodes.add(GNode(d.id, d.label, d.core, d.dep, cx, cy, 0f, 1f, angle))
        } else {
            existing.label = d.label
            existing.core = d.core
            existing.dep = d.dep
            existing.angle = angle
            existing.tr = 1f
        }
    }
    val wanted = desired.map { it.id }.toSet()
    nodes.forEach { if (it.id != "__app" && it.id !in wanted) it.tr = 0f }

    // Ease toward targets.
    nodes.forEach { n ->
        val tx: Float
        val ty: Float
        if (n.id == "__app") {
            tx = cx; ty = cy
        } else {
            tx = cx + cos(n.angle) * radius
            ty = cy + sin(n.angle) * radius
        }
        n.x += (tx - n.x) * 0.16f
        n.y += (ty - n.y) * 0.16f
        n.r += (n.tr - n.r) * 0.14f
    }
    nodes.removeAll { it.id != "__app" && it.r < 0.02f && it.tr == 0f }
}

private fun DrawScope.drawGraph(
    nodes: List<GNode>,
    measurer: TextMeasurer,
    p: Palette,
    valid: Boolean,
    seconds: Double,
) {
    val center = nodes.firstOrNull { it.id == "__app" } ?: return

    // Edges: center -> satellite.
    nodes.forEach { n ->
        if (n === center || n.r < 0.02f) return@forEach
        drawLine(
            color = p.edge.copy(alpha = p.edge.alpha * n.r.coerceIn(0f, 1f)),
            start = Offset(center.x, center.y),
            end = Offset(n.x, n.y),
            strokeWidth = 1.4f,
        )
    }
    // Dependency edges (amber dashed).
    nodes.forEach { n ->
        val dep = n.dep ?: return@forEach
        val target = nodes.firstOrNull { it.id == dep } ?: return@forEach
        drawLine(
            color = p.edgeCat.copy(alpha = p.edgeCat.alpha * n.r.coerceIn(0f, 1f)),
            start = Offset(n.x, n.y),
            end = Offset(target.x, target.y),
            strokeWidth = 1.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)),
        )
    }
    // Nodes.
    nodes.forEach { n ->
        if (n.r < 0.02f) return@forEach
        val isCenter = n.id == "__app"
        val bob = if (isCenter) 0f else (sin(seconds * 1.1 + n.angle * 3).toFloat() * 2f)
        val x = n.x
        val y = n.y + bob
        val rad = (if (isCenter) 18f else 12f) * n.r

        if (isCenter) {
            val pulse = (sin(seconds * 2.0).toFloat() * 0.5f + 0.5f)
            val haloColor = if (valid) p.node else p.bad
            drawCircle(haloColor.copy(alpha = 0.14f * n.r), radius = rad + 7f + if (valid) pulse * 3f else 0f, center = Offset(x, y))
            if (!valid) {
                drawCircle(p.bad, radius = rad + 5f, center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
            }
            drawCircle(if (valid) p.node else p.bad, radius = rad, center = Offset(x, y))
        } else if (n.core) {
            drawCircle(p.node.copy(alpha = 0.9f * n.r), radius = rad, center = Offset(x, y))
        } else {
            drawCircle(p.surface.copy(alpha = n.r), radius = rad, center = Offset(x, y))
            drawCircle(p.node.copy(alpha = n.r), radius = rad, center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }

        // Label.
        val labelText = if (isCenter) (if (valid) "app" else "!") else n.label
        val style =
            TextStyle(
                color = if (isCenter) p.onAccent else p.muted.copy(alpha = n.r.coerceIn(0f, 1f)),
                fontSize = if (isCenter) 12.sp else 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        val measured = measurer.measure(labelText, style)
        val lx = x - measured.size.width / 2f
        val ly = if (isCenter) y - measured.size.height / 2f else y + rad + 3f
        drawText(measurer, labelText, topLeft = Offset(lx, ly), style = style)
    }
}
