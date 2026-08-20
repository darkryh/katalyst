package io.github.darkryh.katalyst.initializr.model

/**
 * A plain-language capability shown on the simple ("What should it do?") screen. Each maps to one or
 * more Katalyst feature starters. The simple capability cards and the advanced fine-grained feature
 * switches read and write the same [FeatureSelection], so the two surfaces can never drift out of
 * sync: turning a capability on adds its [bundled] features; turning it off removes them.
 *
 * "Database" bundles persistence + migrations because that is the outcome a user thinks in — storing
 * and querying data — while migrations-needs-persistence stays an internal starter-boundary detail
 * that only advanced users see (as the child switch).
 */
enum class Capability(
    val id: String,
    val title: String,
    val summary: String,
    val primary: Feature,
    val bundled: Set<Feature>,
) {
    DATABASE("database", "Database", "Store and query data.", Feature.PERSISTENCE, setOf(Feature.PERSISTENCE, Feature.MIGRATIONS)),
    SCHEDULED_JOBS("scheduler", "Scheduled jobs", "Run work on a timer.", Feature.SCHEDULER, setOf(Feature.SCHEDULER)),
    REALTIME("realtime", "Real-time", "Push live updates to clients.", Feature.WEBSOCKETS, setOf(Feature.WEBSOCKETS)),
    MONITORING("monitoring", "Monitoring", "Metrics and a live inspector.", Feature.OBSERVABILITY, setOf(Feature.OBSERVABILITY)),
}

/** A capability shows "on" when its primary feature is selected (its bundled extras follow). */
fun FeatureSelection.isCapabilityOn(capability: Capability): Boolean = capability.primary in features

/** Toggle a capability: enabling adds its bundled features, disabling removes all of them. */
fun FeatureSelection.toggleCapability(capability: Capability): FeatureSelection =
    if (capability.primary in features) {
        copy(features = features - capability.bundled)
    } else {
        copy(features = features + capability.bundled)
    }
