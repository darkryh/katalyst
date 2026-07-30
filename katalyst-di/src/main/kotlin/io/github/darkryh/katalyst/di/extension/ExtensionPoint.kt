package io.github.darkryh.katalyst.di.extension

import io.github.darkryh.katalyst.conventions.KatalystConventions
import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.di.lifecycle.ReadyHook
import io.github.darkryh.katalyst.di.lifecycle.StartupHook
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.ktor.KtorModule
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.repositories.CrudRepository
import kotlin.reflect.KClass

/**
 * How many instances of an extension point may exist, and therefore how it is looked up.
 */
internal enum class Cardinality {
    /**
     * At most one implementation may claim the type as a secondary binding. A second
     * claimant is a configuration error and fails fast with a collision diagnostic.
     */
    SINGLE,

    /**
     * Many implementations are expected and all of them must be reachable together.
     * Multibinding types are exempt from collision detection and are resolved with
     * `getAll`, never `get`.
     */
    MULTI,
}

/**
 * When an extension point is registered relative to the topologically ordered components.
 */
internal enum class RegistrationPhase {
    /** Registered as part of the ordinary dependency-ordered component pass. */
    WITH_COMPONENTS,

    /** Registered after every ordinary component, because it has a different lifecycle. */
    AFTER_COMPONENTS,
}

/**
 * The declarative description of one Katalyst extension point.
 *
 * Katalyst is annotation-free: a class is framework-managed because it implements one of a
 * small set of marker types. Every phase of bootstrap needs to know slightly different facts
 * about those markers — how to scan for them, whether an empty result is normal, whether they
 * join the dependency graph, how they are looked up afterwards.
 *
 * Historically each of those facts lived in its own `when` block or hardcoded set, spread
 * across the registrar, the orchestrator, the dependency graph, the validator and the error
 * renderer. Nothing forced those sites to agree, and that is exactly how
 * [KatalystMigration] came to be excluded from secondary bindings without being given any
 * replacement lookup mechanism: discovered on every boot, registered under its own concrete
 * class, and then invisible to `getAll<KatalystMigration>()` — so no migration ever ran.
 *
 * This descriptor is the single source of truth those sites now read. Adding an extension
 * point is one entry in [ExtensionPoints.all]; it is no longer possible to wire a new marker
 * into discovery while forgetting to wire it into lookup.
 *
 * @property id Stable category name used by discovery snapshots and validation messages.
 * @property type The marker interface implementations are discovered by.
 * @property cardinality Whether many implementations may coexist. See [Cardinality].
 * @property optionalDiscovery Whether finding zero implementations is normal (logs INFO
 *   instead of WARN). True for opt-in subsystems such as migrations and lifecycle hooks.
 * @property joinsDependencyGraph Whether instances participate in dependency validation and
 *   topological ordering. Migrations do not: they have a lifecycle of their own.
 * @property registrationPhase When instances are registered. See [RegistrationPhase].
 * @property featureId Id of the [io.github.darkryh.katalyst.di.feature.KatalystFeature] that
 *   executes these implementations, or null when the runtime handles them unconditionally.
 *   Used to warn when implementations exist but their feature is switched off.
 * @property enableHint The DSL call that turns this subsystem on, surfaced alongside that
 *   warning.
 */
internal data class ExtensionPoint(
    val id: String,
    val type: KClass<*>,
    val cardinality: Cardinality,
    val optionalDiscovery: Boolean,
    val joinsDependencyGraph: Boolean,
    val registrationPhase: RegistrationPhase,
    val featureId: String? = null,
    val enableHint: String? = null,
) {
    val isMultiBinding: Boolean get() = cardinality == Cardinality.MULTI
}

/**
 * The catalog of every extension point the runtime recognises.
 *
 * Kept in `katalyst-di` rather than in each optional module so the scanner can always find
 * marker types even when the optional runtime feature is absent from the classpath — the same
 * reason [KatalystMigration] itself lives here.
 *
 * This is the runtime twin of [KatalystConventions], which encodes the same rules for static
 * analysis and the IntelliJ plugin. `ExtensionPointCatalogParityTest` asserts the two agree,
 * so the runtime, the analysis layer and the IDE cannot drift apart.
 */
internal object ExtensionPoints {

