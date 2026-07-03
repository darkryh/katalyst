package io.github.darkryh.katalyst.analysis.internal

import io.github.darkryh.katalyst.analysis.KatalystAnalysisConfig
import io.github.darkryh.katalyst.analysis.model.ComponentNode
import io.github.darkryh.katalyst.analysis.model.ConfigLoaderNode
import io.github.darkryh.katalyst.analysis.model.DependencyEdge
import io.github.darkryh.katalyst.analysis.model.DiagnosticSeverity
import io.github.darkryh.katalyst.analysis.model.DiscoveryReason
import io.github.darkryh.katalyst.analysis.model.DiscoveryRule
import io.github.darkryh.katalyst.analysis.model.EventHandlerNode
import io.github.darkryh.katalyst.analysis.model.InitializerNode
import io.github.darkryh.katalyst.analysis.model.KatalystApplicationGraph
import io.github.darkryh.katalyst.analysis.model.KatalystDiagnostic
import io.github.darkryh.katalyst.analysis.model.KatalystNodeKind
import io.github.darkryh.katalyst.analysis.model.KatalystSymbol
import io.github.darkryh.katalyst.analysis.model.MigrationNode
import io.github.darkryh.katalyst.analysis.model.RepositoryNode
import io.github.darkryh.katalyst.analysis.model.RouteFunctionNode
import io.github.darkryh.katalyst.analysis.model.SchedulerNode
import io.github.darkryh.katalyst.analysis.model.ServiceNode
import io.github.darkryh.katalyst.analysis.model.TableNode
import io.github.darkryh.katalyst.conventions.KatalystConventions
import io.github.darkryh.katalyst.di.analysis.DependencyAnalyzer
import io.github.darkryh.katalyst.di.analysis.DependencyGraph
import io.github.darkryh.katalyst.di.validation.DependencyValidator
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

/**
 * Builds a [KatalystApplicationGraph] from a compiled classpath by mirroring the runtime's
 * discovery rules and reusing katalyst-di's dependency analysis + validation.
 */
internal class GraphBuilder(private val config: KatalystAnalysisConfig) {

    private val logger = LoggerFactory.getLogger(GraphBuilder::class.java)
    private val index = ClasspathIndex.build(config.classpath, config.scanPackages)

    // Marker classes resolved from the analysed application's own classloader. Any that the app
    // does not depend on (e.g. scheduler not on the classpath) resolve to null and that category
    // is simply skipped — exactly how the runtime degrades when a feature module is absent.
    private val service = marker(KatalystConventions.SERVICE)
    private val component = marker(KatalystConventions.COMPONENT)
    private val crudRepository = marker(KatalystConventions.CRUD_REPOSITORY)
    private val katalystTable = marker(KatalystConventions.TABLE)
    private val exposedTable = marker(KatalystConventions.EXPOSED_TABLE)
    private val identifiable = marker(KatalystConventions.IDENTIFIABLE)
    private val eventHandler = marker(KatalystConventions.EVENT_HANDLER)
    private val ktorModule = marker(KatalystConventions.KTOR_MODULE)
    private val migration = marker(KatalystConventions.KATALYST_MIGRATION)
    private val initializer = marker(KatalystConventions.APPLICATION_INITIALIZER)
    private val readyInitializer = marker(KatalystConventions.APPLICATION_READY_INITIALIZER)
    private val configBinding = marker(KatalystConventions.CONFIG_BINDING)
    private val configPrefix = marker(KatalystConventions.CONFIG_PREFIX)
    private val schedulerHandle = marker(KatalystConventions.SCHEDULER_JOB_HANDLE)
    private val ktorApplication = marker(KatalystConventions.KTOR_APPLICATION)
    private val ktorRoute = marker(KatalystConventions.KTOR_ROUTE)

    private fun marker(fqName: String): Class<*>? = index.loadOrNull(fqName)

    private val concrete: List<Class<*>> by lazy { index.concreteClasses() }

