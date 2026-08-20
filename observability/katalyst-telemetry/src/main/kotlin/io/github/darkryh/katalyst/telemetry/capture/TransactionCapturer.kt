package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.getAll
import io.github.darkryh.katalyst.core.di.getOrNull
import io.github.darkryh.katalyst.telemetry.model.AdapterEntry
import io.github.darkryh.katalyst.telemetry.model.TransactionSnapshot
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import io.github.darkryh.katalyst.transactions.adapter.TransactionAdapter
import io.github.darkryh.katalyst.transactions.config.TransactionConfig
import io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager
import io.github.darkryh.katalyst.transactions.telemetry.TransactionTelemetry

/**
 * Taps the TRANSACTIONS subsystem and reports its already-computed, read-only configuration state.
 *
 * What is *already-free* and captured here (public API only):
 * - **Registered adapters** (name / priority / critical): read from any [TransactionAdapter] that is
 *   registered as a DI bean via `KatalystContainer.getAll`. Each accessor
 *   ([TransactionAdapter.name]/[TransactionAdapter.priority]/[TransactionAdapter.isCritical]) is public.
 *   Note: the framework's own core adapters (Persistence/Events) are added directly to the manager's
 *   *private* `TransactionAdapterRegistry` via `addAdapter(...)` and are not beans, so they will not
 *   appear here unless an app also registers adapters as components; this list may legitimately be empty.
 * - **Configured isolation / timeout / maxRetries**: taken from the framework-default [TransactionConfig].
 *   The live per-manager `defaultTransactionConfig` is a private field with no public accessor; the DI
 *   bootstrap constructs it as `TransactionConfig(phaseLoggingEnabled = …)`, leaving isolation, timeout
 *   and retry policy at their public [TransactionConfig] defaults, so those defaults are the effective
 *   configured values.
 *
 * Deepen-pass (left defaulted): outcome tallies (committed/rolledBack/timedOut/failed), latency
 * percentiles and in-flight transactions all require the dormant `MetricsCollector` and the discarded
 * `PhaseExecutionResults`; [TransactionSnapshot.metricsCollectorAttached] is therefore reported `false`.
 *
 * The provider is null-safe and side-effect-free: if the container or the [DatabaseTransactionManager]
 * bean is absent (subsystem disabled / not yet booted) it returns `null` and reports no section.
 */
class TransactionCapturer : SubsystemCapturer {

    override val id: String = "transactions"

    override fun install(store: TelemetryStore) {
        store.transactionProvider = provider@{
            val container = KatalystContainerProvider.currentOrNull() ?: return@provider null

            // Gate on the subsystem actually being wired; absent manager => nothing to report.
            val manager = runCatching { container.getOrNull<DatabaseTransactionManager>() }.getOrNull()
                ?: return@provider null

            val adapters = runCatching { container.getAll<TransactionAdapter>() }
                .getOrDefault(emptyList())
                .asSequence()
                .take(MAX_ADAPTERS)
                .map { adapter ->
                    AdapterEntry(
                        name = runCatching { adapter.name() }
                            .getOrNull()
                            ?: (adapter::class.simpleName ?: "adapter"),
                        priority = runCatching { adapter.priority() }.getOrDefault(0),
                        critical = runCatching { adapter.isCritical() }.getOrDefault(false),
                    )
                }
                .toList()

            // Framework-default transaction settings (the live manager's config is not public).
            val defaults = TransactionConfig()

            val (p50, p95, p99) = TransactionTelemetry.percentiles()
            val rolledBack = TransactionTelemetry.rolledBack
            val timedOut = TransactionTelemetry.timedOut

            TransactionSnapshot(
                adapters = adapters,
                metricsCollectorAttached = true,
                isolationConfigured = defaults.isolationLevel.name,
                timeoutMs = defaults.timeout.inWholeMilliseconds,
                maxRetries = defaults.retryPolicy.maxRetries,
                committed = TransactionTelemetry.committed,
                rolledBack = rolledBack,
                timedOut = timedOut,
                failed = rolledBack + timedOut,
                p50Ms = p50,
                p95Ms = p95,
                p99Ms = p99,
            )
        }
    }

    private companion object {
        /** Upper bound on adapter rows reported, so a pathological bean set can never blow the section. */
        private const val MAX_ADAPTERS = 64
    }
}
