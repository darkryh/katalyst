package com.ead.boshi_client.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp


fun Color.darken(factor: Float): Color {
    return lerp(this, Color.Black, factor.coerceIn(0f, 1f))
}

/**
 * Extension to make color lighter
 */
fun Color.lighten(factor: Float): Color {
    return lerp(this, Color.White, factor.coerceIn(0f, 1f))
}