    fun build(): KatalystApplicationGraph {

        val services = mutableListOf<ServiceNode>()
        val components = mutableListOf<ComponentNode>()
        val repositories = mutableListOf<RepositoryNode>()
        val tables = mutableListOf<TableNode>()
        val eventHandlers = mutableListOf<EventHandlerNode>()
        val initializers = mutableListOf<InitializerNode>()
        val migrations = mutableListOf<MigrationNode>()
        val configLoaders = mutableListOf<ConfigLoaderNode>()
        val schedulers = mutableListOf<SchedulerNode>()

        // Runtime discovery categories (used to feed katalyst-di's analyzer). A class may appear
        // in several, exactly as the runtime registers it (a Service is also a Component).
        val discovered = linkedMapOf<String, MutableSet<KClass<*>>>(
            KatalystConventions.Categories.SERVICES to mutableSetOf(),
            KatalystConventions.Categories.COMPONENTS to mutableSetOf(),
            KatalystConventions.Categories.REPOSITORIES to mutableSetOf(),
            KatalystConventions.Categories.EVENT_HANDLERS to mutableSetOf(),
            KatalystConventions.Categories.KTOR_MODULES to mutableSetOf(),
            KatalystConventions.Categories.MIGRATIONS to mutableSetOf(),
        )

        for (clazz in concrete) {
            val kotlin = clazz.kotlin
            if (implementsMarker(clazz, service)) discovered.getValue(KatalystConventions.Categories.SERVICES) += kotlin
            if (implementsMarker(clazz, component)) discovered.getValue(KatalystConventions.Categories.COMPONENTS) += kotlin
            if (implementsMarker(clazz, crudRepository)) discovered.getValue(KatalystConventions.Categories.REPOSITORIES) += kotlin
            if (implementsMarker(clazz, eventHandler)) discovered.getValue(KatalystConventions.Categories.EVENT_HANDLERS) += kotlin
            if (implementsMarker(clazz, ktorModule)) discovered.getValue(KatalystConventions.Categories.KTOR_MODULES) += kotlin
            if (implementsMarker(clazz, migration)) discovered.getValue(KatalystConventions.Categories.MIGRATIONS) += kotlin

            // Assign exactly one model node by most-specific precedence.
            when {
                implementsMarker(clazz, migration) ->
                    migrations += MigrationNode(symbol(clazz, KatalystNodeKind.MIGRATION), markerReason(KatalystConventions.KATALYST_MIGRATION))

                implementsMarker(clazz, configBinding) ->
                    configLoaders += ConfigLoaderNode(
                        symbol(clazz, KatalystNodeKind.CONFIG_LOADER),
                        markerReason(KatalystConventions.CONFIG_BINDING),
                        // A ConfigBinding implementor is registered by its own type.
                        configType = clazz.name,
                    )

                isAnnotatedWith(clazz, configPrefix) ->
                    configLoaders += ConfigLoaderNode(
                        symbol(clazz, KatalystNodeKind.CONFIG_LOADER),
                        DiscoveryReason(
                            DiscoveryRule.ANNOTATED_MARKER,
                            "Annotated with @${KatalystConventions.CONFIG_PREFIX.substringAfterLast('.')}",
                            KatalystConventions.CONFIG_PREFIX,
                        ),
                        // An @ConfigPrefix class is bound reflectively by its own type.
                        configType = clazz.name,
                    )

                implementsMarker(clazz, readyInitializer) ->
                    initializers += InitializerNode(symbol(clazz, KatalystNodeKind.INITIALIZER), markerReason(KatalystConventions.APPLICATION_READY_INITIALIZER), runsAfterStartup = true)

                implementsMarker(clazz, initializer) ->
                    initializers += InitializerNode(symbol(clazz, KatalystNodeKind.INITIALIZER), markerReason(KatalystConventions.APPLICATION_INITIALIZER), runsAfterStartup = false)

                implementsMarker(clazz, eventHandler) ->
                    eventHandlers += EventHandlerNode(
                        symbol(clazz, KatalystNodeKind.EVENT_HANDLER),
                        markerReason(KatalystConventions.EVENT_HANDLER),
                        eventType = GenericTypes.argumentOf(clazz, KatalystConventions.EVENT_HANDLER, 0)?.name,
                    )

                isTable(clazz) ->
                    tables += TableNode(
                        symbol(clazz, KatalystNodeKind.TABLE),
                        DiscoveryReason(DiscoveryRule.DUAL_BOUND_TABLE, "Extends Exposed Table and implements the Katalyst Table marker", KatalystConventions.TABLE),
                        entityType = GenericTypes.argumentOf(clazz, KatalystConventions.TABLE, 1)?.name,
                        exposedTableType = KatalystConventions.EXPOSED_TABLE,
                    )

                implementsMarker(clazz, crudRepository) ->
                    repositories += RepositoryNode(
                        symbol(clazz, KatalystNodeKind.REPOSITORY),
                        markerReason(KatalystConventions.CRUD_REPOSITORY),
                        entityType = GenericTypes.argumentOf(clazz, KatalystConventions.CRUD_REPOSITORY, 1)?.name,
                    )

                implementsMarker(clazz, service) -> {
                    services += ServiceNode(symbol(clazz, KatalystNodeKind.SERVICE), markerReason(KatalystConventions.SERVICE), instantiable = isInstantiable(clazz), secondaryTypes = secondaryInterfaces(clazz))
                    schedulers += schedulerMethodsOf(clazz)
                }

                implementsMarker(clazz, component) ->
                    components += ComponentNode(symbol(clazz, KatalystNodeKind.COMPONENT), markerReason(KatalystConventions.COMPONENT), instantiable = isInstantiable(clazz), secondaryTypes = secondaryInterfaces(clazz))
            }
        }

        // Function entrypoints: routes / middleware / websockets / exception handlers.
        val routes = mutableListOf<RouteFunctionNode>()
        val middleware = mutableListOf<RouteFunctionNode>()
        val websockets = mutableListOf<RouteFunctionNode>()
        val exceptionHandlers = mutableListOf<RouteFunctionNode>()
        val nonDslReceiverFunctions = mutableListOf<Method>()

        val receivers = listOfNotNull(ktorApplication, ktorRoute)
        if (receivers.isNotEmpty()) {
            for (method in index.topLevelReceiverFunctions(receivers)) {
                val calls = DslBytecodeAnalyzer.dslCalls(method, index.classLoader())
                if (calls.isEmpty()) {
                    nonDslReceiverFunctions += method
                    continue
                }
                val node = routeNode(method, calls)
                when (node.symbol.kind) {
                    KatalystNodeKind.EXCEPTION_HANDLER -> exceptionHandlers += node
                    KatalystNodeKind.MIDDLEWARE -> middleware += node
                    KatalystNodeKind.WEBSOCKET -> websockets += node
                    else -> routes += node
                }
            }
        }

        // DI graph + diagnostics by reusing the runtime analyzer/validator (no instantiation).
        val (edges, diDiagnostics, diGraph) = analyzeDependencies(discovered)

        val diagnostics = buildList {
            addAll(diDiagnostics)
            if (config.options.includeStaticChecks) {
                addAll(StaticChecks.run(repositories, tables, nonDslReceiverFunctions, diGraph))
            }
        }.let { raw ->
            if (config.options.treatWarningsAsErrors) {
                raw.map { if (it.severity == DiagnosticSeverity.WARNING) it.copy(severity = DiagnosticSeverity.ERROR) else it }
            } else raw
        }

        return KatalystApplicationGraph(
            scanPackages = config.scanPackages,
            components = components,
            services = services,
            repositories = repositories,
            tables = tables,
            routes = routes,
            middleware = middleware,
            websockets = websockets,
            exceptionHandlers = exceptionHandlers,
            schedulers = schedulers,
            eventHandlers = eventHandlers,
            initializers = initializers,
            migrations = migrations,
            configLoaders = configLoaders,
            dependencies = edges,
            diagnostics = diagnostics,
        )
    }

