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
import com.ead.boshi_client.domain.models.Email
import com.ead.boshi_client.data.mappers.stripHtml
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmailItem(
    email: Email,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Strip HTML tags for preview text
    val previewText = email.body.stripHtml()
        .replace("\n", " ")
        .take(100)
        .trimEnd()

    // Format timestamp
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(email.timestamp))

    // Get sender display name (part before @)
    val senderDisplay = email.sender.substringBefore("@")

    // Display subject or placeholder
    val displaySubject = email.subject.takeIf { it.isNotEmpty() } ?: "(no subject)"

    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Sender and date row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = senderDisplay,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Subject
            Text(
                text = displaySubject,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Preview
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