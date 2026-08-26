package io.github.darkryh.katalyst.di.lifecycle

/**
 * Lifecycle hook that runs while the application is shutting down, BEFORE Katalyst tears anything
 * down.
 *
 * This is the counterpart to [ReadyHook]. Whatever a ready hook started — a polling loop, a queue
 * consumer, a scheduler job — is stopped here, and it is stopped while the framework is still
 * completely usable: the connection pool is open, the container still resolves, and a final
 * `transaction { }` still works.
 *
 * **Why this exists, and why it suspends.** Ktor's own `ApplicationStopping` subscribers are
 * ordinary synchronous functions, so a worker's `job.cancel()` there returns *immediately* while the
 * coroutine is still parked inside a blocking JDBC call. The pool then closes underneath it and the
 * shutdown fills with `SQLSTATE 08006 / Socket closed` and
 * `No transaction manager for db ExposedDatabase[...]`. [onShutdown] is a `suspend` function that
 * Katalyst *awaits*, so the stop can be a real one:
 *
 * ```kotlin
 * class AiRunQueueWorker(...) : Service, ReadyHook, ShutdownHook {
 *     private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
 *     private var queueJob: Job? = null
 *
 *     override suspend fun onReady() {
 *         queueJob = scope.launch { while (isActive) { tick(); delay(200) } }
 *     }
 *
 *     override suspend fun onShutdown() {
 *         queueJob?.cancelAndJoin()   // awaited: the in-flight tick is finished before we return
 *         scope.cancel()
 *     }
 * }
 * ```
 *
 * **Discovery:** implementing this interface is sufficient. A hook is scanned, dependency-validated
 * and constructor-injected on its own — it does NOT also need to be a `Component` or `Service`.
 *
 * **Order:** hooks run in DESCENDING [LifecycleHook.order], the reverse of startup, so a hook is
 * stopped before the lower-ordered hooks it was started after.
 *
 * **Isolation:** a hook that throws is logged and the remaining hooks still run — a shutdown that
 * abandons half its cleanup because one hook failed is worse than the failure. A hook that never
 * returns is abandoned after a timeout so it cannot hang the process.
 */
interface ShutdownHook : LifecycleHook {
    /**
     * Invoked while the application is shutting down.
     *
     * At this point:
     * - HTTP connectors are drained and closed ✓
     * - the container, the connection pool and transactions all still work ✓
     * - Katalyst has torn down nothing yet ✓
     *
     * Suspending work is awaited, so this is the place to *join* whatever was cancelled rather than
     * only to signal it.
     */
    suspend fun onShutdown()
}
