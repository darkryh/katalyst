package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngines
import io.github.darkryh.katalyst.di.internal.ServiceRegistry
import io.github.darkryh.katalyst.di.lifecycle.ReadyHookRegistry
import io.github.darkryh.katalyst.di.lifecycle.StartupHookRegistry
import io.github.darkryh.katalyst.telemetry.model.LiveRegistries
import io.github.darkryh.katalyst.telemetry.model.WiringSnapshot
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore

/**
 * Taps the DI/wiring subsystem and reports the *live registries* it keeps outside the Koin index.
 *
 * Focus is the shape/health of the container as it exists right now. The framework's richer wiring
 * artefacts — the [io.github.darkryh.katalyst.di.analysis.DependencyGraph] (node/edge/secondary/koin
 * counts), the [io.github.darkryh.katalyst.di.error.ValidationReport] (validity + error breakdown),
 * the computed instantiation order and the per-scan discovery snapshot — are all computed during boot
 * and then discarded; no *public* live accessor surfaces them from another module, so those fields are
 * left at their model defaults (see `deferred`). What we can read publicly and read-only is:
 *
 * - container readiness via [KatalystContainerProvider.currentOrNull];
 * - the active bean-engine id via [KatalystBeanEngines.activeOrNull];
 * - the [ServiceRegistry] service count and the [StartupHookRegistry]/[ReadyHookRegistry] hook counts.
 *
 * All reads are cheap, side-effect-free static lookups that work before boot completes (registries
 * simply report empty) and when a subsystem is absent.
 */
class WiringCapturer : SubsystemCapturer {

    override val id: String = "wiring"

    override fun install(store: TelemetryStore) {
        store.wiringProvider = {
            WiringSnapshot(
                registries = readLiveRegistries(),
            )
        }
    }

    private fun readLiveRegistries(): LiveRegistries {
        val containerReady = runCatching {
            KatalystContainerProvider.currentOrNull() != null
        }.getOrDefault(false)

        val activeEngineId = runCatching {
            KatalystBeanEngines.activeOrNull()?.id
        }.getOrNull()

        val serviceCount = runCatching { ServiceRegistry.count() }.getOrDefault(0)
        val startupHooks = runCatching { StartupHookRegistry.getAll().size }.getOrDefault(0)
        val readyHooks = runCatching { ReadyHookRegistry.getAll().size }.getOrDefault(0)

        return LiveRegistries(
            containerReady = containerReady,
            activeEngineId = activeEngineId,
            // beanCount: no reliable public read-only bean total (Koin getAll is unreliable — the very
            //   reason these registries exist); left at default.
            serviceCount = serviceCount,
            // tableCount: TableRegistry is `internal`, not visible cross-module; left at default.
            // ktorModuleCount: KtorModuleRegistry only exposes consume(), which mutates; left at default.
            startupHooks = startupHooks,
            readyHooks = readyHooks,
            // features: no public registry of active feature ids; left at default.
        )
    }
}
