package io.github.darkryh.katalyst.di

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.di.config.ApplicationConfigProvider
import io.github.darkryh.katalyst.di.config.stopKatalystStandalone
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.di.internal.KtorModuleRegistry
import io.github.darkryh.katalyst.di.lifecycle.ReadyHook
import io.github.darkryh.katalyst.di.lifecycle.ReadyHookRegistry
import io.github.darkryh.katalyst.di.lifecycle.ShutdownHook
import io.github.darkryh.katalyst.di.lifecycle.ShutdownHookRegistry
import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import io.github.darkryh.katalyst.ktor.KtorModule
import io.ktor.events.Events
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.ServerReady
import io.ktor.server.application.serverConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The regression test for the defect this whole phase exists to fix.
 *
 * Katalyst used to tear itself down from Ktor's `ApplicationStopping`, and because it subscribes
 * before `start()` while an application's own modules subscribe during it, Katalyst always went
 * first. The connection pool closed while application background work — the polling loops
 * [ReadyHook] explicitly invites — was still querying, and a shutdown produced
 * `SQLSTATE 08006 / Socket closed` followed by
 * `IllegalStateException: No transaction manager for db ExposedDatabase[...]`.
 *
 * Everything below is one shape: boot a real application with a worker polling the database as fast
 * as it can, shut it down, and require that **not one query failed**. That is the only assertion that
 * actually pins the fix, because the bug is a race — an ordering assertion alone would pass on a
 * lucky run.
 */
class ShutdownPhaseOrderingTest {

    private val pollFailures = CopyOnWriteArrayList<String>()
    private val pollsCompleted = AtomicInteger()
    private val stoppingSawUsableDatabase = AtomicReference<Boolean?>(null)
    private val shutdownHookSawUsableDatabase = AtomicReference<Boolean?>(null)
    private val featureStopped = AtomicBoolean(false)
    private val featureReadyRan = AtomicBoolean(false)
    private val readyHookRan = AtomicBoolean(false)
    private val workers = CopyOnWriteArrayList<PollingWorker>()

    private var applicationThread: Thread? = null
    private var server: EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>? = null
    private val bootFailure = AtomicReference<Throwable?>(null)
    private val listening = CountDownLatch(1)

    @BeforeTest
    fun setUp() {
        RegistryManager.resetAll()
    }

    @AfterTest
    fun tearDown() {
        runCatching { server?.stop(0, 5_000) }
        applicationThread?.join(TimeUnit.SECONDS.toMillis(30))
        workers.forEach { it.forceStop() }
        workers.clear()
        stopKatalystStandalone()
        RegistryManager.resetAll()
    }

    @Test
    fun `background work started by a ReadyHook never sees a closed pool`() {
        bootAndShutDown()

        assertTrue(
            pollsCompleted.get() > 0,
            "the worker never ran, so this proves nothing - it has to have been polling at shutdown",
        )
        assertEquals(
            emptyList(),
            pollFailures.toList(),
            "background work must not outlive the database it was told to stop using",
        )
    }

    @Test
    fun `an ApplicationStopping subscriber still has a working database`() {
        // The Ktor-native idiom, and the one real applications already use. It runs before Katalyst
        // touches anything now, so a consumer that stops its workers there finds the pool intact.
        bootAndShutDown()

        assertEquals(true, stoppingSawUsableDatabase.get(), "ApplicationStopping ran after the teardown")
    }

    @Test
    fun `a ShutdownHook still has a working database`() {
        bootAndShutDown()

        assertEquals(
            true,
            shutdownHookSawUsableDatabase.get(),
            "a shutdown hook has to be able to do final database work - that is the point of running " +
                "it before the teardown rather than after",
        )
    }

    @Test
    fun `features are stopped as part of the shutdown phase`() {
        bootAndShutDown()

        assertTrue(featureStopped.get(), "KatalystFeature.onShutdown was never called")
    }

