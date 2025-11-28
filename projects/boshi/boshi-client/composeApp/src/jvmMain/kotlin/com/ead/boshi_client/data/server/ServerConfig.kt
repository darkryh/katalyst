package com.ead.boshi_client.data.server

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val jarPath: String? = null,
    val autoStart: Boolean = false
) {
    fun toUrl(): String = "http://$host:$port"

    companion object {
        fun localhost(): ServerConfig = ServerConfig(host = "127.0.0.1", port = 8080)
    }
}

enum class ServerStatus {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR
}

data class ServerState(
    val status: ServerStatus = ServerStatus.IDLE,
    val isRunning: Boolean = false,
    val config: ServerConfig = ServerConfig(),
    val logs: List<String> = emptyList(),
    val error: String? = null,
    val lastLogUpdate: Long = 0
) {
    fun withLog(message: String): ServerState = this.copy(
        logs = (logs + message).takeLast(1000), // Keep last 1000 lines
        lastLogUpdate = System.currentTimeMillis()
    )

    fun clearLogs(): ServerState = this.copy(logs = emptyList())
}
