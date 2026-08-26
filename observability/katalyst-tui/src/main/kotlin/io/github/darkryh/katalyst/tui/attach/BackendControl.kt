package io.github.darkryh.katalyst.tui.attach

import io.github.darkryh.katalyst.telemetry.model.RunDescriptor

/**
 * The two things [ShutdownCoordinator] needs from a backend: ask it to stop, and see whether it is
 * still there.
 *
 * Split out of [TelemetryClient] so the shutdown *logic* — which refusal means what, how long to
 * wait, when to believe a backend has gone — can be tested without a socket. That logic is the part
 * with decisions in it; the HTTP is not.
 */
interface BackendControl {

    /**
     * Ask [descriptor] to stop itself. The result is the backend's answer to the request, never
     * proof that it stopped.
     */
    suspend fun requestShutdown(descriptor: RunDescriptor): ShutdownRequestOutcome

    /**
     * Whether [descriptor] still answers on its transport, within the caller's timeout.
     *
     * Any HTTP answer counts, a rejected token included — the question is whether something is
     * there, not whether it likes us. A backend that accepts a connection and then never replies is
     * indistinguishable from a stopped one to anything outside it, and is reported as gone.
     */
    suspend fun isReachable(descriptor: RunDescriptor): Boolean
}