    @Test
    fun `the teardown still completes - the container is gone afterwards`() {
        bootAndShutDown()

        assertEquals(null, KatalystContainerProvider.currentOrNull())
    }

    @Test
    fun `repeated boot and shutdown cycles never produce a failed query`() {
        // The anti-regression that matters. The defect was probabilistic - whether a tick happened to
        // be in flight in the few milliseconds between two handlers - so a single clean shutdown is
        // not evidence. Every iteration starts a worker hammering the database and stops it.
        repeat(CYCLES) { iteration ->
            reset()
            bootAndShutDown()
            assertTrue(
                pollsCompleted.get() > 0,
                "iteration $iteration never polled, so it did not exercise the race",
            )
            assertEquals(
                emptyList(),
                pollFailures.toList(),
                "iteration $iteration lost a query to the shutdown",
            )
        }
    }

    private fun reset() {
        runCatching { server?.stop(0, 5_000) }
        applicationThread?.join(TimeUnit.SECONDS.toMillis(30))
        workers.forEach { it.forceStop() }
        workers.clear()
        stopKatalystStandalone()
        RegistryManager.resetAll()
        pollFailures.clear()
        pollsCompleted.set(0)
        stoppingSawUsableDatabase.set(null)
        shutdownHookSawUsableDatabase.set(null)
        featureStopped.set(false)
        bootFailure.set(null)
        applicationThread = null
        server = null
    }

    /** Boots a real application with a database-polling worker, then stops it the way SIGINT does. */
    private fun bootAndShutDown() {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val embedded = buildServer(started, release)
        server = embedded

        val thread = Thread({
            runCatching {
                katalystApplication {
                    configuration(deploymentConfiguration())
                    database(inMemoryDb())
                    beanEngine(TestBeanEngine())
                    engine(embedded)
                    features { feature(ProbeFeature(this@ShutdownPhaseOrderingTest)) }
                }
            }.onFailure(bootFailure::set)
        }, "shutdown-phase-test").apply { isDaemon = true }
        applicationThread = thread
        thread.start()

        // Wait in slices so a bootstrap that threw surfaces its own exception immediately instead of
        // being reported thirty seconds later as "never started".
        var reachedStart = false
        repeat(BOOT_WAIT_SLICES) {
            if (started.await(BOOT_WAIT_SLICE_MILLIS, TimeUnit.MILLISECONDS)) {
                reachedStart = true
                return@repeat
            }
            bootFailure.get()?.let { throw it }
        }
        bootFailure.get()?.let { throw it }
        assertTrue(reachedStart, "the application never reached the engine start")

        // Wait for the worker to have actually completed a pass, rather than sleeping a fixed
        // interval and hoping. A pass is forty statements, and on a loaded CI runner that took
        // longer than a hand-picked sleep allowed - which made this an assertion about the runner's
        // speed instead of about the shutdown.
        val warmupDeadlineNanos = System.nanoTime() + POLL_WARMUP_TIMEOUT_MILLIS * 1_000_000
        while (pollsCompleted.get() == 0 && pollFailures.isEmpty() && System.nanoTime() < warmupDeadlineNanos) {
            Thread.sleep(SETTLE_POLL_MILLIS)
        }
        assertTrue(
            pollsCompleted.get() > 0 || pollFailures.isNotEmpty(),
            "the worker never started, so this run proves nothing: featureReady=${featureReadyRan.get()} " +
                "readyHook=${readyHookRan.get()} polls=${pollsCompleted.get()} " +
                "failures=${pollFailures.toList()}",
        )

        embedded.stop(0, 5_000)
        thread.join(TimeUnit.SECONDS.toMillis(30))
        bootFailure.get()?.let { throw it }
        assertFalse(thread.isAlive, "the application never came out of start(wait = true)")
        settle()
    }

