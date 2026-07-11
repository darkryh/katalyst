package io.github.darkryh.katalyst.initializr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VGap(height: Int) = Spacer(Modifier.height(height.dp))

@Composable
fun HGap(width: Int) = Spacer(Modifier.width(width.dp))

/** The little "New service" pill above the headline. */
@Composable
fun Eyebrow(text: String) {
    val p = palette()
    Row(
        Modifier
            .background(p.accentSoft, RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        KIcon(KIcons.Plus, 13.dp, p.accent)
        Text(text.uppercase(), color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
    }
}

/** An inline validation message: alert icon + red text. */
@Composable
fun ErrorRow(message: String) {
    val p = palette()
    Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KIcon(KIcons.AlertCircle, 14.dp, p.bad)
        Text(message, color = p.bad, fontSize = 12.5.sp)
    }
}

/** A labelled text input with focus/error borders, matching the mockup's `.input`. */
@Composable
fun KField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    mono: Boolean = false,
    placeholder: String = "",
    labelTrailing: (@Composable () -> Unit)? = null,
) {
    val p = palette()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hasError = error != null
    val border = when {
        hasError -> p.bad
        focused -> p.accent
        else -> p.line
    }
    val family = if (mono) LocalMono.current else FontFamily.Default
    val fontSize = if (big) 18.sp else 15.sp
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = p.ink2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (labelTrailing != null) {
                Spacer(Modifier.weight(1f))
                labelTrailing()
            }
        }
        VGap(7)
        Box(
            Modifier
                .fillMaxWidth()
                .background(p.surface, RoundedCornerShape(11.dp))
                .border(1.dp, border, RoundedCornerShape(11.dp))
                .padding(horizontal = if (big) 16.dp else 14.dp, vertical = if (big) 15.dp else 13.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, color = p.muted, fontSize = fontSize, fontFamily = family)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                interactionSource = interaction,
                textStyle = TextStyle(
                    color = p.ink,
                    fontSize = fontSize,
                    fontFamily = family,
                    fontWeight = if (big) FontWeight.Medium else FontWeight.Normal,
                ),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) ErrorRow(error)
    }
}

/** The 42×24 pill toggle used by the advanced starter switches. */
@Composable
fun ToggleSwitch(
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val p = palette()
    val track = if (checked) p.accent else p.line
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .width(42.dp)
            .height(24.dp)
            .background(track, RoundedCornerShape(999.dp))
            .then(if (enabled) Modifier.clickable { onToggle() } else Modifier),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .width(18.dp)
                .height(18.dp)
                .background(Color.White, RoundedCornerShape(999.dp)),
        )
    }
}

/** Full-width primary action. */
@Composable
fun GenerateButton(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val p = palette()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        !enabled -> p.well
        hovered -> p.accentH
        else -> p.accent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .clickable(enabled = enabled && !busy) { onClick() }
            .background(bg, RoundedCornerShape(12.dp))
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!busy) {
            KIcon(KIcons.Download, 19.dp, if (enabled) p.onAccent else p.muted)
            HGap(9)
        }
        Text(label, color = if (enabled) p.onAccent else p.muted, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The quiet "Advanced options" disclosure. */
@Composable
fun AdvancedToggle(open: Boolean, onToggle: () -> Unit) {
    val p = palette()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .hoverable(interaction)
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (open) "Hide advanced options" else "Advanced options",
            color = if (hovered) p.ink else p.muted,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
        )
        KIcon(KIcons.Chevron, 16.dp, if (hovered) p.ink else p.muted, Modifier.rotate(if (open) 180f else 0f))
    }
}

/** A bordered advanced card with an icon + title + right-aligned subtitle. */
@Composable
fun Panel(
    icon: IconSpec,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val p = palette()
    Column(
        modifier
            .fillMaxWidth()
            .background(p.surface, RoundedCornerShape(16.dp))
            .border(1.dp, p.line, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KIcon(icon, 18.dp, p.accent)
            HGap(9)
            Text(title, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(sub, color = p.muted, fontSize = 12.sp)
        }
        VGap(16)
        content()
    }
}

/** A read-only toolchain chip: "Kotlin **2.4.0**". */
@Composable
fun Chip(prefix: String, value: String) {
    val p = palette()
    Row(
        Modifier
            .background(p.well, RoundedCornerShape(999.dp))
            .border(1.dp, p.line, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(prefix, color = p.ink2, fontSize = 12.5.sp, fontFamily = LocalMono.current)
        Text(value, color = p.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = LocalMono.current)
    }
}
