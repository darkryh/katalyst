package com.ead.boshi_client.ui.email.sent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ead.boshi_client.ui.email.components.EmailItem
import com.ead.boshi_client.ui.email.components.EmailPreview
import com.ead.boshi_client.ui.util.Email
import com.ead.boshi_client.ui.util.Stubs

@Composable
fun EmailSentScreen() {
    var selectedEmail by remember { mutableStateOf<Email?>(null) }

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
                // Header
                Text(
                    text = "Sent",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )

                Divider()

                // Email list
                LazyColumn {
                    items(Stubs.emails) { email ->
                        EmailItem(
                            email = email,
                            isSelected = selectedEmail?.id == email.id,
                            onClick = { selectedEmail = email }
                        )
                        Divider()
                    }
                }
            }
        }

        // Divider between list and preview
        Divider(
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
            if (selectedEmail != null) {
                EmailPreview(email = selectedEmail!!)
            } else {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
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
