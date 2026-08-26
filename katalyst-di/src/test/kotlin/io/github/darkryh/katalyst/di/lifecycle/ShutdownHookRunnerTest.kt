package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * The contract [ShutdownHookRunner] has to hold for the shutdown phase to be worth having.
 *
 * Two of these are the whole reason the interface exists rather than reusing Ktor's
 * `ApplicationStopping`: a hook is *awaited* (so a worker can join what it cancelled instead of only
 * signalling it), and a hook that never comes back cannot hang the process.
 *
 * The hooks below are separate classes rather than one parameterised class because
 * [ShutdownHookRegistry] keeps at most one instance per class — the same rule [ReadyHookRegistry]
 * uses, and the reason a test written with three instances of one class silently exercises one.
 */
class ShutdownHookRunnerTest {

    private lateinit var engine: TestBeanEngine
    private lateinit var trace: CopyOnWriteArrayList<String>
    private val latches = mutableListOf<CountDownLatch>()

    @BeforeTest
    fun setUp() {
        RegistryManager.resetAll()
        trace = CopyOnWriteArrayList()
        engine = TestBeanEngine()
        engine.start(emptyList(), allowOverrides = true)
    }

    @AfterTest
    fun tearDown() {
        // Release anything still parked before the engine goes, or a blocked hook thread outlives
        // the test class.
        latches.forEach { it.countDown() }
        latches.clear()
        RegistryManager.resetAll()
        engine.stop()
    }

    private fun latch(): CountDownLatch = CountDownLatch(1).also { latches += it }

    @Test
    fun `stops hooks in the reverse of the order they were started in`() = runBlocking {
        // Startup runs ascending order; shutdown has to walk the same numbers backwards so a hook is
        // stopped before the things it was started after.
        ShutdownHookRegistry.register(AlphaHook(trace, order = -10))
        ShutdownHookRegistry.register(BetaHook(trace, order = 10))
        ShutdownHookRegistry.register(GammaHook(trace, order = 0))

        ShutdownHookRunner(engine.container).invokeAll()

        assertEquals(
            listOf("beta-start", "beta-end", "gamma-start", "gamma-end", "alpha-start", "alpha-end"),
            trace.toList(),
        )
    }

    @Test
    fun `awaits a suspending hook before moving on and before returning`() = runBlocking {
        // THE reason ShutdownHook suspends. A synchronous ApplicationStopping subscriber can only
        // ask a worker to stop; this has to be able to wait until it actually has, or the pool still
        // closes under an in-flight statement.
        ShutdownHookRegistry.register(BetaHook(trace, order = 10) { delay(250) })
        ShutdownHookRegistry.register(AlphaHook(trace, order = 0))

        val report = ShutdownHookRunner(engine.container).invokeAll()

        assertEquals(
            listOf("beta-start", "beta-end", "alpha-start", "alpha-end"),
            trace.toList(),
            "a hook that suspends must be finished before the next one starts and before the " +
                "runner returns - otherwise teardown races the work it just asked to stop",
        )
        assertTrue(report.isClean)
    }

    @Test
    fun `a failing hook does not stop the others and is reported`() = runBlocking {
        ShutdownHookRegistry.register(BetaHook(trace, order = 10) { error("hook exploded") })
        ShutdownHookRegistry.register(AlphaHook(trace, order = 0))

        val report = ShutdownHookRunner(engine.container).invokeAll()

        assertContains(trace, "alpha-end", "a failure must not abandon the remaining cleanup")
        assertEquals(listOf("beta"), report.failed)
        assertTrue(report.timedOut.isEmpty())
        assertFalse(report.isClean)
    }

    @Test
    fun `a hook that never returns is abandoned instead of hanging the shutdown`() {
        // Blocking, not suspending, because that is the real case: a coroutine parked in a JDBC call
        // cannot observe cancellation, so the runner has to be able to stop *waiting* for a hook it
        // has no way to interrupt.
        val stuck = latch()
        ShutdownHookRegistry.register(BetaHook(trace, order = 10) { stuck.await(30, TimeUnit.SECONDS) })
        ShutdownHookRegistry.register(AlphaHook(trace, order = 0))

        val startedAt = System.nanoTime()
        val report = runBlocking {
            ShutdownHookRunner(engine.container, hookTimeout = 200.milliseconds).invokeAll()
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(listOf("beta"), report.timedOut)
        assertContains(trace, "alpha-end", "the hooks behind a stuck one still have to run")
        assertTrue(
            elapsedMillis < 10_000,
            "the shutdown waited $elapsedMillis ms on a hook it should have abandoned after 200 ms",
        )
    }

    @Test
    fun `two distinct instances of the same hook class both run`() = runBlocking {
        // Dedup is by identity, not by class: the registry and the container legitimately hold
        // different instances, and both own something that needs stopping.
        engine.registerInstance(AlphaHook(trace), ShutdownHook::class)
        ShutdownHookRegistry.register(AlphaHook(trace))

        val report = ShutdownHookRunner(engine.container).invokeAll()

        assertEquals(2, report.executed)
        assertEquals(listOf("alpha-start", "alpha-end", "alpha-start", "alpha-end"), trace.toList())
    }

    @Test
    fun `no hooks is a no-op`() = runBlocking {
        val report = ShutdownHookRunner(engine.container).invokeAll()

        assertEquals(ShutdownHookReport.NOTHING_TO_DO, report)
    }

    @Test
    fun `a missing container still runs the registry hooks`() = runBlocking {
        // The teardown path resolves the container defensively; losing it must not silently skip
        // every hook the registry already holds.
        ShutdownHookRegistry.register(AlphaHook(trace))

        val report = ShutdownHookRunner(container = null).invokeAll()

        assertEquals(1, report.executed)
        assertContains(trace, "alpha-end")
    }
}

private abstract class RecordingShutdownHook(
    private val trace: MutableList<String>,
    override val order: Int,
    private val body: suspend () -> Unit,
) : ShutdownHook {
    override suspend fun onShutdown() {
        trace += "$id-start"
        body()
        trace += "$id-end"
    }
}

private class AlphaHook(
    trace: MutableList<String>,
    order: Int = 0,
    body: suspend () -> Unit = {},
) : RecordingShutdownHook(trace, order, body) {
    override val id: String = "alpha"
}

private class BetaHook(
    trace: MutableList<String>,
    order: Int = 0,
    body: suspend () -> Unit = {},
) : RecordingShutdownHook(trace, order, body) {
    override val id: String = "beta"
}

private class GammaHook(
    trace: MutableList<String>,
    order: Int = 0,
    body: suspend () -> Unit = {},
) : RecordingShutdownHook(trace, order, body) {
    override val id: String = "gamma"
}
