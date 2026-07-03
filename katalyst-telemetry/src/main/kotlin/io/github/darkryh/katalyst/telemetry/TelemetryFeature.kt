package io.github.darkryh.katalyst.telemetry

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngines
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.telemetry.capture.BootCapturer
import io.github.darkryh.katalyst.telemetry.capture.ConfigCapturer
import io.github.darkryh.katalyst.telemetry.capture.EventsCapturer
import io.github.darkryh.katalyst.telemetry.capture.HttpCapturer
import io.github.darkryh.katalyst.telemetry.capture.MigrationCapturer
import io.github.darkryh.katalyst.telemetry.capture.PersistenceCapturer
import io.github.darkryh.katalyst.telemetry.capture.SchedulerCapturer
import io.github.darkryh.katalyst.telemetry.capture.SubsystemCapturer
import io.github.darkryh.katalyst.telemetry.capture.TransactionCapturer
import io.github.darkryh.katalyst.telemetry.capture.WebSocketCapturer
import io.github.darkryh.katalyst.telemetry.capture.WiringCapturer
import io.github.darkryh.katalyst.telemetry.model.HealthLevel
import io.github.darkryh.katalyst.telemetry.model.HealthSummary
import io.github.darkryh.katalyst.telemetry.store.JvmMemorySampler
import io.github.darkryh.katalyst.telemetry.store.TelemetryIdentity
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import io.github.darkryh.katalyst.telemetry.transport.RunDescriptorWriter
import io.github.darkryh.katalyst.telemetry.transport.TelemetryServer
import org.slf4j.LoggerFactory

/**
 * Auto-attaching telemetry feature. It is loaded reflectively by `KatalystApplicationBuilder` when
 * `katalyst-telemetry` is on the classpath (no compile-time edge from `katalyst-di` to this module),
 * so telemetry is decoupled and purely additive. Every side effect is wrapped so a telemetry failure
 * NEVER breaks application boot.
 *
 * The FQN of this object is a stable contract: `io.github.darkryh.katalyst.telemetry.TelemetryFeature`.
 */
object TelemetryFeature : KatalystFeature {

    override val id: String = "telemetry"

    private val logger = LoggerFactory.getLogger("TelemetryFeature")

    /** Best-effort Katalyst version stamp (kept in sync with gradle.properties `katalystVersion`). */
    private const val KATALYST_VERSION: String = "1.0.0-alpha02"

    private val capturers: List<SubsystemCapturer>
        get() = listOf(
            BootCapturer(),
            WiringCapturer(),
            HttpCapturer(),
            WebSocketCapturer(),
            PersistenceCapturer(),
            TransactionCapturer(),
            MigrationCapturer(),
            EventsCapturer(),
            SchedulerCapturer(),
            ConfigCapturer(),
        )

    @Volatile
    private var descriptorWriter: RunDescriptorWriter? = null

    @Volatile
    private var server: TelemetryServer? = null

    override fun provideBeanModules(): List<KatalystBeanModule> {
        val config = TelemetryConfig.fromEnvironment()
        if (!config.enabled) {
            logger.debug("Telemetry disabled by configuration; skipping attach")
            return emptyList()
        }

        val store = runCatching { attach(config) }
            .onFailure { logger.warn("Telemetry attach failed; app boot unaffected: {}", it.message) }
            .getOrNull()
            ?: return emptyList()

        if (config.quiet) applyQuietMode()

        // Expose the live store as a bean so in-process consumers (and the deepen pass) can read it.
        return listOf(katalystBeanModule { single { store } })
    }

