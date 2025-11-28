package com.ead.boshi_client.ui.email.sent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ead.boshi_client.ui.email.EmailViewModel
import com.ead.boshi_client.ui.email.components.EmailItem
import com.ead.boshi_client.ui.email.components.EmailPreview

@Composable
fun EmailSentScreen(
    viewModel: EmailViewModel
) {
    // Collect StateFlows as Compose states
    val sentEmails by viewModel.sentEmails.collectAsState()
    val selectedEmail by viewModel.selectedEmail
    val isLoading by viewModel.isLoading
    val error by viewModel.error

    // Sync emails on screen load
    LaunchedEffect(Unit) {
        viewModel.syncSentEmails()
    }

    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        // Email list - left side (40% width)
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.4f),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with sync button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sent Emails (${sentEmails.size})",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp)
                    )

                    // Sync button
                    IconButton(
                        onClick = { viewModel.syncSentEmails() },
                        enabled = !isLoading,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = "Sync emails",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                // Loading state
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Error state
                if (error != null && !isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close error",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    HorizontalDivider()
                }

                // Email list
                if (sentEmails.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No sent emails",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn {
                        items(sentEmails, key = { it.messageId }) { email ->
                            EmailItem(
                                email = email,
                                isSelected = selectedEmail?.id == email.id,
                                onClick = { viewModel.setSelectedEmail(email) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Divider between list and preview
        VerticalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
        )

        // Email preview - right side (60% width)
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.6f),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            selectedEmail?.let { email ->
                EmailPreview(email = email)
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (sentEmails.isEmpty() && !isLoading)
                            "No emails to display"
                        else
                            "Select an email to preview",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
