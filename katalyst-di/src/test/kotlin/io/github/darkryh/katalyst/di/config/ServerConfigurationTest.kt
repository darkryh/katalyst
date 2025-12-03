package io.github.darkryh.katalyst.di.config

import io.github.darkryh.katalyst.testing.core.testEmbeddedServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerConfigurationTest {

    private fun deployment(
        host: String = "0.0.0.0",
        port: Int = 8080,
        workerGroupSize: Int = 8,
        idleTimeout: Long = 180000L
    ) = ServerDeploymentConfiguration(
        host = host,
        port = port,
        shutdownGracePeriod = 1000L,
        shutdownTimeout = 5000L,
        connectionGroupSize = 8,
        workerGroupSize = workerGroupSize,
        callGroupSize = 8,
        maxInitialLineLength = 4096,
        maxHeaderSize = 8192,
        maxChunkSize = 8192,
        connectionIdleTimeoutMs = idleTimeout
    )

    @Test
    fun `uses provided engine and exposes deployment fields`() {
        val config = ServerConfiguration(
            engine = testEmbeddedServer(),
            deployment = ServerDeploymentConfiguration.createDefault()
        )

        assertTrue(config.engine != null)
        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
        assertEquals(8, config.workerThreads)
        assertEquals(180000L, config.connectionIdleTimeoutMs)
        assertNull(config.serverWrapper)
        assertNull(config.applicationWrapper)
    }

    @Test
    fun `validates deployment values`() {
        assertFails { deployment(host = "") }
        assertFails { deployment(port = 0) }
        assertFails { deployment(workerGroupSize = 0) }
        assertFails { deployment(idleTimeout = 0) }
    }

    @Test
    fun `supports wrappers`() {
        val wrapper: ServerWrapper = { it }
        val appWrapper: ApplicationWrapper = { it }
        val config = ServerConfiguration(
            engine = testEmbeddedServer(),
            deployment = ServerDeploymentConfiguration.createDefault(),
            serverWrapper = wrapper,
            applicationWrapper = appWrapper
        )

        assertEquals(wrapper, config.serverWrapper)
        assertEquals(appWrapper, config.applicationWrapper)
    }

    @Test
    fun `data class copy preserves engine`() {
        val engine = testEmbeddedServer()
        val original = ServerConfiguration(engine = engine, deployment = deployment(port = 8080))
        val copied = original.copy(deployment = deployment(port = 9090))

        assertEquals(engine, copied.engine)
        assertEquals(9090, copied.port)
        assertEquals(8080, original.port)
    }
}
