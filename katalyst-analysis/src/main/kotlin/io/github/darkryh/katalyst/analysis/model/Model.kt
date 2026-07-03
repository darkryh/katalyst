package io.github.darkryh.katalyst.analysis.model

/**
 * The kind of Katalyst entrypoint a node represents.
 *
 * Mirrors the runtime discovery categories plus the function-based entrypoints
 * (routes / middleware / websockets / exception handlers) and the method-based
 * scheduler entrypoints.
 */
enum class KatalystNodeKind {
    SERVICE,
    COMPONENT,
    REPOSITORY,
    TABLE,
    ROUTE,
    MIDDLEWARE,
    WEBSOCKET,
    EXCEPTION_HANDLER,
    EVENT_HANDLER,
    SCHEDULER,
    INITIALIZER,
    MIGRATION,
    CONFIG_LOADER,
}

/**
 * The machine-readable rule that caused a symbol to be considered "used by Katalyst".
 *
 * This is what lets tooling answer the question *"why is this symbol an entrypoint?"* — the
 * IDE renders [explanation] in a tooltip, reports group by [rule].
 */
enum class DiscoveryRule {
    /** The class implements a Katalyst marker interface (e.g. Service, EventHandler). */
    IMPLEMENTS_MARKER,

    /** A top-level function with a Ktor receiver whose body calls a Katalyst routing DSL function. */
    CALLS_ROUTING_DSL,

    /** A member function (on a discovered Service) that returns SchedulerJobHandle. */
    RETURNS_SCHEDULER_HANDLE,

    /** A class that extends Exposed's Table and also implements the Katalyst Table marker. */
    DUAL_BOUND_TABLE,

    /** A class carrying a Katalyst marker annotation (e.g. @ConfigPrefix). */
    ANNOTATED_MARKER,
}

/**
 * Why a symbol was discovered, in both machine ([rule]) and human ([explanation]) form.
 *
 * @property detail optional extra detail, e.g. the specific DSL function called or the
 * marker interface implemented.
 */
data class DiscoveryReason(
    val rule: DiscoveryRule,
    val explanation: String,
    val detail: String? = null,
)

/** Where a symbol lives in source, when known (populated only when source roots are provided). */
data class SourceLocation(
    val filePath: String,
    val line: Int? = null,
)

/**
 * A reference to a discovered symbol.
 *
 * @property fqName fully-qualified name; for a function entrypoint this is `Owner#functionName`.
 * @property kind which [KatalystNodeKind] this symbol is.
 */
data class KatalystSymbol(
    val fqName: String,
    val simpleName: String,
    val packageName: String,
    val kind: KatalystNodeKind,
    val location: SourceLocation? = null,
)

/** Common contract for every node in the application graph. */
sealed interface KatalystNode {
    val symbol: KatalystSymbol
    val reason: DiscoveryReason
}

/** A constructor/property dependency of a managed component, as resolved statically. */
data class DependencyEdge(
    val fromFqName: String,
    val toFqName: String,
    val parameterName: String,
    val optional: Boolean,
    val resolvable: Boolean,
    /** CONSTRUCTOR, WELL_KNOWN_PROPERTY or SECONDARY_TYPE — mirrors the runtime's DependencySource. */
    val source: String,
)

data class ComponentNode(
    override val symbol: KatalystSymbol,
    override val reason: DiscoveryReason,
    val instantiable: Boolean,
    val secondaryTypes: List<String> = emptyList(),
) : KatalystNode

data class ServiceNode(
    override val symbol: KatalystSymbol,
    override val reason: DiscoveryReason,
    val instantiable: Boolean,
    val secondaryTypes: List<String> = emptyList(),
) : KatalystNode

data class RepositoryNode(
    override val symbol: KatalystSymbol,
    override val reason: DiscoveryReason,
    /** The entity type argument of CrudRepository<Id, Entity>, when statically resolvable. */
    val entityType: String? = null,
    /** The Exposed table referenced by `override val table`, when statically resolvable. */
    val tableType: String? = null,
) : KatalystNode

