package com.ead.boshi_client.ui.email.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ead.boshi_client.ui.util.Attachment

@Composable
fun EmailAttachmentList(
    attachments: List<Attachment>,
    onDownload: (Attachment) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Attachments (${attachments.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        attachments.forEach { attachment ->
            AttachmentCard(
                attachment = attachment,
                onDownload = { onDownload(attachment) }
            )
        }
    }
}

@Composable
private fun AttachmentCard(
    attachment: Attachment,
    onDownload: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = "File",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                
                Column {
                    Text(
                        text = attachment.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatFileSize(attachment.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download ${attachment.fileName}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
