package com.ead.boshi_client.ui.email.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ead.boshi_client.domain.models.Email
import com.ead.boshi_client.domain.models.EmailStatus
import com.ead.boshi_client.data.mappers.stripHtml
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmailPreview(
    modifier: Modifier = Modifier,
    email: Email
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(email.timestamp))

    // Get status badge color and text
    val (statusColor, statusText) = when (email.status) {
        EmailStatus.PENDING -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            "Pending"
        )
        EmailStatus.DELIVERED -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            "Delivered"
        )
        EmailStatus.FAILED -> Pair(
            MaterialTheme.colorScheme.errorContainer,
            "Failed"
        )
        EmailStatus.PERMANENTLY_FAILED -> Pair(
            MaterialTheme.colorScheme.errorContainer,
            "Permanently Failed"
        )
        else -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            "Unknown"
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Subject
                Text(
                    text = email.subject.takeIf { it.isNotEmpty() } ?: "(no subject)",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Sender info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = email.sender.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Sender and recipient info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = email.sender,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "to ${email.recipient}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Date
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status badge
                Surface(
                    modifier = Modifier
                        .padding(4.dp),
                    color = statusColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        HorizontalDivider()

        // Email body - scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Email body (rendered as plain text, can be HTML)
            Text(
                text = email.body.stripHtml(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            // Metadata if available
            if (email.tags.isNotEmpty() || email.metadata != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (email.tags.isNotEmpty()) {
                        Text(
                            text = "Tags: ${email.tags.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (email.spamDetected) {
                        Text(
                            text = "⚠️ Marked as potential spam (score: ${String.format("%.2f", email.spamScore)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "Message ID: ${email.messageId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}