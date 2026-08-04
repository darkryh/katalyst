package io.github.darkryh.katalyst.di

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.config.ApplicationConfigProvider
import io.github.darkryh.katalyst.di.config.stopKatalystStandalone
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.di.internal.KtorModuleRegistry
import io.github.darkryh.katalyst.di.lifecycle.StartupHook
import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import io.github.darkryh.katalyst.ktor.KtorModule
import io.ktor.events.Events
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
import io.ktor.server.application.serverConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.util.logging.KtorSimpleLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Katalyst's teardown is process-wide, but `ApplicationStopping` is a *per-Application* event.
 *
 * Ktor raises it on every dev-mode hot reload — `EmbeddedServer.reloadApplication()` builds the
 * replacement `Application` first and only then stops the superseded one — so wiring the global
 * teardown to that event made the first recompile close the pool, cancel the scheduler and reset
 * [KatalystContainerProvider] for good. `builder.initializeDI()` runs once, before the server
 * starts, so nothing ever bootstraps Katalyst again and every later request fails.
 *
 * The tests drive Ktor's real reload machinery ([EmbeddedServer.reload]) rather than replaying
 * events by hand, so the event order under test is the one Ktor actually produces.
 */
class KatalystApplicationHotReloadTest {

    private val listening = CountDownLatch(1)
    private val releaseEngine = CountDownLatch(1)
    private val bootFailure = AtomicReference<Throwable>()
    private var applicationThread: Thread? = null
    private var server: EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>? = null

    @AfterTest
    fun tearDown() {
        releaseEngine.countDown()
        applicationThread?.join(TimeUnit.SECONDS.toMillis(30))
        stopKatalystStandalone()
        RegistryManager.resetAll()
    }

    @Test
    fun `a hot reload keeps the process-wide Katalyst container alive`() {
        val probe = ProbeKtorModule()
        val server = boot(probe)

        assertNotNull(KatalystContainerProvider.currentOrNull(), "boot must leave a live container")
        assertEquals(1, probe.installs.get(), "the booted application must have the discovered module installed")

        // Exactly what a `watchPaths` recompile does.
        server.reload()

        assertNotNull(
            KatalystContainerProvider.currentOrNull(),
            "a hot reload tore down the process-wide Katalyst container - the reloaded application " +
                "has no DI left and nothing re-bootstraps it"
        )
        assertEquals(
            2,
            probe.installs.get(),
            "the reloaded application must be configured with the discovered Ktor modules too"
        )
    }

    @Test
    fun `stopping the server tears Katalyst down before the start call returns`() {
        val server = boot(ProbeKtorModule())
        assertNotNull(KatalystContainerProvider.currentOrNull(), "boot must leave a live container")

        val stoppedObserved = AtomicBoolean(false)
        val containerAliveWhenStopped = AtomicBoolean(false)
        server.monitor.subscribe(ApplicationStopped) {
            stoppedObserved.set(true)
            containerAliveWhenStopped.set(KatalystContainerProvider.currentOrNull() != null)
        }

        // The engine is still parked inside `start(wait = true)`, so `katalystApplication`'s
        // `finally` has not run: whatever tears Katalyst down here did so from the shutdown
        // subscription alone. That is the path a SIGINT takes - Ktor's own JVM shutdown hook
        // calls `EmbeddedServer.stop()`, and the JVM can halt before main's `finally` is reached.
        server.stop(gracePeriodMillis = 0, timeoutMillis = 0)

        assertTrue(stoppedObserved.get(), "the server shutdown must have run the Ktor stop sequence")
        assertNull(
            KatalystContainerProvider.currentOrNull(),
            "a real server shutdown must still tear Katalyst down"
        )
        assertEquals(
            false,
            containerAliveWhenStopped.get(),
            "teardown must happen during the stop sequence, not only in the main thread's finally"
        )
    }

