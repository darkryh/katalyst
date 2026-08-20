package io.github.darkryh.katalyst.telemetry

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Teardown coverage for the three resources an attach takes: the loopback transport's bound port, the
 * run descriptor (plus its JVM shutdown hook) and the process-global [TelemetryStore].
 *
 * The application never had a teardown edge into telemetry — `KatalystFeature` declares only
 * `provideBeanModules()`/`onReady()` — so an attach used to keep all three until the JVM exited, and
 * a second attach in the same JVM stranded the first server on its port. The lever these tests pin
 * down is the container's own lifecycle: the bean engine closes every [AutoCloseable] bean it
 * registered when the container stops, so telemetry contributes one and tears down there. The tests
 * drive that closeable exactly as the engine does — materialized once at registration, closed
 * newest-first at stop.
 */
class TelemetryTeardownTest {

    private val loopback: InetAddress = InetAddress.getByName(TelemetryConfig.DEFAULT_HOST)

    /** Teardowns handed out by [attach] that a test has not closed yet. */
    private val open = mutableListOf<AutoCloseable>()

    @AfterTest
    fun cleanup() {
        System.clearProperty("katalyst.telemetry.enabled")
        // Never leak an attach into a sibling test. The transport-only seam is the fallback for the
        // case where the attach contributed no closeable at all.
        runCatching { close(open.toList()) }
        TelemetryFeature.shutdownHookActionForTest()
        runCatching { Files.deleteIfExists(descriptorPath()) }
    }

    @Test
    fun `attaching twice releases the first transport's port`() {
        System.setProperty("katalyst.telemetry.enabled", "true")

        attach()
        val firstPort = boundPort()

        val second = attach()
        val secondPort = boundPort()

        // The first server used to be overwritten in place (`server = transport`) with nothing ever
        // stopping it, so it held its port for the life of the process.
        assertPortFree(firstPort, "port of the superseded attach must be released when it is replaced")

        close(second)
        assertPortFree(secondPort, "port of the live attach must be released by teardown")
    }

    @Test
    fun `attach contributes a closeable bean so the container tears telemetry down`() {
        System.setProperty("katalyst.telemetry.enabled", "true")

        val closeables = TelemetryFeature.provideBeanModules()
            .flatMap { it.definitions }
            .filter { AutoCloseable::class.java.isAssignableFrom(it.type.java) }

        assertTrue(
            closeables.isNotEmpty(),
            "telemetry must register an AutoCloseable bean; that is the only teardown edge the " +
                "container gives a feature without changing the KatalystFeature interface",
        )
    }

    @Test
    fun `closing the teardown bean releases the port, the descriptor and the active store`() {
        System.setProperty("katalyst.telemetry.enabled", "true")

        val teardowns = attach()
        val port = boundPort()
        val descriptor = descriptorPath()
        assertTrue(Files.exists(descriptor), "attach must publish a run descriptor for the TUI")

        close(teardowns)

        assertPortFree(port, "teardown must release the transport's loopback port")
        assertFalse(
            Files.exists(descriptor),
            "teardown must retire the run descriptor instead of leaving a stopped app advertised " +
                "as attachable until the JVM exits",
        )
        assertNull(
            TelemetryStore.active,
            "teardown must clear the process-global store; it pins the snapshot providers of a " +
                "container that no longer exists",
        )
    }

    /**
     * Attaches telemetry the way bootstrap does — one `provideBeanModules()` pass — and materializes
     * its definitions exactly once, as the bean engine does at registration time. Returns the
     * closeables the engine would track for this attach.
     */
    private fun attach(): List<AutoCloseable> {
        val modules = TelemetryFeature.provideBeanModules()
        assertTrue(modules.isNotEmpty(), "telemetry must attach when enabled")
        val teardowns = modules
            .flatMap { it.definitions }
            .map { it.provider(KatalystBeanContext(UnusedContainer)) }
            .filterIsInstance<AutoCloseable>()
        open += teardowns
        return teardowns
    }

    /** Closes newest-first, the order the bean engine uses when the container stops. */
    private fun close(teardowns: List<AutoCloseable>) {
        teardowns.asReversed().forEach { it.close() }
        open.removeAll { candidate -> teardowns.any { it === candidate } }
    }

    private fun boundPort(): Int {
        val port = TelemetryStore.active?.snapshot()?.meta?.telemetryPort
        assertTrue(port != null && port > 0, "attach must bind a concrete loopback port")
        return port
    }

    /**
     * Bound explicitly to the loopback address: a wildcard `ServerSocket(port)` can succeed alongside
     * an already-bound specific address on some platforms and would defeat the check.
     */
    private fun assertPortFree(port: Int, message: String) {
        assertTrue(runCatching { ServerSocket(port, 1, loopback).close() }.isSuccess, message)
    }

    /** Mirrors `RunDescriptorWriter`'s own path resolution so the test asserts on the real file. */
    private fun descriptorPath(): Path {
        val xdg = System.getenv("XDG_STATE_HOME")?.takeIf { it.isNotBlank() }
        val base = if (xdg != null) {
            Path.of(xdg, "katalyst")
        } else {
            Path.of(System.getProperty("user.home") ?: ".", ".katalyst")
        }
        return base.resolve("run").resolve("${ProcessHandle.current().pid()}.json")
    }

    /** The teardown bean resolves nothing, so its provider never touches the container. */
    private object UnusedContainer : KatalystContainer {
        override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
            error("telemetry bean providers must not resolve from the container")

        override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? = null

        override fun <T : Any> getAll(type: KClass<T>): List<T> = emptyList()

        override fun contains(type: KClass<*>, qualifier: String?): Boolean = false
    }
}