    /**
     * Waits until the worker is observably finished, or until it proves it is not.
     *
     * Without this the assertions race the worker instead of testing it: the teardown has happened
     * by the time the application thread joins, but a worker that was NOT stopped is most likely
     * parked in its inter-poll delay at that instant and has not yet touched the closed pool. Waiting
     * for it to either stop or fail is what makes "no query failed" mean something in both directions.
     */
    private fun settle() {
        val worker = workers.lastOrNull() ?: return
        val deadlineNanos = System.nanoTime() + SETTLE_TIMEOUT_MILLIS * 1_000_000
        while (worker.isRunning() && pollFailures.isEmpty() && System.nanoTime() < deadlineNanos) {
            Thread.sleep(SETTLE_POLL_MILLIS)
        }
        assertFalse(
            worker.isRunning(),
            "the shutdown phase never stopped the worker - it is still querying after teardown",
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildServer(
        started: CountDownLatch,
        release: CountDownLatch,
    ): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {
        val environment = ShutdownPhaseTestEnvironment()
        val rootConfig = serverConfig(environment) { watchPaths = emptyList() }
        return EmbeddedServer(rootConfig, ParkingEngineFactory(started, release)) { }
            as EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>
    }

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
        url = "jdbc:h2:mem:shutdown-phase-${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver",
        username = "sa",
        password = "",
        maxPoolSize = 4,
        minIdleConnections = 1,
        connectionTimeout = 3000,
        idleTimeout = 10000,
        maxLifetime = 30000,
        autoCommit = false,
        transactionIsolation = "TRANSACTION_REPEATABLE_READ",
    )

    /**
     * Everything the probe needs to observe, handed to the test doubles that live outside the class.
     */
    internal fun recordPollSuccess() = pollsCompleted.incrementAndGet()
    internal fun recordPollFailure(message: String) { pollFailures += message }
    internal fun recordStoppingObservation(usable: Boolean) = stoppingSawUsableDatabase.set(usable)
    internal fun recordShutdownHookObservation(usable: Boolean) = shutdownHookSawUsableDatabase.set(usable)
    internal fun recordFeatureStopped() = featureStopped.set(true)
    internal fun recordFeatureReady() = featureReadyRan.set(true)
    internal fun recordReadyHookRan() = readyHookRan.set(true)
    internal fun trackWorker(worker: PollingWorker) { workers += worker }

    private companion object {
        const val CYCLES = 12
        const val BOOT_WAIT_SLICES = 100
        const val BOOT_WAIT_SLICE_MILLIS = 200L
        const val POLL_WARMUP_TIMEOUT_MILLIS = 30_000L
        const val SETTLE_TIMEOUT_MILLIS = 3_000L
        const val SETTLE_POLL_MILLIS = 10L
    }
}

/**
 * Registers the probe's worker and Ktor module once the container exists.
 *
 * A feature rather than component scanning because [bootstrapKatalystContainer] resets every
 * registry as its first act, so anything registered before boot would be wiped. `onReady` runs after
 * that reset and before the ready-hook lifecycle, which is exactly where a real feature contributes.
 */
private class ProbeFeature(private val test: ShutdownPhaseOrderingTest) : KatalystFeature {
    override val id: String = "shutdown-phase-probe"

    override fun onReady(context: KatalystBeanContext) {
        test.recordFeatureReady()
        val databaseFactory = context.get<DatabaseFactory>()
        val worker = PollingWorker(test, databaseFactory)
        test.trackWorker(worker)
        ReadyHookRegistry.register(worker)
        ShutdownHookRegistry.register(worker)
        KtorModuleRegistry.register(StoppingObserverModule(test, databaseFactory))
    }

