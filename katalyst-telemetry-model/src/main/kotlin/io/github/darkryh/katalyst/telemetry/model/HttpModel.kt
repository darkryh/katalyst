package io.github.darkryh.katalyst.telemetry.model

import kotlinx.serialization.Serializable

/** One installed route: the #1 on-demand thing a developer wants to confirm. */
@Serializable
data class RouteEntry(
    val method: String,
    val path: String,
)

/** One discovered Ktor module and where it landed in the ordered install plan. */
@Serializable
data class ModuleInstallEntry(
    val module: String,
    val kind: String,
    val order: Int,
    val installed: Boolean,
)

/**
 * Per-route latency/count rollup. Keyed by route TEMPLATE (`/user/{id}`), never raw path, so
 * path parameters cannot explode the store's cardinality.
 */
@Serializable
data class RouteStats(
    val template: String,
    val count: Long,
    val p50Ms: Double,
    val p95Ms: Double,
    val maxMs: Double,
)

/**
 * HTTP layer: which engine actually bound and where, the installed route table + module plan, and
 * live request pressure. Route table and engine are already-free; live rollups fill in the deepen
 * pass once the middleware interceptor is instrumented.
 */
@Serializable
data class HttpSnapshot(
    val engineId: String? = null,
    val host: String? = null,
    val port: Int = 0,
    val sslPort: Int? = null,
    val webSocketsPluginInstalled: Boolean = false,
    val statusPagesInstalled: Boolean = false,
    val exceptionHandlerCount: Int = 0,
    val routes: List<RouteEntry> = emptyList(),
    val installPlan: List<ModuleInstallEntry> = emptyList(),
    val droppedDuplicateModules: List<String> = emptyList(),
    val inFlight: Int = 0,
    val totalRequests: Long = 0,
    val statusClassCounts: Map<String, Long> = emptyMap(),
    val abortedByMiddleware: Long = 0,
    val exceptionsHandled: Long = 0,
    val perRoute: List<RouteStats> = emptyList(),
)
