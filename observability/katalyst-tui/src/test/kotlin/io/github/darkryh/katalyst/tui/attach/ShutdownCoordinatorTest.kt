package io.github.darkryh.katalyst.tui.attach

import io.github.darkryh.katalyst.telemetry.model.DescriptorStatus
import io.github.darkryh.katalyst.telemetry.model.RunDescriptor
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The decisions behind `/shutdown`, without a socket.
 *
 * The bug this replaces was not a broken request — there was no request. `/shutdown` closed the
 * inspector while the menu said "Stop the server and quit", and the backend kept running. So the
 * cases worth pinning are the ones where it would be easy to be quietly wrong again: believing a
 * 202, treating a refusal as done, or quitting on a backend that never actually stopped.
 */
class ShutdownCoordinatorTest {

    private val descriptor = RunDescriptor(
        appName = "TestApp",
        pid = 4242,
        katalystVersion = "test",
        host = "127.0.0.1",
        telemetryPort = 1234,
        wsToken = "token",
        snapshotPath = null,
        startedAtEpochMs = 0,
        status = DescriptorStatus.READY,
    )

    /**
     * A backend that answers [outcome] to a shutdown request and stops answering after
     * [reachableCalls] reachability checks. `Int.MAX_VALUE` means it never stops.
     */
    private class FakeBackend(
        private val outcome: ShutdownRequestOutcome,
        private val reachableCalls: Int = 0,
    ) : BackendControl {
        val shutdownRequests = AtomicInteger()
        val reachabilityChecks = AtomicInteger()

        override suspend fun requestShutdown(descriptor: RunDescriptor): ShutdownRequestOutcome {
            shutdownRequests.incrementAndGet()
            return outcome
        }

        override suspend fun isReachable(descriptor: RunDescriptor): Boolean =
            reachabilityChecks.incrementAndGet() <= reachableCalls
    }

    private fun coordinator(
        backend: BackendControl,
        timeout: kotlin.time.Duration = 5.seconds,
    ) = ShutdownCoordinator(
        client = backend,
        timeout = timeout,
        pollInterval = 10.milliseconds,
    )

    @Test
    fun `reports stopped once the backend goes away`() = runTest {
        // Still answering for two polls, gone on the third: a real drain, not an instant death.
        val backend = FakeBackend(ShutdownRequestOutcome.Accepted, reachableCalls = 2)

        val result = coordinator(backend).shutdown(descriptor)

        assertEquals(ShutdownResult.Stopped, result)
        assertEquals(1, backend.shutdownRequests.get(), "the backend must be asked exactly once")
    }

    @Test
    fun `does not report stopped just because the request was accepted`() = runTest {
        // Accepted, and then it keeps answering forever. This is the case that must never quit the
        // inspector: a 202 is the backend agreeing to try, not evidence that it stopped.
        val backend = FakeBackend(ShutdownRequestOutcome.Accepted, reachableCalls = Int.MAX_VALUE)

        val result = coordinator(backend, timeout = 100.milliseconds).shutdown(descriptor)

        val stillRunning = assertIs<ShutdownResult.StillRunning>(result)
        assertEquals(descriptor, stillRunning.descriptor)
    }

    @Test
    fun `never waits on a backend that refused`() = runTest {
        // Waiting for a backend that said no would look exactly like a slow shutdown, and end in a
        // timeout that blames the wrong thing.
        listOf(
            ShutdownRequestOutcome.Disabled,
            ShutdownRequestOutcome.Unsupported,
            ShutdownRequestOutcome.Unauthorized,
            ShutdownRequestOutcome.Unreachable,
        ).forEach { outcome ->
            val backend = FakeBackend(outcome)

            val result = coordinator(backend).shutdown(descriptor)

            val refused = assertIs<ShutdownResult.Refused>(result)
            assertEquals(outcome, refused.outcome, "outcome must reach the caller unflattened")
            assertEquals(0, backend.reachabilityChecks.get(), "$outcome should not have been waited on")
        }
    }

    @Test
    fun `reports no backend rather than failing when nothing is attached`() = runTest {
        val backend = FakeBackend(ShutdownRequestOutcome.Accepted)

        val result = coordinator(backend).shutdown(descriptor = null)

        assertEquals(ShutdownResult.NoBackend, result)
        assertEquals(0, backend.shutdownRequests.get())
    }

    @Test
    fun `checks once more after the deadline before declaring the backend still running`() = runTest {
        // A backend that only stops on the very last look. Without the final check, a shutdown that
        // finished a millisecond late would be reported as failed and send the user after a process
        // that is already gone.
        val backend = FakeBackend(ShutdownRequestOutcome.Accepted, reachableCalls = 1)
        val elapsed = AtomicInteger()
        val coordinator = ShutdownCoordinator(
            client = backend,
            timeout = 1.seconds,
            pollInterval = 10.milliseconds,
            // Two ticks of "now": the first inside the deadline, every later one past it.
            nowNanos = { if (elapsed.getAndIncrement() < 2) 0L else 10_000_000_000L },
        )

        assertEquals(ShutdownResult.Stopped, coordinator.shutdown(descriptor))
    }

    @Test
    fun `asks the backend before it ever checks reachability`() = runTest {
        val order = mutableListOf<String>()
        val backend = object : BackendControl {
            override suspend fun requestShutdown(descriptor: RunDescriptor): ShutdownRequestOutcome {
                order += "request"
                return ShutdownRequestOutcome.Accepted
            }

            override suspend fun isReachable(descriptor: RunDescriptor): Boolean {
                order += "reachable"
                return false
            }
        }

        coordinator(backend).shutdown(descriptor)

        assertEquals(listOf("request", "reachable"), order)
    }
}
