package com.ead.boshi_client.ui.email.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.ReplyAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EmailActionButtons(
    emailId: String,
    onReply: () -> Unit,
    onReplyAll: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Reply button
        OutlinedButton(
            onClick = onReply,
            modifier = Modifier.height(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Reply,
                contentDescription = "Reply",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Reply", style = MaterialTheme.typography.labelLarge)
        }

        // Reply All button
        OutlinedButton(
            onClick = onReplyAll,
            modifier = Modifier.height(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ReplyAll,
                contentDescription = "Reply All",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Reply All", style = MaterialTheme.typography.labelLarge)
        }

        // Forward button
        OutlinedButton(
            onClick = onForward,
            modifier = Modifier.height(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Forward,
                contentDescription = "Forward",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Forward", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Delete button
        IconButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Email?") },
            text = { Text("This email will be permanently deleted. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
