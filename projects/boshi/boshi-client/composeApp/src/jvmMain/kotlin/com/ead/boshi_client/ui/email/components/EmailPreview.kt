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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ead.boshi_client.ui.email.components.renderer.HtmlEmailRenderer
import com.ead.boshi_client.ui.email.components.renderer.PlainTextEmailRenderer
import com.ead.boshi_client.ui.util.Email

@Composable
fun EmailPreview(
    modifier: Modifier = Modifier,
    email: Email
) {
    Column(
        modifier = modifier
            .fillMaxSize()
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
                    text = email.subject,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Companion.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.Companion.height(16.dp))

                // Sender info
                Row(
                    modifier = Modifier.Companion.fillMaxWidth(),
                    verticalAlignment = Alignment.Companion.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.Companion.size(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Companion.Center,
                            modifier = Modifier.Companion.fillMaxSize()
                        ) {
                            Text(
                                text = email.sender.first().uppercaseChar().toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.Companion.width(12.dp))
                    Column {
                        Text(
                            text = email.sender,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = buildString {
                                append("to ${email.recipient}")
                                if (email.cc.isNotEmpty()) {
                                    append(", cc: ${email.cc.joinToString()}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.Companion.weight(1f))
                    Text(
                        text = email.timestamp.toString().substringBefore("T"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.Companion.height(16.dp))

                // Action buttons
                EmailActionButtons(
                    emailId = email.id,
                    onReply = {
                        println("Reply to ${email.id}")
                        // TODO: Open compose window with reply data
                    },
                    onReplyAll = {
                        println("Reply All to ${email.id}")
                        // TODO: Open compose window with reply all data
                    },
                    onForward = {
                        println("Forward ${email.id}")
                        // TODO: Open compose window with forward data
                    },
                    onDelete = {
                        println("Delete ${email.id}")
                        // TODO: Actually delete the email
                    }
                )
            }
        }

        HorizontalDivider()

        // Email body - scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Render HTML or plain text
            if (email.isHtml) {
                HtmlEmailRenderer(
                    htmlContent = email.body,
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                PlainTextEmailRenderer(
                    textContent = email.body,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            // Show attachments if any
            if (email.attachments.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                EmailAttachmentList(
                    attachments = email.attachments,
                    onDownload = { attachment ->
                        println("Download attachment: ${attachment.fileName}")
                        // TODO: Implement actual download
                    }
                )
            }
        }
    }
}