    private data class DiResult(
        val edges: List<DependencyEdge>,
        val diagnostics: List<KatalystDiagnostic>,
        val graph: DependencyGraph?,
    )

    private fun analyzeDependencies(discovered: Map<String, MutableSet<KClass<*>>>): DiResult {
        if (!config.options.includeDiDiagnostics) return DiResult(emptyList(), emptyList(), null)

        return runCatching {
            val configTypes = collectConfigTypes()
            val graph = DependencyAnalyzer(
                discoveredTypes = discovered.mapValues { it.value.toSet() },
                container = StaticAnalysisContainer,
                scanPackages = config.scanPackages.toTypedArray(),
                additionalAvailableTypes = configTypes,
            ).buildGraph()

            val edges = graph.nodes.values.flatMap { node ->
                node.dependencies.map { dep ->
                    DependencyEdge(
                        fromFqName = node.type.qualifiedName ?: node.type.java.name,
                        toFqName = dep.type.qualifiedName ?: dep.type.java.name,
                        parameterName = dep.parameterName,
                        optional = dep.isOptional,
                        resolvable = dep.isResolvable,
                        source = dep.source.name,
                    )
                }
            }

            val report = DependencyValidator(graph).validateAll()
            DiResult(edges, report.errors.map(DiagnosticMapper::map), graph)
        }.getOrElse { error ->
            logger.warn("Dependency analysis failed; continuing with discovery only: {}", error.message)
            DiResult(emptyList(), emptyList(), null)
        }
    }

