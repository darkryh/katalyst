package io.github.darkryh.katalyst.telemetry.transport

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression coverage for the shutdown-hook leak: a [RunDescriptorWriter] is constructed on every
 * telemetry attach and installs a JVM shutdown hook guarded by a *per-instance* flag, so every attach
 * used to pin one more hook thread until the process exited — and a stopped application stayed
 * advertised as attachable in the run directory the whole time.
 *
 * Uses a synthetic pid so the descriptor never collides with this JVM's real one.
 */
class RunDescriptorWriterShutdownHookTest {

    private val syntheticPid = -4242L

    @AfterTest
    fun cleanup() {
        runCatching { Files.deleteIfExists(descriptorPath()) }
    }

    @Test
    fun `stopping a writer retires its descriptor and unregisters its shutdown hook`() {
        val writer = newWriter()

        writer.writeBooting()
        val hook = assertNotNull(writer.shutdownHookOrNull, "writeBooting must install a shutdown hook")
        assertTrue(Files.exists(descriptorPath()), "writeBooting must publish the descriptor")

        writer.stop()

        assertNull(writer.shutdownHookOrNull, "the writer must forget the hook it unregistered")
        // The JVM's own answer: removing an already-removed hook reports "was not registered".
        assertFalse(
            Runtime.getRuntime().removeShutdownHook(hook),
            "the hook must be unregistered with the JVM, not merely dropped by the writer",
        )
        assertFalse(
            Files.exists(descriptorPath()),
            "stopping must retire the descriptor now instead of at JVM exit",
        )
    }

    @Test
    fun `stopping twice is harmless`() {
        val writer = newWriter()
        writer.writeBooting()

        writer.stop()
        writer.stop()

        assertNull(writer.shutdownHookOrNull)
    }

    private fun newWriter() = RunDescriptorWriter(
        appName = "teardown-test",
        pid = syntheticPid,
        katalystVersion = "test",
        host = "127.0.0.1",
        telemetryPort = 1,
        wsToken = "token",
        snapshotPath = null,
        startedAtEpochMs = 0L,
    )

    /** Mirrors [RunDescriptorWriter]'s own path resolution so the test asserts on the real file. */
    private fun descriptorPath(): Path {
        val xdg = System.getenv("XDG_STATE_HOME")?.takeIf { it.isNotBlank() }
        val base = if (xdg != null) {
            Path.of(xdg, "katalyst")
        } else {
            Path.of(System.getProperty("user.home") ?: ".", ".katalyst")
        }
        return base.resolve("run").resolve("$syntheticPid.json")
    }
}
