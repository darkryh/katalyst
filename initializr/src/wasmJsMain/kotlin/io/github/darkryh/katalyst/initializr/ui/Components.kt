package io.github.darkryh.katalyst.initializr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A titled panel — the numbered "01 / Identity" card of the console. */
@Composable
fun SectionCard(
    index: String,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val p = palette()
    Column(
        modifier
            .fillMaxWidth()
            .background(p.surface, RoundedCornerShape(10.dp))
            .border(1.dp, p.line, RoundedCornerShape(10.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(p.accentSoft, RoundedCornerShape(6.dp))
                    .border(1.dp, p.accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) { Text(index, color = p.accent, fontSize = 11.sp, fontFamily = LocalMono.current) }
            Spacer(Modifier.width(10.dp))
            Text(title, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Text(hint, color = p.muted, fontSize = 12.5.sp)
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/** A labeled text input with live validation state: neutral / valid (accent) / error (red + message). */
@Composable
fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    placeholder: String = "",
) {
    val p = palette()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hasError = error != null
    val filled = value.isNotEmpty()
    val borderColor =
        when {
            hasError -> p.bad
            focused -> p.accent
            filled -> p.good.copy(alpha = 0.55f)
            else -> p.line
        }
    Column(modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Text(label, color = p.ink2, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) p.surface else p.well, RoundedCornerShape(7.dp))
                .border(1.dp, borderColor, RoundedCornerShape(7.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, color = p.faint, fontSize = 14.5.sp, fontFamily = if (mono) LocalMono.current else FontFamily.Default)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    interactionSource = interaction,
                    textStyle =
                        TextStyle(
                            color = p.ink,
                            fontSize = 14.5.sp,
                            fontFamily = if (mono) LocalMono.current else FontFamily.Default,
                        ),
                    cursorBrush = SolidColor(p.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (hasError) "✗" else if (filled) "✓" else "",
                color = if (hasError) p.bad else p.good,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (hasError) {
            Spacer(Modifier.height(4.dp))
            Text(error!!, color = p.bad, fontSize = 11.5.sp)
        }
    }
}

/** The primary call-to-action ("Generate & download"). */
@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = if (!enabled) p.well else if (hovered) p.accentH else p.accent
    Box(
        modifier
            .hoverable(interaction)
            .clickable(enabled = enabled) { onClick() }
            .background(bg, RoundedCornerShape(7.dp))
            .border(1.dp, if (enabled) bg else p.line, RoundedCornerShape(7.dp))
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(
            text,
            color = if (enabled) p.onAccent else p.muted,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** A quiet secondary button ("Explore files", "Reconfigure"). */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .hoverable(interaction)
            .clickable { onClick() }
            .background(p.surface, RoundedCornerShape(7.dp))
            .border(1.dp, if (hovered) p.accent.copy(alpha = 0.4f) else p.line, RoundedCornerShape(7.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, color = if (hovered) p.ink else p.ink2, fontSize = 13.5.sp)
    }
}

/** A small pill — used for the version tag and the validation chips. */
@Composable
fun Pill(
    text: String,
    fg: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
    border: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null,
) {
    val base =
        Modifier
            .background(bg, RoundedCornerShape(100.dp))
            .border(1.dp, border, RoundedCornerShape(100.dp))
    val m = if (onClick != null) base.clickable { onClick() } else base
    Box(m.padding(horizontal = 9.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontSize = 11.5.sp, fontFamily = LocalMono.current)
    }
}

@Composable
fun HGap(width: Int) = Spacer(Modifier.width(width.dp))

@Composable
fun VGap(height: Int) = Spacer(Modifier.height(height.dp))
