package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.getAll
import io.github.darkryh.katalyst.di.internal.distinctByIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What [ShutdownHookRunner.invokeAll] did, for the caller to log and for tests to assert on.
 */
internal data class ShutdownHookReport(
    val executed: Int,
    val failed: List<String>,
    val timedOut: List<String>,
) {
    val isClean: Boolean get() = failed.isEmpty() && timedOut.isEmpty()

    companion object {
        val NOTHING_TO_DO = ShutdownHookReport(executed = 0, failed = emptyList(), timedOut = emptyList())
    }
}

/**
 * Executes [ShutdownHook]s while the framework is still fully alive.
 *
 * Mirrors [ReadyHookRunner] deliberately — same two discovery sources, same identity dedup — with
 * two differences that only matter on the way down:
 *
 * 1. **Reverse order.** Hooks run in descending [LifecycleHook.order], the exact reverse of startup.
 * 2. **Nothing is allowed to hang or abort the shutdown.** A failing hook is logged and the rest
 *    still run; a hook that never returns is abandoned after [hookTimeout]. Both are deliberate:
 *    at this point in the lifecycle the process is leaving, and cleanup that gives up quietly is
 *    strictly worse than cleanup that reports what it could not finish.
 *
 * Each hook runs as its own job so the timeout can actually take effect. Awaiting the hook inline
 * would not work: a hook blocked in a JDBC call cannot observe cancellation, so `withTimeoutOrNull`
 * around the call itself would still wait for it. Joining a separate job is cancellable, so a stuck
 * hook costs [hookTimeout] and not the whole shutdown.
 */
internal class ShutdownHookRunner(
    private val container: KatalystContainer?,
    private val hookTimeout: Duration = DEFAULT_HOOK_TIMEOUT,
) {
    private val logger = LoggerFactory.getLogger("ShutdownHookRunner")

    suspend fun invokeAll(): ShutdownHookReport {
        val hooks = discoverHooks()
        if (hooks.isEmpty()) {
            logger.debug("Shutdown hook lifecycle: no hooks registered")
            return ShutdownHookReport.NOTHING_TO_DO
        }

        logger.info("Shutdown hook lifecycle starting: {} hook(s)", hooks.size)
        val failed = mutableListOf<String>()
        val timedOut = mutableListOf<String>()
        // Not the caller's scope: a hook that never returns must not keep this scope from being
        // left behind, and cancelling at the end is the only pressure we can put on one.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            hooks.forEach { hook -> runHook(scope, hook, failed, timedOut) }
        } finally {
            scope.cancel()
        }

        val report = ShutdownHookReport(hooks.size, failed, timedOut)
        if (report.isClean) {
            logger.info("Shutdown hook lifecycle completed: {} hook(s)", hooks.size)
        } else {
            logger.warn(
                "Shutdown hook lifecycle completed with problems: {} hook(s), failed={}, timed out={}",
                hooks.size,
                failed,
                timedOut,
            )
        }
        return report
    }

    private suspend fun runHook(
        scope: CoroutineScope,
        hook: ShutdownHook,
        failed: MutableList<String>,
        timedOut: MutableList<String>,
    ) {
        val startTime = System.currentTimeMillis()
        logger.debug("Shutdown hook starting: {}", hook.id)

        val failure = AtomicReference<Throwable?>(null)
        val job = scope.launch {
            runCatching { hook.onShutdown() }.onFailure(failure::set)
        }

        val completed = withTimeoutOrNull(hookTimeout) { job.join() } != null
        val duration = System.currentTimeMillis() - startTime

        when {
            !completed -> {
                timedOut += hook.id
                job.cancel()
                logger.warn(
                    "Shutdown hook did not finish within {} and was abandoned: {} - the shutdown " +
                        "continues, but whatever it owns was not stopped cleanly",
                    hookTimeout,
                    hook.id,
                )
            }

            failure.get() != null -> {
                failed += hook.id
                // Logged here and not left to the caller: Ktor swallows exceptions thrown out of
                // its shutdown events at debug level, so an unlogged failure here is an invisible one.
                logger.error(
                    "Shutdown hook failed: {} ({} ms) - {}",
                    hook.id,
                    duration,
                    failure.get()?.message,
                    failure.get(),
                )
            }

            else -> logger.debug("Shutdown hook completed: {} ({} ms)", hook.id, duration)
        }
    }

    private fun discoverHooks(): List<ShutdownHook> {
        val registryHooks = ShutdownHookRegistry.getAll()
        // Same reasoning as ReadyHookRunner: a container-side failure must not silently reduce the
        // hook set. Losing a shutdown hook means background work keeps running into the teardown.
        val containerHooks = container?.let { active ->
            runCatching { active.getAll<ShutdownHook>() }
                .onFailure { error ->
                    logger.warn(
                        "Could not enumerate {} beans from the container; only the {} hook(s) already " +
                            "held by ShutdownHookRegistry will run. Reason: {}: {}",
                        ShutdownHook::class.simpleName,
                        registryHooks.size,
                        error::class.simpleName,
                        error.message,
                        error,
                    )
                }
                .getOrElse { emptyList() }
        }.orEmpty()

        return (registryHooks + containerHooks)
            .distinctByIdentity()
            .sortedWith(shutdownHookOrderComparator)
    }

    private companion object {
        /**
         * How long one hook may take before the shutdown stops waiting for it.
         *
         * Per hook rather than a shared budget, so a slow drain does not eat the allowance of the
         * hooks queued behind it and the log names exactly which one overran.
         */
        val DEFAULT_HOOK_TIMEOUT: Duration = 10.seconds
    }
}

/**
 * The exact reverse of [readyHookOrderComparator], tie-break included, so stop order mirrors start
 * order even between hooks that share a number.
 */
internal val shutdownHookOrderComparator: Comparator<ShutdownHook> =
    compareByDescending<ShutdownHook> { it.order }
        .thenByDescending { it::class.qualifiedName ?: it::class.simpleName ?: "" }
