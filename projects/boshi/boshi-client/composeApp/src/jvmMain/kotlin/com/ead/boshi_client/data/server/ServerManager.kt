package com.ead.boshi_client.data.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.net.Socket
import java.util.concurrent.TimeUnit

class ServerManager {
    private val _state = MutableStateFlow(ServerState())
    val state = _state.asStateFlow()

    private var serverProcess: Process? = null
    private var logReaderThread: Thread? = null

    /**
     * Launch the boshi-server JAR with the configured settings.
     * Captures logs in real-time and updates state.
     */
    suspend fun launchServer(config: ServerConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _state.value = _state.value.copy(logs = emptyList())  // Clear old logs
            if (serverProcess?.isAlive == true) {
                throw IllegalStateException("Server is already running")
            }

            _state.value = _state.value.copy(
                status = ServerStatus.STARTING,
                error = null,
                logs = emptyList()
            ).withLog("Initializing server launch...")

            // Optionally rebuild the server jar before launch
            rebuildServerJar()

            val jarFile = config.jarPath?.let { File(it) }
            if (jarFile != null && !jarFile.exists()) {
                throw IllegalArgumentException("JAR file not found: ${config.jarPath}")
            }

            if (jarFile == null) {
                throw IllegalArgumentException("JAR path not configured. Please select a server JAR file.")
            }

            // Always prefer the newest jar in the same directory to avoid running stale builds
            val selectedJar = pickLatestJar(jarFile)

            _state.value = _state.value.withLog("JAR path: ${selectedJar.absolutePath}")
            _state.value = _state.value.withLog("Server config: host=${config.host}, port=${config.port}")

            // Create a wrapper script with the proper process name
            val wrapperScript = createWrapperScript(selectedJar, config)
            _state.value = _state.value.withLog("Wrapper script: ${wrapperScript.absolutePath}")

            // Build ProcessBuilder to launch the wrapper script
            val command = listOf(
                "bash",
                wrapperScript.absolutePath
            )

            _state.value = _state.value.withLog("Launching: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command)

            // Redirect error stream to standard output
            processBuilder.redirectErrorStream(true)

            // Start the process
            try {
                serverProcess = processBuilder.start()
                _state.value = _state.value.withLog("✓ Process started successfully (PID via wrapper script)")
            } catch (e: Exception) {
                _state.value = _state.value.withLog("✗ Failed to start process: ${e.message}")
                throw e
            }

            // Start log reader thread
            startLogReader()

            _state.value = _state.value.copy(
                status = ServerStatus.RUNNING,
                isRunning = true,
                config = config
            ).withLog("Server is running. Waiting for startup logs...")
        }
    }

