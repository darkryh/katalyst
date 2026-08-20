package io.github.darkryh.katalyst.tui.navigation

import io.github.darkryh.dispatch.navigation.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable navigation routes for the inspector. `Home` is the tile grid; every other route is
 * one subsystem tile. Each is a `@Serializable` `data object` so the Dispatch back stack can save,
 * restore, and content-key it. Detail/drill-down routes can be added later without touching the seam.
 */

@Serializable @SerialName("home")
data object HomeRoute : NavKey

@Serializable @SerialName("boot")
data object BootRoute : NavKey

@Serializable @SerialName("wiring")
data object WiringRoute : NavKey

@Serializable @SerialName("http")
data object HttpRoute : NavKey

@Serializable @SerialName("scheduler")
data object SchedulerRoute : NavKey

@Serializable @SerialName("persistence")
data object PersistenceRoute : NavKey

@Serializable @SerialName("transactions")
data object TransactionsRoute : NavKey

@Serializable @SerialName("migrations")
data object MigrationsRoute : NavKey

@Serializable @SerialName("events")
data object EventsRoute : NavKey

@Serializable @SerialName("websockets")
data object WebSocketsRoute : NavKey

@Serializable @SerialName("config")
data object ConfigRoute : NavKey

/** Drill-down: one scheduled job's aggregate stats + its recent run history. */
@Serializable @SerialName("scheduler/job")
data class SchedulerJobRoute(val jobName: String) : NavKey