data class TableNode(
    override val symbol: KatalystSymbol,
    override val reason: DiscoveryReason,
    val entityType: String? = null,
    /** The Exposed table class this dual-binds to. */
    val exposedTableType: String,
) : KatalystNode

/**
 * A function entrypoint: route, middleware, websocket or exception handler.
 *
 * @property receiverType the Ktor receiver (`Route` or `Application`).
 * @property dslCalls the Katalyst DSL functions the body invokes.
 * @property installOrder the runtime install-order hint (exception handlers first, etc).
 */
data class RouteFunctionNode(
    override val symbol: KatalystSymbol,
    override val reason: DiscoveryReason,
    val receiverType: String,
    val dslCalls: Set<String>,
    val installOrder: Int,
) : KatalystNode

data class SchedulerNode(
    override val symbol: KatalystSymbol,
    override val reason: DiscoveryReason,
    /** The service class that declares this scheduling method. */
    val owningServiceFqName: String,
) : KatalystNode

data class EventHandlerNode(
    override val symbol: KatalystSymbol,
    override val reason: DiscoveryReason,
    /** The event type argument of EventHandler<E>, when statically resolvable. */
    val eventType: String? = null,
) : KatalystNode

data class InitializerNode(
    override val symbol: KatalystSymbol,
    override val reason: DiscoveryReason,
    /** True for ReadyHook, false for StartupHook. */
    val runsAfterStartup: Boolean,
) : KatalystNode

data class MigrationNode(
    override val symbol: KatalystSymbol,
    override val reason: DiscoveryReason,
) : KatalystNode

data class ConfigLoaderNode(
    override val symbol: KatalystSymbol,
    override val reason: DiscoveryReason,
    /** The config type produced by this loader, when statically resolvable. */
    val configType: String? = null,
) : KatalystNode

/**
 * The complete semantic graph of a Katalyst application.
 *
 * This is the primary product of [io.github.darkryh.katalyst.analysis.KatalystAnalyzer].
 * It can be consumed in memory, serialised to `katalyst-graph.json`, or rendered as a
 * report — three views of one model, never three separate truths.
 */
data class KatalystApplicationGraph(
    val scanPackages: List<String>,
    val components: List<ComponentNode> = emptyList(),
    val services: List<ServiceNode> = emptyList(),
    val repositories: List<RepositoryNode> = emptyList(),
    val tables: List<TableNode> = emptyList(),
    val routes: List<RouteFunctionNode> = emptyList(),
    val middleware: List<RouteFunctionNode> = emptyList(),
    val websockets: List<RouteFunctionNode> = emptyList(),
    val exceptionHandlers: List<RouteFunctionNode> = emptyList(),
    val schedulers: List<SchedulerNode> = emptyList(),
    val eventHandlers: List<EventHandlerNode> = emptyList(),
    val initializers: List<InitializerNode> = emptyList(),
    val migrations: List<MigrationNode> = emptyList(),
    val configLoaders: List<ConfigLoaderNode> = emptyList(),
    val dependencies: List<DependencyEdge> = emptyList(),
    val diagnostics: List<KatalystDiagnostic> = emptyList(),
) {
    /** Every node across all categories. */
    val allNodes: List<KatalystNode>
        get() = components + services + repositories + tables + routes + middleware +
            websockets + exceptionHandlers + schedulers + eventHandlers + initializers +
            migrations + configLoaders

    /** Fully-qualified names of every symbol Katalyst considers used. */
    val usedSymbolFqNames: Set<String>
        get() = allNodes.mapTo(mutableSetOf()) { it.symbol.fqName }

    /** True if analysis surfaced any [DiagnosticSeverity.ERROR]. */
    val hasErrors: Boolean
        get() = diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
}
