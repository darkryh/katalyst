package io.github.darkryh.katalyst.tui.attach

import io.github.darkryh.katalyst.telemetry.model.RunDescriptor
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Read-only enumeration of the per-run discovery descriptors a Katalyst backend writes to
 * `${XDG_STATE_HOME:-~/.katalyst}/run/<pid>.json`. Everything here is fully guarded: a missing
 * directory, an unreadable file, or a malformed / stale descriptor is silently skipped so the TUI
 * never crashes on a dead or half-written backend.
 */
object RunDiscovery {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** The directory backends drop their `<pid>.json` descriptors into. Never touches the filesystem. */
    fun runDirectory(): File {
        val xdg = System.getenv("XDG_STATE_HOME")?.takeIf { it.isNotBlank() }
        val base = if (xdg != null) File(File(xdg), "katalyst") else File(System.getProperty("user.home"), ".katalyst")
        return File(base, "run")
    }

    /** Enumerate and parse every `*.json` descriptor. Returns an empty list on any I/O failure. */
    fun discover(): List<RunDescriptor> {
        val dir = runDirectory()
        val files = runCatching {
            dir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
        }.getOrNull() ?: return emptyList()
        return files
            .sortedBy { it.name }
            .mapNotNull { parse(it) }
    }

    private fun parse(file: File): RunDescriptor? = runCatching {
        json.decodeFromString(RunDescriptor.serializer(), file.readText())
    }.getOrNull()
}
