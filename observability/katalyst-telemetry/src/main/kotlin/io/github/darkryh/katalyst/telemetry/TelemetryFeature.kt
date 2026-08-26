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

    /**
     * Katalyst version stamp, generated from the build's `katalystVersion` (see the module's
     * `build.gradle.kts`). Previously a hand-maintained literal, which drifted nine releases behind
     * and made every snapshot report a version the running framework was not.
     */
    private val KATALYST_VERSION: String = BuildInfo.KATALYST_VERSION

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

    @Volatile
    private var transportShutdownHook: Thread? = null

    /**
     * Container-owned teardown handle. [KatalystFeature] declares only [provideBeanModules] and
     * [onReady] — there is no feature teardown callback — but the bean engine closes every
     * [AutoCloseable] bean the container holds when it stops, so a closeable bean *is* the shutdown
     * edge. Feature modules are registered before discovered components, and closing runs in reverse
     * registration order, so telemetry is torn down last and keeps observing the rest of the shutdown.
     *
     * Nothing ever resolves this bean; its only job is to be closed.
     */
    internal object Teardown : AutoCloseable {
        override fun close() = detach()
    }

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

        // Expose the live store as a bean so in-process consumers (and the deepen pass) can read it,
        // plus the closeable that hands telemetry's teardown to the container lifecycle.
        return listOf(
            katalystBeanModule {
                single { store }
                single { Teardown }
            }
        )
    }

    private fun attach(config: TelemetryConfig): TelemetryStore {
        // Release whatever a previous attach in this JVM still owns before taking new resources. A
        // second boot (embedded restart, a test suite booting repeatedly) used to overwrite `server`
        // in place, stranding the first CIO server on its bound loopback port for the whole process.
        detach()

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

        val transport = TelemetryServer(
            store = store,
            host = config.host,
            requestedPort = chosenPort,
            wsToken = wsToken,
            shutdownControlEnabled = config.shutdownControlEnabled,
        )
        val boundPort = runCatching { transport.start() }.getOrNull() ?: chosenPort
        server = transport
        installShutdownHook()

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
     * Releases everything [attach] took: the loopback transport and its bound port, the run
     * descriptor and its JVM shutdown hook, and the process-global [TelemetryStore].
     *
     * Two edges call it. [Teardown] runs it when the bean container stops, which is the application
     * shutdown path (`ApplicationStopping` -> `stopKatalystStandalone()` -> engine `stop()`), and
     * [attach] runs it first so a re-attach cannot strand the previous one. Idempotent and never
     * throws: it executes while something else is shutting down, and a telemetry failure must not
     * mask that.
     */
    private fun detach() {
        stopTransport()
        server = null
        removeTransportShutdownHook()
        runCatching { descriptorWriter?.stop() }
            .onFailure { logger.debug("Run descriptor retire failed: {}", it.message) }
        descriptorWriter = null
        // `active` is process-global while its snapshot providers read the registries of the container
        // that just went away, so a stopped app would otherwise keep serving a dead container's state.
        runCatching { TelemetryStore.clearActive() }
            .onFailure { logger.debug("Telemetry store clear failed: {}", it.message) }
    }

    /**
     * Last-resort net for a JVM that exits without stopping the container — a hard `System.exit`, or
     * an application bootstrapped without the Ktor lifecycle that never reaches
     * `stopKatalystStandalone()`. The container-driven [Teardown] is the primary path; this hook only
     * covers the case where that path never runs, and [detach] unregisters it so the hook itself is
     * not the thing that leaks.
     */
    private fun installShutdownHook() {
        if (transportShutdownHook != null) return
        runCatching {
            val hook = Thread { stopTransport() }
            Runtime.getRuntime().addShutdownHook(hook)
            transportShutdownHook = hook
        }.onFailure { logger.debug("Could not register telemetry transport shutdown hook: {}", it.message) }
    }

    private fun removeTransportShutdownHook() {
        val hook = transportShutdownHook ?: return
        transportShutdownHook = null
        // Throws once shutdown is already under way — and then the hook is running, or about to,
        // which is exactly the case it was registered for.
        runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
            .onFailure { logger.debug("Telemetry transport shutdown hook already firing: {}", it.message) }
    }

    /** Gracefully stops the currently attached transport (small grace/timeout), if any. Never throws. */
    private fun stopTransport() {
        runCatching { server?.stop() }
            .onFailure { logger.debug("Telemetry transport stop failed: {}", it.message) }
    }

    /**
     * Test-only seam: runs exactly the action the JVM shutdown hook installed by [installShutdownHook]
     * would run, without forcing a real JVM exit. Internal — not part of this module's public API.
     */
    internal fun shutdownHookActionForTest() = stopTransport()

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
