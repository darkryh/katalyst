package com.ead.boshi_client.ui.email.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ead.boshi_client.ui.util.Email
import org.jsoup.Jsoup

@Composable
fun EmailItem(
    email: Email,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Strip HTML tags for preview text
    val previewText = if (email.isHtml) {
        stripHtmlTags(email.body)
    } else {
        email.body
    }

    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Companion.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = email.sender,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = email.timestamp.toString().substringBefore("T"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = email.subject,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Companion.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Companion.Ellipsis
            )
            Spacer(modifier = Modifier.Companion.height(2.dp))
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Utility function to strip HTML tags from text
private fun stripHtmlTags(html: String): String {
    return Jsoup.parse(html).text()
}