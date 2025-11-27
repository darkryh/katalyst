package com.ead.boshi_client.ui.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ead.boshi_client.ui.dashboard.DashboardCompositionLayoutState
import com.ead.boshi_client.ui.dashboard.models.NavItem
import com.ead.boshi_client.ui.util.Icon


@Composable
fun DashboardNavigationItem(
    modifier: Modifier = Modifier,
    itemModifier : Modifier = Modifier,
    navItem: NavItem,
    onClick : () -> Unit
) {
    val dashboardComposition = DashboardCompositionLayoutState.current
    val isExpanded = dashboardComposition.isSidebarExpanded.value

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val shape : Shape = if (isHovered) {
        RoundedCornerShape(size = 8.dp)
    }
    else {
        RectangleShape
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) { MaterialTheme.colorScheme.surfaceVariant }
        else { MaterialTheme.colorScheme.surface }
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = backgroundColor, shape = shape)
            .clip(shape = shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .hoverable(interactionSource),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = itemModifier
                .size(20.dp),
            resource = navItem.res,
            contentDescription = navItem.contentDescription
        )

        if(isExpanded) {
            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = navItem.title,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                fontWeight = FontWeight.W400
            )
        }
    }
}