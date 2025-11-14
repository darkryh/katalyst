package com.ead.katalyst.di.config

import com.ead.katalyst.di.config.test.MockEngine
import io.ktor.server.application.Application
import io.ktor.server.engine.ApplicationEngine
import kotlin.test.*

/**
 * Comprehensive tests for ServerConfiguration.
 *
 * Tests cover:
 * - ServerConfiguration data class with engine objects
 * - Factory methods (netty, jetty, cio)
 * - Input validation through ServerDeploymentConfiguration
 * - Extension functions (wrap)
 * - Bridge convenience accessors (host, port, workerThreads, connectionIdleTimeoutMs)
 */
class ServerConfigurationTest {

    private fun createDeploymentConfig(
        host: String = "0.0.0.0",
        port: Int = 8080,
        workerGroupSize: Int = 8,
        connectionIdleTimeoutMs: Long = 180000L
    ): ServerDeploymentConfiguration {
        return ServerDeploymentConfiguration(
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
            connectionIdleTimeoutMs = connectionIdleTimeoutMs
        )
    }

    // ========== BASIC CONSTRUCTION TESTS ==========

    @Test
    fun `ServerConfiguration should use provided engine`() {
        val mockNetty = MockEngine("netty")
        val deployment = ServerDeploymentConfiguration.createDefault()
        val config = ServerConfiguration(engine = mockNetty, deployment = deployment)

        assertEquals(mockNetty, config.engine)
        assertEquals("netty", config.engine.engineType)
        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
        assertEquals(8, config.workerThreads)  // workerGroupSize in deployment
        assertEquals(180000L, config.connectionIdleTimeoutMs)
        assertNull(config.serverWrapper)
        assertNull(config.applicationWrapper)
    }

    @Test
    fun `ServerConfiguration should support different engine types`() {
        val mockJetty = MockEngine("jetty")
        val deployment = ServerDeploymentConfiguration.createDefault()
        val config = ServerConfiguration(engine = mockJetty, deployment = deployment)
        assertEquals(mockJetty, config.engine)
        assertEquals("jetty", config.engine.engineType)
    }

    @Test
    fun `ServerConfiguration should support CIO engine type`() {
        val mockCio = MockEngine("cio")
        val deployment = ServerDeploymentConfiguration.createDefault()
        val config = ServerConfiguration(engine = mockCio, deployment = deployment)
        assertEquals(mockCio, config.engine)
        assertEquals("cio", config.engine.engineType)
    }

    @Test
    fun `ServerConfiguration should support custom host`() {
        val engine = MockEngine("netty")
        val deployment = createDeploymentConfig(host = "127.0.0.1")
        val config = ServerConfiguration(engine = engine, deployment = deployment)
        assertEquals("127.0.0.1", config.host)
    }

    @Test
    fun `ServerConfiguration should support custom port`() {
        val engine = MockEngine("netty")
        val deployment = createDeploymentConfig(port = 9090)
        val config = ServerConfiguration(engine = engine, deployment = deployment)
        assertEquals(9090, config.port)
    }

    @Test
    fun `ServerConfiguration should support custom worker threads`() {
        val engine = MockEngine("netty")
        val deployment = createDeploymentConfig(workerGroupSize = 16)
        val config = ServerConfiguration(engine = engine, deployment = deployment)
        assertEquals(16, config.workerThreads)
    }

    @Test
    fun `ServerConfiguration should support custom idle timeout`() {
        val engine = MockEngine("netty")
        val deployment = createDeploymentConfig(connectionIdleTimeoutMs = 300000L)
        val config = ServerConfiguration(engine = engine, deployment = deployment)
        assertEquals(300000L, config.connectionIdleTimeoutMs)
    }

    // ========== VALIDATION TESTS ==========

    @Test
    fun `ServerConfiguration should reject blank host via deployment validation`() {
        val engine = MockEngine("netty")
        assertFails {
            createDeploymentConfig(host = "")
        }
    }

    @Test
    fun `ServerConfiguration should reject port below 1 via deployment validation`() {
        val engine = MockEngine("netty")
        assertFails {
            createDeploymentConfig(port = 0)
        }
    }

    @Test
    fun `ServerConfiguration should reject port above 65535 via deployment validation`() {
        val engine = MockEngine("netty")
        assertFails {
            createDeploymentConfig(port = 65536)
        }
    }

    @Test
    fun `ServerConfiguration should accept port 1`() {
        val engine = MockEngine("netty")
        val deployment = createDeploymentConfig(port = 1)
        val config = ServerConfiguration(engine = engine, deployment = deployment)
        assertEquals(1, config.port)
    }

