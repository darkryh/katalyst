package com.ead.boshi_client.ui.email.inbox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ead.boshi_client.ui.email.EmailViewModel
import com.ead.boshi_client.ui.email.components.EmailItem
import com.ead.boshi_client.ui.email.components.EmailPreview
import com.ead.boshi_client.ui.util.Stubs

@Composable
fun EmailInboxScreen(
    viewModel: EmailViewModel,
) {
    val selectedEmail by viewModel.selectedEmail
    val receivedEmails by viewModel.receivedEmails.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxSize()
    ) {
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
                // Header
                Text(
                    text = "Inbox",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )

                HorizontalDivider()

                // Email list
                LazyColumn {
                    items(receivedEmails) { email ->
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
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select an email to preview",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


