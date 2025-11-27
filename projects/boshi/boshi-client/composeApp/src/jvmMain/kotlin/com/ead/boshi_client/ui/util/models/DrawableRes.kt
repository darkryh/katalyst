package com.ead.boshi_client.ui.util.models

sealed class DrawableRes {
    data class ImageVector(val vector : androidx.compose.ui.graphics.vector.ImageVector) : DrawableRes()
    data class Painter(val painter : androidx.compose.ui.graphics.painter.Painter) : DrawableRes()
}