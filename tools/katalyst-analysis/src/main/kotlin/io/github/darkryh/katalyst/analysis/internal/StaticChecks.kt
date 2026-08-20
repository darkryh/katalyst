package io.github.darkryh.katalyst.analysis.internal

import io.github.darkryh.katalyst.analysis.model.DiagnosticKind
import io.github.darkryh.katalyst.analysis.model.DiagnosticSeverity
import io.github.darkryh.katalyst.analysis.model.KatalystDiagnostic
import io.github.darkryh.katalyst.analysis.model.RepositoryNode
import io.github.darkryh.katalyst.analysis.model.TableNode
import io.github.darkryh.katalyst.di.analysis.DependencyGraph
import java.lang.reflect.Method

/**
 * Analysis-only checks the runtime cannot perform.
 *
 * These complement the reused katalyst-di validators with problems that only matter (or are only
 * detectable) statically: repository/table pairing, route functions that forgot their DSL call,
 * and ambiguous secondary-type bindings.
 */
internal object StaticChecks {

    private val routeNameHints = listOf("routes", "route", "middleware", "websocket", "websockets", "exceptionhandler", "handlers")

    fun run(
        repositories: List<RepositoryNode>,
        tables: List<TableNode>,
        nonDslReceiverFunctions: List<Method>,
        diGraph: DependencyGraph?,
    ): List<KatalystDiagnostic> = buildList {
        addAll(repositoryWithoutTable(repositories, tables))
        addAll(suspiciousRouteFunctions(nonDslReceiverFunctions))
        diGraph?.let { addAll(duplicateBindings(it)) }
    }

    private fun repositoryWithoutTable(
        repositories: List<RepositoryNode>,
        tables: List<TableNode>,
    ): List<KatalystDiagnostic> {
        val entityTypesWithTable = tables.mapNotNull { it.entityType }.toSet()
        return repositories
            .filter { it.entityType != null && it.entityType !in entityTypesWithTable }
            .map { repo ->
                KatalystDiagnostic(
                    severity = DiagnosticSeverity.WARNING,
                    kind = DiagnosticKind.REPOSITORY_WITHOUT_TABLE,
                    message = "Repository ${repo.symbol.simpleName} maps entity " +
                        "${repo.entityType?.substringAfterLast('.')} but no Katalyst Table for that entity was found",
                    symbolFqName = repo.symbol.fqName,
                    suggestion = "Define an Exposed table implementing Table<Id, ${repo.entityType?.substringAfterLast('.')}> within a scanned package.",
                )
            }
    }

    private fun suspiciousRouteFunctions(functions: List<Method>): List<KatalystDiagnostic> =
        functions
            .filter { method -> routeNameHints.any { method.name.lowercase().endsWith(it) } }
            .map { method ->
                KatalystDiagnostic(
                    severity = DiagnosticSeverity.WARNING,
                    kind = DiagnosticKind.INVALID_DSL_SIGNATURE,
                    message = "Function ${method.declaringClass.simpleName}.${method.name} looks like a Katalyst " +
                        "route/middleware function but does not call any katalyst* DSL, so it will not be discovered",
                    symbolFqName = "${method.declaringClass.name}#${method.name}",
                    suggestion = "Wrap the body in katalystRouting { } / katalystMiddleware { } / " +
                        "katalystWebSockets { } / katalystExceptionHandler { }, or rename if it is not an entrypoint.",
                )
            }

    private fun duplicateBindings(graph: DependencyGraph): List<KatalystDiagnostic> =
        graph.secondaryTypeBindings
            .filter { it.value.size > 1 }
            .map { (iface, providers) ->
                KatalystDiagnostic(
                    severity = DiagnosticSeverity.WARNING,
                    kind = DiagnosticKind.DUPLICATE_BINDING,
                    message = "Interface ${iface.simpleName} is provided by multiple components " +
                        providers.joinToString(prefix = "(", postfix = ")") { it.simpleName ?: "?" } +
                        "; injecting it by interface is ambiguous",
                    symbolFqName = iface.qualifiedName,
                    suggestion = "Inject by concrete type, use an @InjectNamed qualifier, or remove one implementation.",
                )
            }
}
