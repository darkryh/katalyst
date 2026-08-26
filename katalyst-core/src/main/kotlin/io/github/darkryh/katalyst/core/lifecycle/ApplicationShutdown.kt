package io.github.darkryh.katalyst.core.lifecycle

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The seam through which something inside this process can ask the application to stop itself.
 *
 * It exists because "stop the application" is knowledge only the entry point has. `katalystApplication`
 * is the one place that holds the started `EmbeddedServer`, and stopping *that* is what unwinds the
 * whole lifecycle — `start(wait = true)` returns, `ApplicationStopping` fires,
 * `stopKatalystStandalone()` runs, features detach. Anything else that wants a shutdown (today the
 * telemetry transport, on behalf of the TUI inspector's `/shutdown`) needs a way to reach that
 * without depending on the entry point, and without inventing a second, weaker teardown path of its
 * own.
 *
 * The alternative — having the inspector signal the process from outside — was rejected deliberately.
 * A signal is not a request: the application cannot decline it, cannot be asked whether it even
 * supports being stopped this way, and on Windows `Process.destroy()` is not graceful at all. Worse,
 * it relies on a pid read from a file, and pids are reused. This is a request the application itself
 * services, through its own ordinary shutdown, or honestly reports that it cannot.
 *
 * ### Contract
 *
 * - [request] is a **request**, not a stop. It decides synchronously and returns immediately; the
 *   installed action runs on a separate thread. Callers are therefore free to be HTTP handlers,
 *   which is the whole point — a handler that blocked until the process was down could never write
 *   its own response.
 * - It fires **at most once**. A second request while one is in flight is reported as
 *   [ShutdownRequest.AlreadyRequested] and does nothing, so a double-click, a retry, or two
 *   inspectors cannot start two teardowns over the same container.
 * - With nothing installed it reports [ShutdownRequest.Unsupported] rather than pretending. An
 *   application not booted through `katalystApplication` has no server to stop, and a caller that is
 *   told so can say so instead of silently doing nothing.
 * - Nothing here decides *policy*. Whether a shutdown may be requested over the network is the
 *   transport's business, checked before it ever gets here.
 *
 * Process-global by nature: there is one application per process to stop.
 */
object ApplicationShutdown {

    private val action = AtomicReference<ShutdownAction?>(null)
    private val requested = AtomicBoolean(false)

    /** Whether an application has published a way to stop itself. */
    val isSupported: Boolean
        get() = action.get() != null

    /** Whether a shutdown has already been requested and is running (or has run). */
    val isRequested: Boolean
        get() = requested.get()

    /**
     * Publishes how this application stops. Called by the entry point once it holds a started server.
     *
     * [description] appears in the log line when a shutdown is requested, so an operator reading the
     * output can tell *what* was stopped and by whom.
     */
    fun install(description: String, stop: () -> Unit) {
        action.set(ShutdownAction(description, stop))
        requested.set(false)
    }

    /**
     * Withdraws the action. After this a [request] reports [ShutdownRequest.Unsupported] again.
     *
     * Idempotent, and called from more than one place on purpose: the entry point's `finally` is the
     * normal path, but a SIGINT tears down through `ApplicationStopping` and may never unwind that
     * far, so the boot-scoped global reset clears it too.
     */
    fun uninstall() {
        action.set(null)
        requested.set(false)
    }

    /**
     * Asks the application to stop, and returns what was decided — without waiting for it to happen.
     *
     * [reason] is recorded in the log line and is meant to name the requester ("katalyst-tui"), not
     * to describe the shutdown.
     */
    fun request(reason: String): ShutdownRequest {
        val current = action.get() ?: return ShutdownRequest.Unsupported
        if (!requested.compareAndSet(false, true)) return ShutdownRequest.AlreadyRequested

        // Off the caller's thread, always. The installed action blocks until the server has drained,
        // and the caller is typically serving the very request that asked for this.
        //
        // Daemon: if the action wedges, a JVM that wants to die still can. It cannot exit *early*
        // because of it — the main thread is parked in `start(wait = true)` until this same action
        // releases it.
        val thread = Thread({ runShutdown(current, reason) }, "katalyst-shutdown")
        thread.isDaemon = true
        thread.start()
        return ShutdownRequest.Accepted
    }

    private fun runShutdown(current: ShutdownAction, reason: String) {
        // Never let a teardown failure escape into an uncaught-exception handler: this thread has no
        // one to report to, and the process is on its way out either way. Print to stderr and stop —
        // this module carries no logging dependency.
        runCatching { current.stop() }.onFailure { failure ->
            System.err.println(
                "Katalyst shutdown requested by '$reason' (${current.description}) failed: $failure",
            )
        }
    }

    /**
     * Test-only reset. Shutdown state is process-global, so a test that installs an action has to be
     * able to put the process back the way it found it.
     */
    @JvmSynthetic
    internal fun resetForTest() = uninstall()

    private class ShutdownAction(
        val description: String,
        val stop: () -> Unit,
    )
}

/** What [ApplicationShutdown.request] decided. */
enum class ShutdownRequest {
    /** The action was accepted and is running on its own thread. */
    Accepted,

    /** A shutdown was already requested; this one changed nothing. */
    AlreadyRequested,

    /** No application published a way to stop itself, so nothing was done. */
    Unsupported,
}