    override fun onShutdown(context: KatalystBeanContext) {
        test.recordFeatureStopped()
    }
}

/**
 * A background worker in the shape the framework invites: started from [ReadyHook], owning its own
 * scope, polling the database on a tight loop, stopped from [ShutdownHook] with a real join.
 */
internal class PollingWorker(
    private val test: ShutdownPhaseOrderingTest,
    private val databaseFactory: DatabaseFactory,
) : ReadyHook, ShutdownHook {

    override val id: String = "polling-worker"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override suspend fun onReady() {
        test.recordReadyHookRan()
        job = scope.launch {
            while (isActive) {
                runCatching {
                    // Several statements per pass, like a real tick that refreshes gauges, sweeps
                    // stale rows and claims work. It keeps a connection checked out for most of the
                    // loop, which is what makes the shutdown race reproducible rather than lucky.
                    transaction(databaseFactory.database) {
                        repeat(QUERIES_PER_POLL) { exec("SELECT 1") { row -> row.next() } }
                    }
                }
                    .onSuccess { test.recordPollSuccess() }
                    .onFailure { error -> test.recordPollFailure("${error::class.simpleName}: ${error.message}") }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    override suspend fun onShutdown() {
        // cancelAndJoin, not cancel: a cancel alone returns while the loop is still inside a blocking
        // JDBC call, which is precisely how the original defect survived a correctly ordered stop.
        job?.cancelAndJoin()
        job = null
        scope.cancel()
        test.recordShutdownHookObservation(canQuery())
    }

    /** Whether the poll loop is still alive - the shutdown phase is supposed to have ended it. */
    fun isRunning(): Boolean = job?.isActive == true

    /** Last-resort cleanup if a test fails before the shutdown phase runs. */
    fun forceStop() {
        job?.cancel()
        job = null
        runCatching { scope.cancel() }
    }

    private fun canQuery(): Boolean =
        runCatching { transaction(databaseFactory.database) { exec("SELECT 1") { it.next() } } }.isSuccess

    private companion object {
        const val POLL_INTERVAL_MILLIS = 1L
        const val QUERIES_PER_POLL = 40
    }
}

/** The Ktor-native teardown idiom, observed from the inside. */
private class StoppingObserverModule(
    private val test: ShutdownPhaseOrderingTest,
    private val databaseFactory: DatabaseFactory,
) : KtorModule {
    override val order: Int = -1_000

    override fun install(application: Application) {
        application.monitor.subscribe(ApplicationStopping) {
            val usable = runCatching {
                transaction(databaseFactory.database) { exec("SELECT 1") { it.next() } }
            }.isSuccess
            test.recordStoppingObservation(usable && !databaseFactory.poolSnapshot().closed)
        }
    }
}

private class ShutdownPhaseTestEnvironment : ApplicationEnvironment {
    override val classLoader: ClassLoader = Thread.currentThread().contextClassLoader
    override val log = KtorSimpleLogger("ShutdownPhaseOrderingTest")
    override val config = MapApplicationConfig()

    @Deprecated("Use Application.monitor", level = DeprecationLevel.WARNING)
    override val monitor: Events = Events()
}

/** Parks in `start(wait = true)` like a real engine and releases that park when stopped. */
private class ParkingEngine(
    override val environment: ApplicationEnvironment,
    private val monitor: Events,
    private val started: CountDownLatch,
    private val release: CountDownLatch,
) : ApplicationEngine {

    override fun start(wait: Boolean): ApplicationEngine {
        monitor.raise(ServerReady, environment)
        started.countDown()
        if (wait) release.await(60, TimeUnit.SECONDS)
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        release.countDown()
    }

    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = emptyList()
}

private class ParkingEngineFactory(
    private val started: CountDownLatch,
    private val release: CountDownLatch,
) : ApplicationEngineFactory<ParkingEngine, ApplicationEngine.Configuration> {

    override fun configuration(
        configure: ApplicationEngine.Configuration.() -> Unit
    ): ApplicationEngine.Configuration = ApplicationEngine.Configuration().apply(configure)

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: ApplicationEngine.Configuration,
        applicationProvider: () -> Application,
    ): ParkingEngine = ParkingEngine(environment, monitor, started, release)
}
