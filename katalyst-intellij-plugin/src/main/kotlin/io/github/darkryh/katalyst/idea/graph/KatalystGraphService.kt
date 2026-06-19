package io.github.darkryh.katalyst.idea.graph

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Loads and caches the `katalyst-graph.json` produced by `katalyst-analysis` for this project.
 *
 * This is the metadata half of the plugin's hybrid architecture: instant editor features (implicit
 * usage, gutters, signature inspection) are pure PSI and need no build; the deeper, whole-app
 * features (cross-node navigation, dependency diagnostics) read this document when it is present and
 * simply stay dormant when it is not. It is never a substitute for the live PSI rules — only an
 * enrichment.
 *
 * Generate the file from a build with `katalyst-analysis` (e.g. a Gradle task writing
 * `build/katalyst/katalyst-graph.json`); the service picks up changes by file modification time.
 */
@Service(Service.Level.PROJECT)
class KatalystGraphService(private val project: Project) {

    private val gson = Gson()

    @Volatile
    private var cache: Cached? = null

    private data class Cached(val file: File, val lastModified: Long, val document: GraphDocument)

    /** The most recent graph document for this project, or null if none has been generated. */
    fun document(): GraphDocument? {
        val file = locateGraphFile() ?: return null
        cache?.let { if (it.file == file && it.lastModified == file.lastModified()) return it.document }
        return runCatching { gson.fromJson(file.readText(), GraphDocument::class.java) }
            .onFailure { log.warn("Failed to parse ${file.path}", it) }
            .getOrNull()
            ?.also { cache = Cached(file, file.lastModified(), it) }
    }

    /** Diagnostics attached to a given fully-qualified symbol name. */
    fun diagnosticsFor(fqName: String): List<DiagnosticRecord> =
        document()?.diagnostics?.filter { it.symbolFqName == fqName } ?: emptyList()

    /** The node record for a fully-qualified symbol name, if present in the graph. */
    fun nodeFor(fqName: String): NodeRecord? =
        document()?.nodes?.firstOrNull { it.fqName == fqName }

    private fun locateGraphFile(): File? {
        val base = project.basePath?.let(::File) ?: return null
        // Conventional location(s): <module>/build/katalyst/katalyst-graph.json. Bounded walk so we
        // never traverse the whole tree; build directories are shallow relative to module roots.
        return base.walkTopDown()
            .maxDepth(GRAPH_SEARCH_DEPTH)
            .filter { it.isFile && it.name == GRAPH_FILE_NAME }
            .maxByOrNull { it.lastModified() }
    }

    companion object {
        private const val GRAPH_FILE_NAME = "katalyst-graph.json"
        private const val GRAPH_SEARCH_DEPTH = 6
        private val log = logger<KatalystGraphService>()
    }
}