    val all: List<ExtensionPoint> = listOf(
        ExtensionPoint(
            id = "repositories",
            type = CrudRepository::class,
            cardinality = Cardinality.SINGLE,
            optionalDiscovery = false,
            joinsDependencyGraph = true,
            registrationPhase = RegistrationPhase.WITH_COMPONENTS,
        ),
        ExtensionPoint(
            id = "components",
            type = Component::class,
            cardinality = Cardinality.SINGLE,
            optionalDiscovery = false,
            joinsDependencyGraph = true,
            registrationPhase = RegistrationPhase.WITH_COMPONENTS,
        ),
        ExtensionPoint(
            id = "services",
            type = Service::class,
            cardinality = Cardinality.SINGLE,
            optionalDiscovery = false,
            joinsDependencyGraph = true,
            registrationPhase = RegistrationPhase.WITH_COMPONENTS,
        ),
        // Many handlers legitimately coexist — that is the whole point of an event bus — so
        // this is MULTI, and every handler is reachable through getAll. Call sites that union
        // the container with GlobalEventHandlerRegistry must deduplicate by identity, since a
        // scanned handler appears in both and EventTopology subscribes whatever it is handed.
        ExtensionPoint(
            id = "event handlers",
            type = EventHandler::class,
            cardinality = Cardinality.MULTI,
            optionalDiscovery = false,
            joinsDependencyGraph = true,
            registrationPhase = RegistrationPhase.WITH_COMPONENTS,
            featureId = "events",
            enableHint = "features { enableEvents() }",
        ),
        ExtensionPoint(
            id = "ktor modules",
            type = KtorModule::class,
            cardinality = Cardinality.SINGLE,
            optionalDiscovery = true,
            joinsDependencyGraph = true,
            registrationPhase = RegistrationPhase.WITH_COMPONENTS,
        ),
        ExtensionPoint(
            id = "migrations",
            type = KatalystMigration::class,
            cardinality = Cardinality.MULTI,
            optionalDiscovery = true,
            joinsDependencyGraph = false,
            registrationPhase = RegistrationPhase.AFTER_COMPONENTS,
            featureId = "migrations",
            enableHint = "features { enableMigrations() }",
        ),
        ExtensionPoint(
            id = "startup hooks",
            type = StartupHook::class,
            cardinality = Cardinality.MULTI,
            optionalDiscovery = true,
            joinsDependencyGraph = true,
            registrationPhase = RegistrationPhase.WITH_COMPONENTS,
        ),
        ExtensionPoint(
            id = "ready hooks",
            type = ReadyHook::class,
            cardinality = Cardinality.MULTI,
            optionalDiscovery = true,
            joinsDependencyGraph = true,
            registrationPhase = RegistrationPhase.WITH_COMPONENTS,
        ),
    )

    private val byType: Map<KClass<*>, ExtensionPoint> = all.associateBy { it.type }
    private val byId: Map<String, ExtensionPoint> = all.associateBy { it.id }

    /** Marker types that must never be claimed as an ordinary secondary binding. */
    val reservedTypes: Set<KClass<*>> = all.map { it.type }.toSet()

    /** Marker types that many implementations may share, resolved via `getAll`. */
    val multiBindingTypes: Set<KClass<*>> =
        all.filter { it.isMultiBinding }.map { it.type }.toSet()

    fun forType(type: KClass<*>): ExtensionPoint? = byType[type]

    fun forId(id: String): ExtensionPoint? = byId[id]

    fun isMultiBinding(type: KClass<*>): Boolean = type in multiBindingTypes

    /**
     * The multibinding marker types [instance] actually implements.
     *
     * Deliberately a runtime `isInstance` check rather than a walk over declared supertypes.
     * `KClass.supertypes` yields only *direct* supertypes, so a migration written the ordinary
     * way — `class AddUsers : SqlMigration()` — reports `SqlMigration` (an abstract class) and
     * never `KatalystMigration`. `isInstance` is depth-independent, survives abstract
     * intermediates, and is correct under generic erasure.
     */
    fun multiBindingTypesOf(instance: Any): List<KClass<*>> =
        all.filter { it.isMultiBinding && it.type.isInstance(instance) }.map { it.type }

    /** Whether a component of this type participates in dependency validation and ordering. */
    fun joinsDependencyGraph(type: KClass<*>): Boolean {
        val excluded = all.filter { !it.joinsDependencyGraph }
        return excluded.none { it.type.java.isAssignableFrom(type.java) }
    }
}
