package io.github.darkryh.katalyst.initializr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/**
 * The whole icon system. Every icon is a real vector — SVG path data (and native circles/ellipses/
 * rounded-rects) stroked onto a [Canvas] and scaled from a 24×24 viewBox to the requested size. This
 * is the deliberate replacement for the old UI's Unicode-glyph "icons" (⬢ ☀ ☾ ↺ …), which rendered
 * as tofu boxes wherever the font lacked the code point. Nothing here depends on a font.
 */
sealed interface Prim {
    val alpha: Float
}

data class Pth(val d: String, override val alpha: Float = 1f) : Prim

data class Cir(val cx: Float, val cy: Float, val r: Float, override val alpha: Float = 1f) : Prim

data class Ell(val cx: Float, val cy: Float, val rx: Float, val ry: Float, override val alpha: Float = 1f) : Prim

data class Rct(val x: Float, val y: Float, val w: Float, val h: Float, val rx: Float, override val alpha: Float = 1f) : Prim

/** An icon: a stroke width (in 24-unit viewBox space) and the primitives that draw it. */
class IconSpec(val stroke: Float, val prims: List<Prim>)

@Composable
fun KIcon(
    spec: IconSpec,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val f = this.size.minDimension / 24f
        val stroke = Stroke(width = spec.stroke * f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        spec.prims.forEach { prim ->
            val color = tint.copy(alpha = tint.alpha * prim.alpha)
            when (prim) {
                is Pth -> {
                    val path = PathParser().parsePathString(prim.d).toPath()
                    path.transform(Matrix().apply { scale(f, f, 1f) })
                    drawPath(path, color, style = stroke)
                }
                is Cir -> drawCircle(color, radius = prim.r * f, center = Offset(prim.cx * f, prim.cy * f), style = stroke)
                is Ell -> drawOval(
                    color,
                    topLeft = Offset((prim.cx - prim.rx) * f, (prim.cy - prim.ry) * f),
                    size = Size(prim.rx * 2 * f, prim.ry * 2 * f),
                    style = stroke,
                )
                is Rct -> drawRoundRect(
                    color,
                    topLeft = Offset(prim.x * f, prim.y * f),
                    size = Size(prim.w * f, prim.h * f),
                    cornerRadius = CornerRadius(prim.rx * f),
                    style = stroke,
                )
            }
        }
    }
}

