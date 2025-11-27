package com.ead.boshi_client.ui.util

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.ead.boshi_client.ui.util.models.DrawableRes

@Composable
fun Icon(
    resource: DrawableRes,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    when(resource) {
        is DrawableRes.ImageVector -> {
            Icon(
                painter = rememberVectorPainter(resource.vector),
                contentDescription = contentDescription,
                modifier = modifier,
                tint = tint,
            )
        }
        is DrawableRes.Painter -> {
            Icon(
                painter = resource.painter,
                contentDescription = contentDescription,
                modifier = modifier,
                tint = tint,
            )
        }
    }
}