    /** Config types contributed by discovered ConfigBinding implementors (registered by their own type). */
    private fun collectConfigTypes(): Set<KClass<*>> {
        val binding = configBinding ?: return emptySet()
        return concrete
            .filter { implementsMarker(it, binding) }
            .map { it.kotlin }
            .toSet()
    }

    private fun schedulerMethodsOf(clazz: Class<*>): List<SchedulerNode> {
        val handle = schedulerHandle ?: return emptyList()
        return clazz.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && handle.isAssignableFrom(it.returnType) }
            .map { method ->
                SchedulerNode(
                    symbol = KatalystSymbol(
                        fqName = "${clazz.name}#${method.name}",
                        simpleName = method.name,
                        packageName = clazz.packageName ?: "",
                        kind = KatalystNodeKind.SCHEDULER,
                    ),
                    reason = DiscoveryReason(DiscoveryRule.RETURNS_SCHEDULER_HANDLE, "Returns SchedulerJobHandle, scheduled at startup by the scheduler feature", KatalystConventions.SCHEDULER_JOB_HANDLE),
                    owningServiceFqName = clazz.name,
                )
            }
    }

    private fun routeNode(method: Method, calls: Set<String>): RouteFunctionNode {
        val kind = when {
            KatalystConventions.DSL_EXCEPTION_HANDLER in calls -> KatalystNodeKind.EXCEPTION_HANDLER
            KatalystConventions.DSL_MIDDLEWARE in calls -> KatalystNodeKind.MIDDLEWARE
            KatalystConventions.DSL_WEBSOCKETS in calls -> KatalystNodeKind.WEBSOCKET
            else -> KatalystNodeKind.ROUTE
        }
        val owner = method.declaringClass
        return RouteFunctionNode(
            symbol = KatalystSymbol(
                fqName = "${owner.name}#${method.name}",
                simpleName = method.name,
                packageName = owner.packageName ?: "",
                kind = kind,
            ),
            reason = DiscoveryReason(DiscoveryRule.CALLS_ROUTING_DSL, "Top-level ${method.parameterTypes[0].simpleName} function that calls ${calls.joinToString()}", calls.joinToString()),
            receiverType = method.parameterTypes[0].name,
            dslCalls = calls,
            installOrder = KatalystConventions.installOrderFor(calls, method.name),
        )
    }

    private fun isTable(clazz: Class<*>): Boolean =
        exposedTable != null && katalystTable != null &&
            exposedTable.isAssignableFrom(clazz) && katalystTable.isAssignableFrom(clazz)

    private fun implementsMarker(clazz: Class<*>, marker: Class<*>?): Boolean =
        marker != null && marker.isAssignableFrom(clazz)

    @Suppress("UNCHECKED_CAST")
    private fun isAnnotatedWith(clazz: Class<*>, annotation: Class<*>?): Boolean =
        annotation != null && annotation.isAnnotation &&
            clazz.isAnnotationPresent(annotation as Class<out Annotation>)

    private fun isInstantiable(clazz: Class<*>): Boolean =
        !clazz.isInterface && !Modifier.isAbstract(clazz.modifiers)

    /** Interfaces (excluding the Katalyst base markers) this class binds as secondary types. */
    private fun secondaryInterfaces(clazz: Class<*>): List<String> {
        val reserved = KatalystConventions.markerInterfaces
        return clazz.interfaces.map { it.name }.filter { it !in reserved }
    }

    private fun markerReason(markerFqName: String) =
        DiscoveryReason(DiscoveryRule.IMPLEMENTS_MARKER, "Implements ${markerFqName.substringAfterLast('.')}", markerFqName)

    private fun symbol(clazz: Class<*>, kind: KatalystNodeKind) =
        KatalystSymbol(
            fqName = clazz.name,
            simpleName = clazz.simpleName,
            packageName = clazz.packageName ?: "",
            kind = kind,
        )

}