    private fun attach(config: TelemetryConfig): TelemetryStore {
        val pid = runCatching { ProcessHandle.current().pid() }.getOrDefault(-1L)
        val appName = resolveAppName()
        val wsToken = java.util.UUID.randomUUID().toString()

        // Pre-resolve a concrete loopback port so identity/meta and the descriptor advertise the same
        // value the server actually binds. When a fixed port is configured we use it directly.
        val chosenPort = if (config.port > 0) config.port else pickFreePort() ?: 0

        val identity = TelemetryIdentity(
            appName = appName,
            pid = pid,
            katalystVersion = KATALYST_VERSION,
            startedAtEpochMs = System.currentTimeMillis(),
            host = config.host,
            port = chosenPort,
            snapshotPath = null,
            memoryBudgetBytes = config.memoryBudgetBytes,
        )

        val store = TelemetryStore(identity)
        TelemetryStore.activate(store)

        capturers.forEach { capturer ->
            runCatching { capturer.install(store) }
                .onFailure { logger.debug("Capturer '{}' install failed: {}", capturer.id, it.message) }
        }

        val transport = TelemetryServer(store, config.host, chosenPort, wsToken)
        val boundPort = runCatching { transport.start() }.getOrNull() ?: chosenPort
        server = transport

        val writer = RunDescriptorWriter(
            appName = appName,
            pid = pid,
            katalystVersion = KATALYST_VERSION,
            host = config.host,
            telemetryPort = boundPort,
            wsToken = wsToken,
            snapshotPath = null,
            startedAtEpochMs = identity.startedAtEpochMs,
        )
        runCatching { writer.writeBooting() }
            .onFailure { logger.debug("Run descriptor (BOOTING) write failed: {}", it.message) }
        descriptorWriter = writer

        logger.info("Telemetry attached: pid={} port={} token=****", pid, boundPort)
        return store
    }

    /**
     * Quiet mode (`-Dkatalyst.telemetry.quiet=true`): the console flood is the pain the inspector
     * exists to replace, so once telemetry is attached the root logger is raised to WARN — warnings
     * and errors still print, loggers the app pins explicitly keep their levels. Done reflectively
     * against logback (this module only depends on slf4j-api) and fully guarded: any other SLF4J
     * binding simply leaves logging untouched.
     */
    private fun applyQuietMode() {
        runCatching {
            val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
            val levelClass = Class.forName("ch.qos.logback.classic.Level")
            val warn = levelClass.getField("WARN").get(null)
            logger.info(
                "Telemetry quiet mode: raising root log level to WARN (warnings/errors still print; " +
                    "attach the katalyst-tui inspector for live state, or drop -Dkatalyst.telemetry.quiet=true to restore logs)",
            )
            root.javaClass.getMethod("setLevel", levelClass).invoke(root, warn)
        }.onFailure { logger.debug("Quiet mode unavailable (non-logback SLF4J binding?): {}", it.message) }
    }

    /** Grab an OS-assigned free loopback port, then release it for the transport to rebind. */
    private fun pickFreePort(): Int? = runCatching {
        java.net.ServerSocket(0, 1, java.net.InetAddress.getByName(TelemetryConfig.DEFAULT_HOST)).use { it.localPort }
    }.getOrNull()

    override fun onReady(context: KatalystBeanContext) {
        val store = TelemetryStore.active ?: return
        runCatching {
            store.healthProvider = {
                val containerReady = KatalystContainerProvider.currentOrNull() != null
                val engineId = runCatching { KatalystBeanEngines.activeOrNull()?.id }.getOrNull()
                HealthSummary(
                    level = if (containerReady) HealthLevel.OK else HealthLevel.DEGRADED,
                    containerReady = containerReady,
                    activeEngineId = engineId,
                    bootComplete = true,
                    jvm = runCatching { JvmMemorySampler.sample() }.getOrNull(),
                )
            }
        }.onFailure { logger.debug("Failed to set telemetry health provider: {}", it.message) }

        runCatching { descriptorWriter?.markReady() }
            .onFailure { logger.debug("Run descriptor (READY) write failed: {}", it.message) }
    }

    private fun resolveAppName(): String {
        runCatching { System.getProperty("katalyst.app.name") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        // Fall back to the launching main class' simple name when available.
        return runCatching { System.getProperty("sun.java.command") }.getOrNull()
            ?.substringBefore(' ')
            ?.substringAfterLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: "katalyst-app"
    }
}
