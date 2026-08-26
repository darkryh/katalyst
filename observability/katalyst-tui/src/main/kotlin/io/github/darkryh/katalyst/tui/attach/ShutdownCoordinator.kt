package io.github.darkryh.katalyst.tui.attach

import io.github.darkryh.katalyst.telemetry.model.RunDescriptor
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The outcome of `/shutdown`, in the terms the footer has to report to a human. */
sealed interface ShutdownResult {
    /** The backend was asked and observed to stop. The inspector may now quit. */
    data object Stopped : ShutdownResult

    /** There is no backend attached, so there is nothing to stop. */
    data object NoBackend : ShutdownResult

    /**
     * The backend took the request but was still answering when the inspector gave up waiting.
     *
     * Deliberately NOT treated as success: a slow drain and a shutdown that silently failed look
     * identical from here, and quitting on either would leave the user believing a server is down
     * that may not be.
     */
    data class StillRunning(val descriptor: RunDescriptor) : ShutdownResult

    /** The backend declined, or cannot be asked. [outcome] says which. */
    data class Refused(
        val descriptor: RunDescriptor,
        val outcome: ShutdownRequestOutcome,
    ) : ShutdownResult
}

/**
 * Turns the inspector's `/shutdown` command into a real, verified backend shutdown.
 *
 * This exists because the command used to lie. Standalone (`./tui.sh`), `/shutdown` only closed the
 * inspector — the backend kept running — while the menu said "Stop the server and quit". Embedded,
 * the TUI *is* the application's console, so quitting it does stop the process and the command was
 * always honest there. One command, two modes, one of them wrong.
 *
 * The shape of the fix is the important part:
 *
 *  - **Ask, do not kill.** The request goes to the backend's own loopback transport, which asks the
 *    application to stop *itself* through the same path SIGINT takes. Signalling the pid from
 *    outside would have been fewer lines and worse: the application could not decline, could not
 *    report that it does not support it, and pids get reused.
 *  - **Verify, do not assume.** An HTTP 202 means "request taken", nothing more — the transport is
 *    torn down by the very shutdown it just accepted, so no response it could send would prove the
 *    application stopped. So this waits for the backend to stop answering, and reports
 *    [ShutdownResult.StillRunning] if it never does. The observation is the authority, not the
 *    status code.
 *  - **Every refusal is distinguishable.** Disabled control, an unsupported backend, a stale token
 *    and an unreachable one are four different things to tell the user; the command that started
 *    all this was wrong precisely because it collapsed a failure into silence.
 */
class ShutdownCoordinator(
    private val client: BackendControl,
    private val timeout: Duration = DEFAULT_TIMEOUT,
    private val pollInterval: Duration = DEFAULT_POLL_INTERVAL,
    /** Injected so tests do not have to spend real seconds proving a timeout. */
    private val nowNanos: () -> Long = System::nanoTime,
) {

    /**
     * Asks [descriptor] to stop and waits until it does.
     *
     * A null [descriptor] means nothing is attached — the dashboard can be showing a detached state
     * — which is [ShutdownResult.NoBackend] rather than a failure.
     */
    suspend fun shutdown(descriptor: RunDescriptor?): ShutdownResult {
        if (descriptor == null) return ShutdownResult.NoBackend

        val outcome = client.requestShutdown(descriptor)
        if (outcome != ShutdownRequestOutcome.Accepted) {
            return ShutdownResult.Refused(descriptor, outcome)
        }

        return if (awaitStopped(descriptor)) {
            ShutdownResult.Stopped
        } else {
            ShutdownResult.StillRunning(descriptor)
        }
    }

    /**
     * Polls until the backend stops answering, or the deadline passes.
     *
     * Reachability, not the descriptor file, decides. A descriptor is written by the backend and
     * removed by it at the very end of teardown — a crashed one leaves a file that says RUNNING
     * forever, and a healthy one keeps its file for the whole drain. A socket that no longer accepts
     * is the process itself being gone.
     */
    private suspend fun awaitStopped(descriptor: RunDescriptor): Boolean {
        val deadline = nowNanos() + timeout.inWholeNanoseconds
        while (nowNanos() < deadline) {
            if (!client.isReachable(descriptor)) return true
            delay(pollInterval)
        }
        // One last look: the final poll may have been the moment before it closed, and reporting a
        // stopped backend as "still running" sends the user chasing a process that is already gone.
        return !client.isReachable(descriptor)
    }

    companion object {
        /**
         * Long enough for a real drain — the entry point gives in-flight requests a second and caps
         * the engine stop at five — plus room for the container teardown behind it (pool close,
         * scheduler cancel, feature detach) on a loaded machine.
         */
        val DEFAULT_TIMEOUT: Duration = 15.seconds

        /** Fast enough that a quick shutdown feels immediate, slow enough not to spin. */
        val DEFAULT_POLL_INTERVAL: Duration = 200.milliseconds
    }
}