    @Test
    fun `ServerConfiguration should accept port 65535`() {
        val engine = MockEngine("netty")
        val deployment = createDeploymentConfig(port = 65535)
        val config = ServerConfiguration(engine = engine, deployment = deployment)
        assertEquals(65535, config.port)
    }

    @Test
    fun `ServerConfiguration should reject zero worker threads via deployment validation`() {
        val engine = MockEngine("netty")
        assertFails {
            createDeploymentConfig(workerGroupSize = 0)
        }
    }

    @Test
    fun `ServerConfiguration should reject negative worker threads via deployment validation`() {
        val engine = MockEngine("netty")
        assertFails {
            createDeploymentConfig(workerGroupSize = -1)
        }
    }

    @Test
    fun `ServerConfiguration should reject zero idle timeout via deployment validation`() {
        val engine = MockEngine("netty")
        assertFails {
            createDeploymentConfig(connectionIdleTimeoutMs = 0)
        }
    }

    @Test
    fun `ServerConfiguration should reject negative idle timeout via deployment validation`() {
        val engine = MockEngine("netty")
        assertFails {
            createDeploymentConfig(connectionIdleTimeoutMs = -1)
        }
    }

    // ========== FACTORY METHOD TESTS ==========
    // Note: Factory methods (netty(), jetty(), cio()) require actual engine modules
    // on the classpath and are tested via integration tests in their respective modules

    // ========== WRAPPER TESTS ==========

    @Test
    fun `ServerConfiguration should support server wrapper`() {
        val wrapper: ServerWrapper = { engine -> engine }
        val engine = MockEngine("netty")
        val deployment = ServerDeploymentConfiguration.createDefault()
        val config = ServerConfiguration(engine = engine, deployment = deployment, serverWrapper = wrapper)

        assertEquals(wrapper, config.serverWrapper)
    }

