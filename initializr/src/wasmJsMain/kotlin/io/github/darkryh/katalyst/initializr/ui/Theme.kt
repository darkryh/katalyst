package io.github.darkryh.katalyst.initializr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * The Katalyst web palette — indigo on a cool, indigo-biased neutral, the visual twin of the docs
 * site's Material `indigo` scheme (light + slate-dark). Bright indigo is the one signature accent;
 * amber is the "depends-on" reaction bond in the wiring graph; semantic good/warn/bad stay separate
 * from the accent. Both themes are designed, not inverted.
 */
data class Palette(
    val ground: Color,
    val surface: Color,
    val well: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val accent: Color,
    val accentH: Color,
    val accentSoft: Color,
    val amber: Color,
    val violet: Color,
    val good: Color,
    val goodSoft: Color,
    val warn: Color,
    val bad: Color,
    val badSoft: Color,
    val node: Color,
    val edge: Color,
    val edgeCat: Color,
    val graphBg: Color,
    val onAccent: Color,
)

val LightPalette =
    Palette(
        ground = Color(0xFFF5F7FC),
        surface = Color(0xFFFFFFFF),
        well = Color(0xFFEDF0F8),
        ink = Color(0xFF141824),
        ink2 = Color(0xFF4B5268),
        muted = Color(0xFF868DA3),
        faint = Color(0xFFB4BAC9),
        line = Color(0xFFE3E7F1),
        accent = Color(0xFF4B57D6),
        accentH = Color(0xFF3A45B8),
        accentSoft = Color(0xFFEAECFF),
        amber = Color(0xFFDB8636),
        violet = Color(0xFF6E56E8),
        good = Color(0xFF2E9E5B),
        goodSoft = Color(0xFFE4F5EB),
        warn = Color(0xFFC08A24),
        bad = Color(0xFFCE4141),
        badSoft = Color(0xFFFCEBEB),
        node = Color(0xFF4B57D6),
        edge = Color(0x4D4B57D6),
        edgeCat = Color(0xB8DB8636),
        graphBg = Color(0xFFF0F3FB),
        onAccent = Color(0xFFFFFFFF),
    )

val DarkPalette =
    Palette(
        ground = Color(0xFF0B0D14),
        surface = Color(0xFF12141D),
        well = Color(0xFF161927),
        ink = Color(0xFFECEEF6),
        ink2 = Color(0xFFA7AEC4),
        muted = Color(0xFF6C7389),
        faint = Color(0xFF3A4056),
        line = Color(0xFF232838),
        accent = Color(0xFF7E8CFF),
        accentH = Color(0xFF97A2FF),
        accentSoft = Color(0xFF1B2038),
        amber = Color(0xFFF0A45C),
        violet = Color(0xFF9A86FF),
        good = Color(0xFF43B972),
        goodSoft = Color(0xFF132A1E),
        warn = Color(0xFFD9A63E),
        bad = Color(0xFFE5615F),
        badSoft = Color(0xFF2C1719),
        node = Color(0xFF8B97FF),
        edge = Color(0x528B97FF),
        edgeCat = Color(0xC7F0A45C),
        graphBg = Color(0xFF0E111B),
        onAccent = Color(0xFF0B0D14),
    )

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Ambient monospace family for code/coordinates; the rest of the UI uses the default sans. */
val LocalMono = staticCompositionLocalOf { FontFamily.Monospace }

@Composable
fun palette(): Palette = LocalPalette.current
