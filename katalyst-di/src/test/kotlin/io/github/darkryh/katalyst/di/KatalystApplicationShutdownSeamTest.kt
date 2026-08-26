package io.github.darkryh.katalyst.di

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.lifecycle.ApplicationShutdown
import io.github.darkryh.katalyst.core.lifecycle.ShutdownRequest
import io.github.darkryh.katalyst.di.config.ApplicationConfigProvider
import io.github.darkryh.katalyst.di.config.stopKatalystStandalone
import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import io.ktor.events.Events
import io.ktor.server.application.ApplicationEnvironment
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End to end for the seam the TUI inspector's `/shutdown` ultimately pulls: a real
 * `katalystApplication` boot, a request through [ApplicationShutdown], and the application actually
 * stopping.
 *
 * The unit tests either side of this prove their own halves — [ApplicationShutdown] fires once and
 * off-thread, the transport gates who may ask — but neither can prove the part that matters most:
 * that what the entry point installs *is a real shutdown*. That is the failure this whole area is
 * about. `/shutdown` used to close the inspector and leave the backend running, and a seam that was
 * installed but wired to something that did not stop the application would reproduce exactly that
 * bug one layer down, with tests passing.
 *
 * So this asserts the whole chain: boot publishes an action, requesting it stops the server, that
 * stop tears Katalyst down, and the entry point returns with the seam withdrawn.
 */
class KatalystApplicationShutdownSeamTest {

    private val listening = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val stopCalls = AtomicInteger()
    private val bootFailure = AtomicReference<Throwable>()
    private var applicationThread: Thread? = null

    @AfterTest
    fun tearDown() {
        release.countDown()
        applicationThread?.join(TimeUnit.SECONDS.toMillis(30))
        ApplicationShutdown.uninstall()
        stopKatalystStandalone()
        RegistryManager.resetAll()
    }

    @Test
    fun `booting publishes a way to stop the application`() {
        boot()

        assertTrue(
            ApplicationShutdown.isSupported,
            "katalystApplication must publish a shutdown action - without it the transport can only " +
                "report 'unsupported' and /shutdown is back to doing nothing",
        )
        assertFalse(ApplicationShutdown.isRequested)
    }

    @Test
    fun `requesting a shutdown really stops the application`() {
        boot()

        assertEquals(ShutdownRequest.Accepted, ApplicationShutdown.request("test"))

        // The entry point returns only when start(wait = true) is released, and only the engine's
        // own stop() releases it. The thread finishing IS the proof that a real shutdown ran — a
        // seam wired to something that did not stop the server would leave it parked here forever.
        applicationThread?.join(TimeUnit.SECONDS.toMillis(30))
        bootFailure.get()?.let { throw it }

        assertFalse(applicationThread!!.isAlive, "the application never came out of start(wait = true)")
        // At least once, not exactly once: the entry point's finally stops the server again on its
        // way out (it has always done that, and EmbeddedServer.stop is safe to repeat). The
        // "at most one teardown" guarantee is ApplicationShutdown's, and is pinned in its own test
        // where the action can be counted directly.
        assertTrue(stopCalls.get() >= 1, "the request must have stopped the embedded server")
        assertNull(
            KatalystContainerProvider.currentOrNull(),
            "stopping the server must tear Katalyst down - the same teardown SIGINT gets",
        )
    }

    @Test
    fun `the seam is withdrawn once the application is gone`() {
        boot()
        ApplicationShutdown.request("test")
        applicationThread?.join(TimeUnit.SECONDS.toMillis(30))

        assertFalse(
            ApplicationShutdown.isSupported,
            "a stopped application must stop advertising a shutdown, or the transport would accept " +
                "a request for a container that no longer exists",
        )
        assertEquals(ShutdownRequest.Unsupported, ApplicationShutdown.request("after"))
    }

    @Test
    fun `a second request cannot start a second teardown`() {
        boot()

        assertEquals(ShutdownRequest.Accepted, ApplicationShutdown.request("first"))
        // Two inspectors, or one impatient user. The engine must be stopped once.
        val second = ApplicationShutdown.request("second")

        applicationThread?.join(TimeUnit.SECONDS.toMillis(30))

        assertTrue(
            second == ShutdownRequest.AlreadyRequested || second == ShutdownRequest.Unsupported,
            "a second request must be refused (already running, or the seam was already withdrawn), " +
                "got $second",
        )
    }

    private fun boot() {
        val server = buildServer()
        val thread = Thread({
            runCatching {
                katalystApplication {
                    configuration(deploymentConfiguration())
                    database(inMemoryDb())
                    beanEngine(TestBeanEngine())
                    engine(server)
                }
            }.onFailure(bootFailure::set)
        }, "katalyst-shutdown-seam-test").apply { isDaemon = true }

        applicationThread = thread
        thread.start()

        val started = listening.await(60, TimeUnit.SECONDS)
        bootFailure.get()?.let { throw it }
        assertTrue(started, "the application never reached the engine start")
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildServer(): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {
        val environment = ShutdownSeamTestEnvironment()
        val rootConfig = serverConfig(environment) { watchPaths = emptyList() }
        return EmbeddedServer(rootConfig, StoppableEngineFactory(listening, release, stopCalls)) { }
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
        url = "jdbc:h2:mem:shutdown-seam-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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

private class ShutdownSeamTestEnvironment : ApplicationEnvironment {
    override val classLoader: ClassLoader = Thread.currentThread().contextClassLoader
    override val log = KtorSimpleLogger("KatalystApplicationShutdownSeamTest")
    override val config = MapApplicationConfig()

    @Deprecated("Use Application.monitor", level = DeprecationLevel.WARNING)
    override val monitor: Events = Events()
}

/**
 * Parks in `start(wait = true)` like a real engine and — unlike the hot-reload test's engine —
 * releases that park when stopped.
 *
 * That release is the point. It is what lets the entry point unwind exactly as it does under a real
 * shutdown, so the test can assert on what happens *after* the server stops (teardown, the seam
 * being withdrawn) rather than only on the stop itself.
 */
private class StoppableEngine(
    override val environment: ApplicationEnvironment,
    private val monitor: Events,
    private val listening: CountDownLatch,
    private val release: CountDownLatch,
    private val stopCalls: AtomicInteger,
) : ApplicationEngine {

    override fun start(wait: Boolean): ApplicationEngine {
        monitor.raise(ServerReady, environment)
        listening.countDown()
        if (wait) release.await(60, TimeUnit.SECONDS)
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        stopCalls.incrementAndGet()
        release.countDown()
    }

    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = emptyList()
}

private class StoppableEngineFactory(
    private val listening: CountDownLatch,
    private val release: CountDownLatch,
    private val stopCalls: AtomicInteger,
) : ApplicationEngineFactory<StoppableEngine, ApplicationEngine.Configuration> {

    override fun configuration(
        configure: ApplicationEngine.Configuration.() -> Unit
    ): ApplicationEngine.Configuration = ApplicationEngine.Configuration().apply(configure)

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: ApplicationEngine.Configuration,
        applicationProvider: () -> io.ktor.server.application.Application,
    ): StoppableEngine = StoppableEngine(environment, monitor, listening, release, stopCalls)
}