    private fun boot(probe: ProbeKtorModule): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {
        val embeddedServer = buildServer()
        server = embeddedServer

        val thread = Thread({
            runCatching {
                katalystApplication {
                    configuration(deploymentConfiguration())
                    database(inMemoryDb())
                    beanEngine(TestBeanEngine())
                    engine(embeddedServer)
                    features { feature(RegistryProbeFeature(probe)) }
                }
            }.onFailure(bootFailure::set)
        }, "katalyst-hot-reload-test").apply { isDaemon = true }

        applicationThread = thread
        thread.start()

        val reachedEngine = listening.await(60, TimeUnit.SECONDS)
        bootFailure.get()?.let { throw it }
        assertTrue(reachedEngine, "the application never reached the engine start")

        return embeddedServer
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildServer(): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {
        val environment = HotReloadTestEnvironment()
        val rootConfig = serverConfig(environment) { watchPaths = emptyList() }
        return EmbeddedServer(rootConfig, ParkedEngineFactory(listening, releaseEngine)) { }
            as EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>
    }

    /** The `ktor.deployment.*` keys `ServerDeploymentConfigurationLoader` requires. */
    private fun deploymentConfiguration() = ApplicationConfigProvider(
        MapApplicationConfig(
            "ktor.deployment.host" to "127.0.0.1",
            "ktor.deployment.port" to "8080",
            "ktor.deployment.shutdownGracePeriod" to "1000",
            "ktor.deployment.shutdownTimeout" to "5000",
            "ktor.deployment.connectionGroupSize" to "1",
            "ktor.deployment.workerGroupSize" to "1",
            "ktor.deployment.callGroupSize" to "1",
            "ktor.deployment.maxInitialLineLength" to "4096",
            "ktor.deployment.maxHeaderSize" to "8192",
            "ktor.deployment.maxChunkSize" to "8192",
            "ktor.deployment.connectionIdleTimeoutMs" to "180000",
        )
    )

    private fun inMemoryDb() = DatabaseConfig(
        url = "jdbc:h2:mem:hot-reload-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        driver = "org.h2.Driver",
        username = "sa",
        password = "",
        maxPoolSize = 4,
        minIdleConnections = 1,
        connectionTimeout = 3000,
        idleTimeout = 10000,
        maxLifetime = 30000,
        autoCommit = false,
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
    )
}

private class HotReloadTestEnvironment : ApplicationEnvironment {
    override val classLoader: ClassLoader = Thread.currentThread().contextClassLoader
    override val log = KtorSimpleLogger("KatalystApplicationHotReloadTest")
    override val config = MapApplicationConfig()

    @Deprecated("Use Application.monitor", level = DeprecationLevel.WARNING)
    override val monitor: Events = Events()
}

/**
 * Blocks in `start(wait = true)` like a real engine, and - deliberately - keeps blocking after
 * `stop()`. That parks `katalystApplication`'s `finally` so a test can attribute a teardown to
 * the shutdown subscription instead of to the main thread unwinding.
 */
private class ParkedEngine(
    override val environment: ApplicationEnvironment,
    private val monitor: Events,
    private val listening: CountDownLatch,
    private val release: CountDownLatch,
) : ApplicationEngine {

    override fun start(wait: Boolean): ApplicationEngine {
        monitor.raise(ServerReady, environment)
        listening.countDown()
        if (wait) release.await(60, TimeUnit.SECONDS)
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) = Unit

    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = emptyList()
}

private class ParkedEngineFactory(
    private val listening: CountDownLatch,
    private val release: CountDownLatch,
) : ApplicationEngineFactory<ParkedEngine, ApplicationEngine.Configuration> {

    override fun configuration(
        configure: ApplicationEngine.Configuration.() -> Unit
    ): ApplicationEngine.Configuration = ApplicationEngine.Configuration().apply(configure)

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: ApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): ParkedEngine = ParkedEngine(environment, monitor, listening, release)
}

/** Counts how many Ktor `Application` instances Katalyst configured. */
private class ProbeKtorModule : KtorModule {
    val installs = AtomicInteger()
    override fun install(application: Application) {
        installs.incrementAndGet()
    }
}

/**
 * Publishes [probe] the way component scanning publishes a discovered route function: into
 * [KtorModuleRegistry], during bootstrap, after the registries have been reset.
 */
private class RegistryProbeFeature(private val probe: KtorModule) : KatalystFeature {
    override val id: String = "hot-reload-registry-probe"

    override fun provideBeanModules(): List<KatalystBeanModule> = listOf(
        katalystBeanModule {
            single<StartupHook> { RegistryProbeHook(probe) }
        }
    )
}

private class RegistryProbeHook(private val probe: KtorModule) : StartupHook {
    override val id: String = "RegistryProbeHook"

    override suspend fun onStartup() {
        KtorModuleRegistry.register(probe)
    }
}
