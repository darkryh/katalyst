package com.ead.boshi_client.ui.smtptesting

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ead.boshi_client.ui.settings.ServerSettingsViewModel
import com.ead.boshi_client.ui.settings.ServerSettingsUiState
import com.ead.boshi_client.ui.settings.components.LogViewerSection
import com.ead.boshi_client.ui.settings.components.NetworkConfigSection
import com.ead.boshi_client.ui.settings.components.ServerControlSection
import com.ead.boshi_client.data.server.ServerState
import org.koin.compose.koinInject

@Composable
fun SMTPTestingScreen(
    viewModel: ServerSettingsViewModel = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val serverState by viewModel.serverState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with title and description
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "SMTP Server Testing",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Launch and manage the boshi-server locally. Use this to validate SMTP functionality with multiple client instances.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Main content area - split layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Left column: Server control and network config
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Server Control Card
                ServerControlSection(
                    status = serverState.status,
                    isLoading = uiState.isLoading,
                    jarPath = uiState.jarPath,
                    error = uiState.error,
                    onStartServer = { viewModel.startServer() },
                    onStopServer = { viewModel.stopServer() },
                    onSelectJar = { viewModel.autoDiscoverJar() },
                    modifier = Modifier.fillMaxWidth()
                )

                // Network Config Card
                NetworkConfigSection(
                    selectedHost = uiState.selectedHost,
                    selectedPort = uiState.selectedPort,
                    onHostChange = { viewModel.setServerHost(it) },
                    onPortChange = { viewModel.setServerPort(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Right column: Logs viewer (full height)
            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight()
                    .padding(start = 12.dp)
            ) {
                LogViewerSection(
                    logs = serverState.logs,
                    onClearLogs = { viewModel.clearLogs() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
