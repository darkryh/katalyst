package io.github.darkryh.katalyst.telemetry.transport

import io.github.darkryh.katalyst.telemetry.model.DescriptorStatus
import io.github.darkryh.katalyst.telemetry.model.RunDescriptor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Writes the per-run discovery descriptor to `${XDG_STATE_HOME:-~/.katalyst}/run/<pid>.json` so a TUI
 * can enumerate attachable backends. The file is created 0600 where the OS supports POSIX perms, and a
 * JVM shutdown hook flips its status to STOPPED (best-effort delete on clean exit). Every operation is
 * guarded — descriptor I/O never breaks the app.
 */
class RunDescriptorWriter(
    private val appName: String,
    private val pid: Long,
    private val katalystVersion: String,
    private val host: String,
    private val telemetryPort: Int,
    private val wsToken: String,
    private val snapshotPath: String?,
    private val startedAtEpochMs: Long,
) {
    private val logger = LoggerFactory.getLogger("RunDescriptorWriter")
    private val json = Json { encodeDefaults = true; explicitNulls = false; prettyPrint = true }

    private val ownerRw: Set<PosixFilePermission> =
        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)

    private val file: Path? = runCatching { resolveDescriptorPath() }.getOrNull()

    @Volatile
    private var shutdownHookInstalled = false

    private fun resolveStateDir(): Path {
        val xdg = runCatching { System.getenv("XDG_STATE_HOME") }.getOrNull()?.takeIf { it.isNotBlank() }
        val base = if (xdg != null) {
            Path.of(xdg, "katalyst")
        } else {
            Path.of(System.getProperty("user.home") ?: ".", ".katalyst")
        }
        return base.resolve("run")
    }

    private val ownerRwx: Set<PosixFilePermission> = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE,
    )

    private fun resolveDescriptorPath(): Path {
        val dir = resolveStateDir()
        Files.createDirectories(dir)
        // The run dir holds descriptors containing plaintext ws tokens — owner-only (best-effort).
        runCatching { Files.setPosixFilePermissions(dir, ownerRwx) }
        return dir.resolve("$pid.json")
    }

    private fun descriptor(status: DescriptorStatus): RunDescriptor = RunDescriptor(
        appName = appName,
        pid = pid,
        katalystVersion = katalystVersion,
        host = host,
        telemetryPort = telemetryPort,
        wsToken = wsToken,
        snapshotPath = snapshotPath,
        startedAtEpochMs = startedAtEpochMs,
        status = status,
    )

    private fun write(status: DescriptorStatus) {
        val target = file ?: return
        runCatching {
            val bytes = json.encodeToString(RunDescriptor.serializer(), descriptor(status)).toByteArray()
            // Create the file 0600 BEFORE writing the token, so it is never briefly world-readable
            // during the creation->chmod window (POSIX). Non-POSIX filesystems fall back to plain create.
            if (Files.notExists(target)) {
                runCatching { Files.createFile(target, PosixFilePermissions.asFileAttribute(ownerRw)) }
                    .recoverCatching { Files.createFile(target) }
            }
            Files.write(target, bytes)
            runCatching { Files.setPosixFilePermissions(target, ownerRw) }
                .onFailure { logger.debug("POSIX perms unsupported for descriptor: {}", it.message) }
        }.onFailure { logger.debug("Failed to write run descriptor ({}): {}", status, it.message) }
    }

    /** Write the initial BOOTING descriptor and install the shutdown hook. */
    fun writeBooting() {
        write(DescriptorStatus.BOOTING)
        installShutdownHook()
    }

    /** Flip the descriptor to READY once the app is serving traffic. */
    fun markReady() = write(DescriptorStatus.READY)

    private fun installShutdownHook() {
        if (shutdownHookInstalled) return
        shutdownHookInstalled = true
        runCatching {
            Runtime.getRuntime().addShutdownHook(Thread {
                val target = file ?: return@Thread
                // Best-effort: delete on clean shutdown; if that fails, leave a STOPPED marker.
                val deleted = runCatching { Files.deleteIfExists(target) }.getOrDefault(false)
                if (!deleted) write(DescriptorStatus.STOPPED)
            })
        }.onFailure { logger.debug("Could not register descriptor shutdown hook: {}", it.message) }
    }
}