    @Test
    fun `ServerConfiguration should support application wrapper`() {
        val wrapper: ApplicationWrapper = { app -> app }
        val engine = MockEngine("netty")
        val deployment = ServerDeploymentConfiguration.createDefault()
        val config = ServerConfiguration(engine = engine, deployment = deployment, applicationWrapper = wrapper)

        assertEquals(wrapper, config.applicationWrapper)
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `ServerConfiguration should support copy`() {
        val engine = MockEngine("netty")
        val originalDeployment = createDeploymentConfig(port = 8080)
        val original = ServerConfiguration(engine = engine, deployment = originalDeployment)

        val newDeployment = createDeploymentConfig(port = 9090)
        val copied = original.copy(deployment = newDeployment)

        assertEquals(engine, copied.engine)
        assertEquals(9090, copied.port)
        assertEquals(8080, original.port)
    }

    @Test
    fun `ServerConfiguration should support equality`() {
        val engine = MockEngine("netty")
        val deployment1 = createDeploymentConfig(port = 8080)
        val deployment2 = createDeploymentConfig(port = 8080)

        val config1 = ServerConfiguration(engine = engine, deployment = deployment1)
        val config2 = ServerConfiguration(engine = engine, deployment = deployment2)

        assertEquals(config1, config2)
    }

    @Test
    fun `ServerConfiguration should support inequality`() {
        val engine = MockEngine("netty")
        val deployment1 = createDeploymentConfig(port = 8080)
        val deployment2 = createDeploymentConfig(port = 9090)

        val config1 = ServerConfiguration(engine = engine, deployment = deployment1)
        val config2 = ServerConfiguration(engine = engine, deployment = deployment2)

        assertNotEquals(config1, config2)
    }

    @Test
    fun `ServerConfiguration should support hashCode`() {
        val engine = MockEngine("jetty")
        val deployment1 = createDeploymentConfig(port = 8080)
        val deployment2 = createDeploymentConfig(port = 8080)

        val config1 = ServerConfiguration(engine = engine, deployment = deployment1)
        val config2 = ServerConfiguration(engine = engine, deployment = deployment2)

        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun `ServerConfiguration should support toString`() {
        val engine = MockEngine("cio")
        val deployment = createDeploymentConfig(port = 3000)
        val config = ServerConfiguration(engine = engine, deployment = deployment)
        val string = config.toString()

        assertTrue(string.contains("ServerConfiguration"))
        assertTrue(string.contains("cio"))
        assertTrue(string.contains("3000"))
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `production Netty server configuration`() {
        val engine = MockEngine("netty")
        val deployment = ServerDeploymentConfiguration(
            host = "0.0.0.0",
            port = 8080,
            shutdownGracePeriod = 1000L,
            shutdownTimeout = 5000L,
            connectionGroupSize = 8,
            workerGroupSize = 32,
            callGroupSize = 8,
            maxInitialLineLength = 4096,
            maxHeaderSize = 8192,
            maxChunkSize = 8192,
            connectionIdleTimeoutMs = 300000L
        )
        val config = ServerConfiguration(engine = engine, deployment = deployment)

        assertEquals(engine, config.engine)
        assertEquals(8080, config.port)
        assertEquals(32, config.workerThreads)
    }

    @Test
    fun `development server configuration with CIO`() {
        val engine = MockEngine("cio")
        val deployment = ServerDeploymentConfiguration(
            host = "127.0.0.1",
            port = 3000,
            shutdownGracePeriod = 1000L,
            shutdownTimeout = 5000L,
            connectionGroupSize = 8,
            workerGroupSize = 4,
            callGroupSize = 8,
            maxInitialLineLength = 4096,
            maxHeaderSize = 8192,
            maxChunkSize = 8192,
            connectionIdleTimeoutMs = 180000L
        )
        val config = ServerConfiguration(engine = engine, deployment = deployment)

        assertEquals(engine, config.engine)
        assertEquals("127.0.0.1", config.host)
        assertEquals(3000, config.port)
    }

    @Test
    fun `high-performance Jetty configuration`() {
        val engine = MockEngine("jetty")
        val deployment = ServerDeploymentConfiguration(
            host = "0.0.0.0",
            port = 8443,
            shutdownGracePeriod = 1000L,
            shutdownTimeout = 5000L,
            connectionGroupSize = 8,
            workerGroupSize = 64,
            callGroupSize = 8,
            maxInitialLineLength = 4096,
            maxHeaderSize = 8192,
            maxChunkSize = 8192,
            connectionIdleTimeoutMs = 600000L
        )
        val config = ServerConfiguration(engine = engine, deployment = deployment)

        assertEquals(engine, config.engine)
        assertEquals(64, config.workerThreads)
        assertEquals(600000L, config.connectionIdleTimeoutMs)
    }

    @Test
    fun `microservice configuration with CIO engine`() {
        val engine = MockEngine("cio")
        val deployment = ServerDeploymentConfiguration.createDefault()
        val config = ServerConfiguration(engine = engine, deployment = deployment)

        assertEquals("cio", config.engine.engineType)
        assertEquals(8080, config.port)
    }

    @Test
    fun `custom port configuration for different environments`() {
        val engine = MockEngine("netty")
        val devDeployment = createDeploymentConfig(port = 3000)
        val stagingDeployment = createDeploymentConfig(port = 8080)
        val prodDeployment = createDeploymentConfig(port = 80)

        val devConfig = ServerConfiguration(engine = engine, deployment = devDeployment)
        val stagingConfig = ServerConfiguration(engine = engine, deployment = stagingDeployment)
        val prodConfig = ServerConfiguration(engine = engine, deployment = prodDeployment)

        assertEquals(3000, devConfig.port)
        assertEquals(8080, stagingConfig.port)
        assertEquals(80, prodConfig.port)
    }

    @Test
    fun `localhost-only server configuration`() {
        val engine = MockEngine("netty")
        val deployment = createDeploymentConfig(host = "127.0.0.1", port = 8080)
        val config = ServerConfiguration(engine = engine, deployment = deployment)

        assertEquals("127.0.0.1", config.host)
        assertEquals(8080, config.port)
    }

    @Test
    fun `multi-threaded server configuration`() {
        val engine = MockEngine("netty")
        val deployment = ServerDeploymentConfiguration(
            host = "0.0.0.0",
            port = 8080,
            shutdownGracePeriod = 1000L,
            shutdownTimeout = 5000L,
            connectionGroupSize = 8,
            workerGroupSize = 16,  // Will be multiplied or used directly
            callGroupSize = 8,
            maxInitialLineLength = 4096,
            maxHeaderSize = 8192,
            maxChunkSize = 8192,
            connectionIdleTimeoutMs = 180000L
        )
        val config = ServerConfiguration(engine = engine, deployment = deployment)

        assertEquals(16, config.workerThreads)
        assertEquals(180000L, config.connectionIdleTimeoutMs)
    }

    @Test
    fun `server with custom wrapper for SSL configuration`() {
        val sslWrapper: ServerWrapper = { engine ->
            // Simulated SSL configuration
            engine
        }

        val engine = MockEngine("netty")
        val deployment = createDeploymentConfig(port = 8443)
        val config = ServerConfiguration(
            engine = engine,
            deployment = deployment,
            serverWrapper = sslWrapper
        )

        assertEquals(8443, config.port)
        assertNotNull(config.serverWrapper)
    }
}