    /**
     * Run a Gradle build to refresh the boshi-server jar before launching.
     * Best-effort; logs any errors and continues with existing jar if build fails.
     */
    private fun rebuildServerJar() {
        val projectRoot = File(System.getProperty("user.dir"))
        val gradlew = File(projectRoot, "projects/boshi/gradlew")
        if (!gradlew.exists()) {
            _state.value = _state.value.withLog("Gradle wrapper not found at projects/boshi/gradlew; skipping rebuild")
            return
        }

        val cmd = listOf(
            gradlew.absolutePath,
            ":boshi-server:boshi-app:build",
            "-x", "test"
        )

        _state.value = _state.value.withLog("Rebuilding server jar: ${cmd.joinToString(" ")}")

        try {
            val process = ProcessBuilder(cmd)
                .directory(File(projectRoot, "projects/boshi"))
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    _state.value = _state.value.withLog(line)
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                _state.value = _state.value.withLog("Gradle build failed (exit $exitCode); using existing jar")
            } else {
                _state.value = _state.value.withLog("Gradle build succeeded; using freshly built jar")
            }
        } catch (e: Exception) {
            _state.value = _state.value.withLog("Gradle build error: ${e.message}; using existing jar")
        }
    }

    /**
     * Create a temporary wrapper script that will show the correct process name.
     */
    private fun createWrapperScript(jarFile: File, config: ServerConfig): File {
        val tmpDir = File(System.getProperty("java.io.tmpdir"))
        val scriptFile = File(tmpDir, "boshi-server-${config.port}.sh")

        val scriptContent = buildString {
            appendLine("#!/bin/bash")
            append("exec java -jar \"${jarFile.absolutePath}\" ")
            // Profile configs first, then CLI overrides so -P wins
            //append("-config=application.yaml -config=application-${'$'}{KATALYST_PROFILE:-dev}.yaml ")
            //append("-P:ktor.deployment.host=${config.host} ")
            append("-P:ktor.deployment.port=${config.port}")
            appendLine()
        }

        scriptFile.writeText(scriptContent)
        scriptFile.setExecutable(true)

        return scriptFile
    }

    /**
     * If there is a newer boshi-app-*.jar in the same folder, prefer it.
     */
    private fun pickLatestJar(current: File): File {
        val parent = current.parentFile ?: return current
        if (!parent.exists()) return current

        val latest = parent.listFiles { file ->
            file.isFile && file.name.startsWith("boshi-app-") && file.name.endsWith(".jar")
        }?.maxByOrNull { it.lastModified() } ?: return current

        return if (latest.lastModified() > current.lastModified() && latest.absolutePath != current.absolutePath) {
            _state.value = _state.value.withLog("Found newer JAR: ${latest.name}, using it instead of ${current.name}")
            latest
        } else {
            current
        }
    }

    /**
     * Stop the running server process.
     */
    suspend fun stopServer(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _state.value = _state.value.copy(status = ServerStatus.STOPPING)

            serverProcess?.let {
                if (it.isAlive) {
                    // Try graceful shutdown first
                    it.destroy()

                    // Wait up to 5 seconds for graceful shutdown
                    if (!it.waitFor(5, TimeUnit.SECONDS)) {
                        // Force kill if it doesn't shutdown
                        it.destroyForcibly()
                        it.waitFor()
                    }
                }
            }

            logReaderThread?.interrupt()
            serverProcess = null
            logReaderThread = null

            _state.value = _state.value.copy(
                status = ServerStatus.STOPPED,
                isRunning = false
            )
        }
    }

    /**
     * Check if the server is responding to HTTP requests.
     */
    suspend fun validateConnectivity(config: ServerConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                Socket(config.host, config.port).use { socket ->
                    socket.soTimeout = 2000
                    true
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Clear all logs from the state.
     */
    fun clearLogs() {
        _state.value = _state.value.clearLogs()
    }

    /**
     * Find boshi-server JAR (boshi-app) in common build output locations.
     */
    suspend fun findBoshiServerJar(): File? = withContext(Dispatchers.IO) {
        // Search in common gradle build output locations
        val searchPaths = listOf(
            "projects/boshi/boshi-server/boshi-app/build/libs",
            "../boshi-server/boshi-app/build/libs",
            "../../boshi/boshi-server/boshi-app/build/libs"
        )

        for (path in searchPaths) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                val jar = dir.listFiles { file ->
                    file.name.startsWith("boshi-app-") && file.name.endsWith(".jar")
                }?.maxByOrNull { it.lastModified() }

                if (jar != null) {
                    return@withContext jar
                }
            }
        }

        null
    }

    /**
     * Start a background thread to read server output logs.
     */
    private fun startLogReader() {
        logReaderThread = Thread {
            try {
                serverProcess?.inputStream?.let { inputStream ->
                    val reader = InputStreamReader(inputStream)
                    val bufferedReader = reader.buffered()

                    var line: String? = bufferedReader.readLine()
                    while (line != null && !Thread.currentThread().isInterrupted) {
                        _state.value = _state.value.withLog(line)
                        line = bufferedReader.readLine()
                    }

                    bufferedReader.close()
                    reader.close()
                }
            } catch (e: Exception) {
                if (e !is InterruptedException) {
                    _state.value = _state.value.withLog("Log reader error: ${e.message}")
                    _state.value = _state.value.copy(
                        error = "Log reader error: ${e.message}",
                        status = ServerStatus.ERROR
                    )
                }
            } finally {
                // Check if process died unexpectedly
                Thread.sleep(100) // Give a moment for final state updates
                if (serverProcess?.isAlive == false && _state.value.isRunning) {
                    val exitCode = try {
                        serverProcess?.exitValue() ?: -1
                    } catch (e: Exception) {
                        -1
                    }
                    _state.value = _state.value.copy(
                        status = ServerStatus.ERROR,
                        isRunning = false,
                        error = "Server process stopped unexpectedly (exit code: $exitCode). Check logs for details."
                    ).withLog("✗ Process terminated with exit code: $exitCode")
                }
            }
        }.apply {
            isDaemon = true
            name = "BoshiServerLogReader"
            start()
        }
    }

    /**
     * Get the current server state.
     */
    fun getCurrentState(): ServerState = _state.value

    /**
     * Update the server configuration.
     */
    fun updateConfig(config: ServerConfig) {
        _state.value = _state.value.copy(config = config)
    }

    /**
     * Check if server is currently running.
     */
    fun isRunning(): Boolean = serverProcess?.isAlive == true

    /**
     * Clean up resources when manager is destroyed.
     */
    suspend fun dispose() {
        if (isRunning()) {
            stopServer()
        }
    }
}
