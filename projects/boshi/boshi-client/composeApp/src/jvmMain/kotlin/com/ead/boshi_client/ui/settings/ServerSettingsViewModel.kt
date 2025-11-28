package com.ead.boshi_client.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ead.boshi_client.data.network.DynamicBoshiService
import com.ead.boshi_client.data.preferences.SMTPPreferences
import com.ead.boshi_client.data.server.ServerConfig
import com.ead.boshi_client.data.server.ServerManager
import com.ead.boshi_client.data.server.ServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ServerSettingsUiState(
    val isSmtpTestingEnabled: Boolean = false,
    val serverState: ServerState = ServerState(),
    val selectedHost: String = "127.0.0.1",
    val selectedPort: Int = 8080,
    val jarPath: String? = null,
    val autoStart: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false
)

class ServerSettingsViewModel(
    private val serverManager: ServerManager,
    private val smtpPreferences: SMTPPreferences,
    private val dynamicBoshiApi: DynamicBoshiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerSettingsUiState())
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    val isSmtpTestingEnabled: StateFlow<Boolean> = smtpPreferences.isSmtpTestingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val serverState: StateFlow<ServerState> = serverManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerState())

    init {
        // Combine all preferences into UI state
        viewModelScope.launch {
            combine(
                smtpPreferences.isSmtpTestingEnabled,
                serverManager.state,
                smtpPreferences.serverHost,
                smtpPreferences.serverPort,
                smtpPreferences.serverJarPath,
                smtpPreferences.serverAutoStart
            ) { values ->
                val enabled = values[0] as Boolean
                val state = values[1] as ServerState
                val host = values[2] as String
                val port = values[3] as Int
                val jar = values[4] as String?
                val autoStart = values[5] as Boolean

                ServerSettingsUiState(
                    isSmtpTestingEnabled = enabled,
                    serverState = state,
                    selectedHost = host,
                    selectedPort = port,
                    jarPath = jar,
                    autoStart = autoStart,
                    isConnected = state.isRunning
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerSettingsUiState())
                .collect { newState ->
                    _uiState.value = newState
                }
        }
    }

    /**
     * Toggle SMTP testing mode enabled/disabled.
     */
    fun toggleSmtpTesting(enabled: Boolean) {
        viewModelScope.launch {
            smtpPreferences.setSmtpTestingEnabled(enabled)
            if (!enabled && serverManager.isRunning()) {
                stopServer()
            }
        }
    }

    /**
     * Start the SMTP server.
     */
    fun startServer() {
        viewModelScope.launch {
            updateLoading(true)
            updateError(null)

            val config = ServerConfig(
                host = _uiState.value.selectedHost,
                port = _uiState.value.selectedPort,
                jarPath = _uiState.value.jarPath
            )

            serverManager.launchServer(config)
                .onSuccess {
                    // Validate connectivity
                    val isConnected = validateConnectivity()
                    if (!isConnected) {
                        updateError("Server started but not responding. Check logs for errors.")
                    }
                }
                .onFailure { exception ->
                    updateError(exception.message ?: "Failed to start server")
                }

            updateLoading(false)
        }
    }

    /**
     * Stop the SMTP server.
     */
    fun stopServer() {
        viewModelScope.launch {
            updateLoading(true)
            serverManager.stopServer()
                .onFailure { exception ->
                    updateError(exception.message ?: "Failed to stop server")
                }
            updateLoading(false)
        }
    }

    /**
     * Validate server connectivity.
     */
    suspend fun validateConnectivity(): Boolean {
        val config = ServerConfig(
            host = _uiState.value.selectedHost,
            port = _uiState.value.selectedPort
        )

        val result = serverManager.validateConnectivity(config).getOrNull() ?: false
        return result
    }

    /**
     * Update the server host and API endpoint.
     */
    fun setServerHost(host: String) {
        viewModelScope.launch {
            smtpPreferences.setServerHost(host)
            val config = ServerConfig(
                host = host,
                port = _uiState.value.selectedPort,
                jarPath = _uiState.value.jarPath
            )
            serverManager.updateConfig(config)
            // Update API endpoint
            dynamicBoshiApi.updateEndpoint(config.toUrl())
        }
    }

    /**
     * Update the server port and API endpoint.
     */
    fun setServerPort(port: Int) {
        viewModelScope.launch {
            smtpPreferences.setServerPort(port)
            val config = ServerConfig(
                host = _uiState.value.selectedHost,
                port = port,
                jarPath = _uiState.value.jarPath
            )
            serverManager.updateConfig(config)
            // Update API endpoint
            dynamicBoshiApi.updateEndpoint(config.toUrl())
        }
    }

    /**
     * Set the JAR file path.
     */
    fun setJarPath(path: String?) {
        viewModelScope.launch {
            smtpPreferences.setServerJarPath(path)
            serverManager.updateConfig(
                ServerConfig(
                    host = _uiState.value.selectedHost,
                    port = _uiState.value.selectedPort,
                    jarPath = path
                )
            )
        }
    }

    /**
     * Toggle auto-start setting.
     */
    fun setAutoStart(enabled: Boolean) {
        viewModelScope.launch {
            smtpPreferences.setServerAutoStart(enabled)
        }
    }

    /**
     * Auto-discover JAR path.
     */
    fun autoDiscoverJar() {
        viewModelScope.launch {
            updateLoading(true)
            updateError(null)

            serverManager.findBoshiServerJar()
                .let { jarFile ->
                    if (jarFile != null) {
                        setJarPath(jarFile.absolutePath)
                        updateError(null)
                    } else {
                        updateError("Could not find boshi-server JAR. Please select it manually.")
                    }
                }

            updateLoading(false)
        }
    }

    /**
     * Clear server logs.
     */
    fun clearLogs() {
        serverManager.clearLogs()
    }

    /**
     * Get API endpoint URL for current server configuration.
     */
    fun getApiEndpointUrl(): String {
        return ServerConfig(
            host = _uiState.value.selectedHost,
            port = _uiState.value.selectedPort
        ).toUrl()
    }

    private fun updateLoading(loading: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = loading)
    }

    private fun updateError(error: String?) {
        _uiState.value = _uiState.value.copy(error = error)
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            serverManager.dispose()
        }
    }
}
