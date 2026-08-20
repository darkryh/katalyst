package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.getOrNull
import io.github.darkryh.katalyst.di.config.ServerConfiguration
import io.github.darkryh.katalyst.ktor.telemetry.HttpTelemetry
import io.github.darkryh.katalyst.telemetry.model.HttpSnapshot
import io.github.darkryh.katalyst.telemetry.model.RouteEntry
import io.github.darkryh.katalyst.telemetry.model.RouteStats
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.path
import io.ktor.server.routing.routingRoot

/**
 * Taps the HTTP subsystem's already-computed state and exposes it as an [HttpSnapshot].
 *
 * Everything here is read-only and already-free:
 * - The bound engine id and `host:port` come from the [ServerConfiguration] bean the framework
 *   registers during DI bootstrap (`single { serverConfig }`); its `deployment` carries the resolved
 *   host/port/sslPort and its `engine` is the running [io.ktor.server.engine.EmbeddedServer].
 * - The installed route table is walked from the *running* Ktor `Application` via Ktor's own public
 *   introspection (`Application.routingRoot` uses `pluginOrNull`, so the read never installs anything)
 *   and `RoutingNode.getAllRoutes()` (handler leaves only), reconstructing `method` + `path` per route.
 *
 * Everything is resolved lazily at capture time so it works before boot completes (container/bean
 * absent -> `null`) and when the subsystem is disabled. Live request rollups (inFlight/status/latency),
 * the module install plan, and plugin flags are left defaulted for the deepen pass.
 */
class HttpCapturer : SubsystemCapturer {

    override val id: String = "http"

    override fun install(store: TelemetryStore) {
        store.httpProvider = provider@{
            val container = KatalystContainerProvider.currentOrNull() ?: return@provider null
            val serverConfig = container.getOrNull<ServerConfiguration>() ?: return@provider null

            val deployment = serverConfig.deployment
            val embedded = serverConfig.engine

            val engineId = embedded?.let { server ->
                runCatching {
                    val simpleName = server.engine.javaClass.simpleName
                    simpleName.removeSuffix("ApplicationEngine").ifBlank { simpleName }.lowercase()
                }.getOrNull()
            }

            val routes: List<RouteEntry> = embedded?.let { server ->
                runCatching {
                    server.application.routingRoot.getAllRoutes().map { leaf ->
                        RouteEntry(method = httpMethodOf(leaf), path = leaf.path)
                    }
                }.getOrDefault(emptyList())
            } ?: emptyList()

            // Live request rollups from the always-on Monitoring-phase interceptor. There is no
            // top-level latency field on the model, so global latency is surfaced as one aggregate
            // "(all requests)" RouteStats entry.
            val total = HttpTelemetry.total.get()
            val (p50, p95, max) = HttpTelemetry.latencyStats()
            val perRoute = if (total > 0) {
                listOf(RouteStats("(all requests)", total, p50, p95, max))
            } else {
                emptyList()
            }

            HttpSnapshot(
                engineId = engineId,
                host = deployment.host,
                port = deployment.port,
                sslPort = deployment.sslPort,
                routes = routes,
                inFlight = HttpTelemetry.inFlight.get(),
                totalRequests = total,
                statusClassCounts = HttpTelemetry.statusClassCounts(),
                abortedByMiddleware = HttpTelemetry.abortedByMiddleware.get(),
                exceptionsHandled = HttpTelemetry.exceptionsSeen.get(),
                perRoute = perRoute,
            )
        }
    }

    /**
     * Reconstruct the HTTP method for a handler leaf by walking its lineage for the nearest
     * [HttpMethodRouteSelector]. Routes with no method constraint report `"ANY"`.
     */
    private fun httpMethodOf(leaf: RoutingNode): String {
        var current: RoutingNode? = leaf
        while (current != null) {
            val selector = current.selector
            if (selector is HttpMethodRouteSelector) {
                return selector.method.value
            }
            current = current.parent
        }
        return "ANY"
    }
}
