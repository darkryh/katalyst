package io.github.darkryh.katalyst.initializr.model

/**
 * The Ktor server engine the generated app runs on — exactly one is selected. Each carries the
 * concrete facts the generator needs: the `object` name used in `engine(...)`, its import, and the
 * engine starter that brings it in.
 */
enum class Engine(
    val id: String,
    val serverObject: String,
    val importFqn: String,
    val starter: String,
    val summary: String,
) {
    NETTY(
        "netty", "NettyServer",
        "io.github.darkryh.katalyst.ktor.engine.netty.NettyServer",
        "katalyst-starter-engine-netty", "NIO · default",
    ),
    JETTY(
        "jetty", "JettyServer",
        "io.github.darkryh.katalyst.ktor.engine.jetty.JettyServer",
        "katalyst-starter-engine-jetty", "servlet · HTTP/2",
    ),
    CIO(
        "cio", "CioServer",
        "io.github.darkryh.katalyst.ktor.engine.cio.CioServer",
        "katalyst-starter-engine-cio", "coroutine-native",
    ),
    ;

    companion object {
        val DEFAULT = NETTY

        fun byId(id: String): Engine? = entries.firstOrNull { it.id == id }
    }
}

/**
 * An optional Katalyst feature starter. [requires] encodes a real starter-boundary constraint:
 * migrations depend on persistence, exactly as `validateStarterBoundaries` enforces in the
 * framework build. The generator adds the [starter] to `build.gradle.kts` and (for observability)
 * conditionally emits the `run.sh` debug launcher.
 */
enum class Feature(
    val id: String,
    val display: String,
    val starter: String,
    val requires: Feature? = null,
) {
    PERSISTENCE("persistence", "Persistence", "katalyst-starter-persistence"),
    MIGRATIONS("migrations", "Migrations", "katalyst-starter-migrations", requires = PERSISTENCE),
    SCHEDULER("scheduler", "Scheduler", "katalyst-starter-scheduler"),
    WEBSOCKETS("websockets", "WebSockets", "katalyst-starter-websockets"),
    OBSERVABILITY("observability", "Observability", "katalyst-starter-observability"),
    ;

    companion object {
        fun byId(id: String): Feature? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The engine + the set of enabled optional features. [isEnabled] resolves the requires-constraint:
 * a feature is *effectively* on only if it is selected AND its prerequisite is too (migrations needs
 * persistence). All generation reads [isEnabled], never the raw [features] set, so a dangling
 * selection can never leak an unusable starter into the output.
 */
data class FeatureSelection(
    val engine: Engine = Engine.DEFAULT,
    val features: Set<Feature> = Feature.entries.toSet(),
) {
    fun isEnabled(feature: Feature): Boolean =
        feature in features && (feature.requires == null || isEnabled(feature.requires))

    /** The effectively-enabled features, requires-resolved, in declaration order. */
    val enabled: List<Feature> get() = Feature.entries.filter { isEnabled(it) }

    fun toggled(feature: Feature): FeatureSelection =
        copy(features = if (feature in features) features - feature else features + feature)

    fun withEngine(engine: Engine): FeatureSelection = copy(engine = engine)

    companion object {
        /** Everything on — the default first-load selection. */
        val DEFAULT = FeatureSelection()

        /** Named presets the UI offers, keyed by a short id. */
        val MINIMAL = FeatureSelection(features = emptySet())
        val STANDARD =
            FeatureSelection(features = setOf(Feature.PERSISTENCE, Feature.MIGRATIONS, Feature.OBSERVABILITY))
        val FULL = FeatureSelection(features = Feature.entries.toSet())
    }
}