/** The catalogue — geometry ported verbatim from the approved mockup. */
object KIcons {
    val Logo = IconSpec(1.6f, listOf(
        Pth("M12 2.5 20.2 7.2v9.6L12 21.5 3.8 16.8V7.2Z"),
        Pth("M12 7 16 9.3v4.9L12 16.5 8 14.2V9.3Z", alpha = 0.5f),
    ))
    val Moon = IconSpec(1.7f, listOf(Pth("M20.5 13.2A8 8 0 1 1 10.8 3.5 6.3 6.3 0 0 0 20.5 13.2Z")))
    val Sun = IconSpec(1.7f, listOf(
        Cir(12f, 12f, 4.2f),
        Pth("M12 2v2.2M12 19.8V22M4.2 4.2l1.6 1.6M18.2 18.2l1.6 1.6M2 12h2.2M19.8 12H22M4.2 19.8l1.6-1.6M18.2 5.8l1.6-1.6"),
    ))
    val Plus = IconSpec(2f, listOf(Pth("M12 5v14M5 12h14")))
    val AlertCircle = IconSpec(1.8f, listOf(Cir(12f, 12f, 9f), Pth("M12 7.5v5.5M12 16.2h.01")))
    val CheckCircle = IconSpec(1.8f, listOf(Cir(12f, 12f, 8.5f), Pth("M8.4 12.2l2.4 2.4 4.7-5")))
    val Database = IconSpec(1.7f, listOf(
        Ell(12f, 5.5f, 7f, 2.8f),
        Pth("M5 5.5v6c0 1.55 3.13 2.8 7 2.8s7-1.25 7-2.8v-6"),
        Pth("M5 11.5v6c0 1.55 3.13 2.8 7 2.8s7-1.25 7-2.8v-6"),
    ))
    val Clock = IconSpec(1.7f, listOf(Cir(12f, 12f, 8.5f), Pth("M12 7v5l3.4 2")))
    val Broadcast = IconSpec(1.7f, listOf(
        Cir(12f, 12f, 2.1f),
        Pth("M7.6 7.6a6.4 6.4 0 0 0 0 8.8M16.4 7.6a6.4 6.4 0 0 1 0 8.8M4.8 4.8a10.3 10.3 0 0 0 0 14.4M19.2 4.8a10.3 10.3 0 0 1 0 14.4"),
    ))
    val Bars = IconSpec(1.7f, listOf(
        Pth("M3.5 20h17"),
        Rct(5f, 11f, 3.4f, 7f, 1f),
        Rct(10.3f, 6.5f, 3.4f, 11.5f, 1f),
        Rct(15.6f, 13.5f, 3.4f, 4.5f, 1f),
    ))
    val Check = IconSpec(2.4f, listOf(Pth("M4.5 12.5l4.5 4.5 10.5-11")))
    val Download = IconSpec(1.9f, listOf(Pth("M12 4v11"), Pth("M8 11l4 4 4-4"), Pth("M5 19.5h14")))
    val Chevron = IconSpec(2f, listOf(Pth("M6 9l6 6 6-6")))
    val ServerPanel = IconSpec(1.7f, listOf(
        Rct(7f, 7f, 10f, 10f, 2f),
        Rct(10f, 10f, 4f, 4f, 1f),
        Pth("M10 3.5v3M14 3.5v3M10 17.5v3M14 17.5v3M3.5 10h3M3.5 14h3M17.5 10h3M17.5 14h3"),
    ))
    val Server = IconSpec(1.6f, listOf(
        Rct(3.5f, 4.5f, 17f, 6f, 1.6f),
        Rct(3.5f, 13.5f, 17f, 6f, 1.6f),
        Pth("M7 7.5h.01M7 16.5h.01"),
    ))
    val CheckBold = IconSpec(2.6f, listOf(Pth("M5 12.5l4.5 4.5 9.5-10")))
    val Sliders = IconSpec(1.7f, listOf(Pth("M6 4v16M18 4v16"), Rct(3.5f, 7f, 5f, 4f, 1f), Rct(15.5f, 13f, 5f, 4f, 1f)))
    val WarnTriangle = IconSpec(1.8f, listOf(Pth("M12 3.5 21.5 20H2.5Z"), Pth("M12 10v3.6"), Pth("M12 16.6h.01")))
    val Lines = IconSpec(1.7f, listOf(Pth("M4 7h16M4 12h16M4 17h10")))
    val Refresh = IconSpec(2f, listOf(Pth("M4 9a8 8 0 1 1-1.4 4.6"), Pth("M4 4v5h5")))
    val Code = IconSpec(1.7f, listOf(Pth("M8 8l-4 4 4 4M16 8l4 4-4 4M13.5 5l-3 14")))
    val Copy = IconSpec(1.7f, listOf(Rct(9f, 9f, 11f, 11f, 2.4f), Pth("M6 15H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1")))
    val Tag = IconSpec(1.7f, listOf(
        Pth("M3.5 12.5 12 4h6.5v6.5L10 19a1.5 1.5 0 0 1-2.1 0l-4.4-4.4a1.5 1.5 0 0 1 0-2.1Z"),
        Cir(15f, 8f, 1.15f),
    ))
    val Folder = IconSpec(1.6f, listOf(Pth("M4 7.5a1 1 0 0 1 1-1h3.6l1.8 1.8H19a1 1 0 0 1 1 1V18a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1Z")))
    val File = IconSpec(1.6f, listOf(Pth("M7 3.5h6.4L18 8.1V20a.5.5 0 0 1-.5.5h-10A.5.5 0 0 1 7 20Z"), Pth("M13.2 3.7V8.2H17.8")))
}
