package io.github.darkryh.katalyst.analysis.export

import io.github.darkryh.katalyst.analysis.model.ConfigLoaderNode
import io.github.darkryh.katalyst.analysis.model.EventHandlerNode
import io.github.darkryh.katalyst.analysis.model.InitializerNode
import io.github.darkryh.katalyst.analysis.model.KatalystApplicationGraph
import io.github.darkryh.katalyst.analysis.model.KatalystNode
import io.github.darkryh.katalyst.analysis.model.RepositoryNode
import io.github.darkryh.katalyst.analysis.model.RouteFunctionNode
import io.github.darkryh.katalyst.analysis.model.SchedulerNode
import io.github.darkryh.katalyst.analysis.model.TableNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The serialised form of a [KatalystApplicationGraph] — i.e. `katalyst-graph.json`.
 *
 * This is the *second serialisation of the same model*, not a separate source of truth: a Gradle
 * task or CLI runs [io.github.darkryh.katalyst.analysis.KatalystAnalyzer] and writes this document
 * to the build directory; the IntelliJ plugin reads it for cross-symbol features (navigation,
 * graph diagnostics) without re-running classpath analysis itself.
 *
 * The document is intentionally flat and string-based so it is trivial to consume from any tool and
 * stable across model refactors. Kind-specific data is carried in [NodeRecord.attributes].
 */
@Serializable
data class GraphDocument(
    val schemaVersion: Int = SCHEMA_VERSION,
    val scanPackages: List<String>,
    val nodes: List<NodeRecord>,
    val dependencies: List<EdgeRecord>,
    val diagnostics: List<DiagnosticRecord>,
) {
    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}

@Serializable
data class NodeRecord(
    val fqName: String,
    val simpleName: String,
    val packageName: String,
    val kind: String,
    val discoveryRule: String,
    val discoveryExplanation: String,
    val discoveryDetail: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)

@Serializable
data class EdgeRecord(
    val from: String,
    val to: String,
    val parameterName: String,
    val optional: Boolean,
    val resolvable: Boolean,
    val source: String,
)

@Serializable
data class DiagnosticRecord(
    val severity: String,
    val kind: String,
    val message: String,
    val symbolFqName: String? = null,
    val suggestion: String? = null,
)

/** Reads/writes [GraphDocument]s and converts a live [KatalystApplicationGraph] into one. */
object KatalystGraphJson {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun toDocument(graph: KatalystApplicationGraph): GraphDocument = GraphDocument(
        scanPackages = graph.scanPackages,
        nodes = graph.allNodes.map { it.toRecord() },
        dependencies = graph.dependencies.map {
            EdgeRecord(it.fromFqName, it.toFqName, it.parameterName, it.optional, it.resolvable, it.source)
        },
        diagnostics = graph.diagnostics.map {
            DiagnosticRecord(it.severity.name, it.kind.name, it.message, it.symbolFqName, it.suggestion)
        },
    )

    fun encode(graph: KatalystApplicationGraph): String = json.encodeToString(GraphDocument.serializer(), toDocument(graph))

    fun decode(text: String): GraphDocument = json.decodeFromString(GraphDocument.serializer(), text)

    /** Writes `katalyst-graph.json` (creating parent directories) and returns the file. */
    fun writeTo(file: File, graph: KatalystApplicationGraph): File {
        file.parentFile?.mkdirs()
        file.writeText(encode(graph))
        return file
    }

    private fun KatalystNode.toRecord(): NodeRecord = NodeRecord(
        fqName = symbol.fqName,
        simpleName = symbol.simpleName,
        packageName = symbol.packageName,
        kind = symbol.kind.name,
        discoveryRule = reason.rule.name,
        discoveryExplanation = reason.explanation,
        discoveryDetail = reason.detail,
        attributes = attributesOf(this),
    )

    private fun attributesOf(node: KatalystNode): Map<String, String> = when (node) {
        is RouteFunctionNode -> mapOf(
            "receiverType" to node.receiverType,
            "dslCalls" to node.dslCalls.joinToString(","),
            "installOrder" to node.installOrder.toString(),
        )
        is RepositoryNode -> buildMap { node.entityType?.let { put("entityType", it) } }
        is TableNode -> buildMap {
            node.entityType?.let { put("entityType", it) }
            put("exposedTableType", node.exposedTableType)
        }
        is EventHandlerNode -> buildMap { node.eventType?.let { put("eventType", it) } }
        is ConfigLoaderNode -> buildMap { node.configType?.let { put("configType", it) } }
        is SchedulerNode -> mapOf("owningService" to node.owningServiceFqName)
        is InitializerNode -> mapOf("runsAfterStartup" to node.runsAfterStartup.toString())
        else -> emptyMap()
    }
}
