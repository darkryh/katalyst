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
 * - Input validation
 * - Extension functions (wrap)
 */
class ServerConfigurationTest {

    // ========== BASIC CONSTRUCTION TESTS ==========

    @Test
    fun `ServerConfiguration should use provided engine`() {
        val mockNetty = MockEngine("netty")
        val config = ServerConfiguration(engine = mockNetty)

        assertEquals(mockNetty, config.engine)
        assertEquals("netty", config.engine.engineType)
        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
        assertEquals(Runtime.getRuntime().availableProcessors() * 2, config.workerThreads)
        assertEquals(180000L, config.connectionIdleTimeoutMs)
        assertNull(config.serverWrapper)
        assertNull(config.applicationWrapper)
    }

    @Test
    fun `ServerConfiguration should support different engine types`() {
        val mockJetty = MockEngine("jetty")
        val config = ServerConfiguration(engine = mockJetty)
        assertEquals(mockJetty, config.engine)
        assertEquals("jetty", config.engine.engineType)
    }

    @Test
    fun `ServerConfiguration should support CIO engine type`() {
        val mockCio = MockEngine("cio")
        val config = ServerConfiguration(engine = mockCio)
        assertEquals(mockCio, config.engine)
        assertEquals("cio", config.engine.engineType)
    }

    @Test
    fun `ServerConfiguration should support custom host`() {
        val engine = MockEngine("netty")
        val config = ServerConfiguration(engine = engine, host = "127.0.0.1")
        assertEquals("127.0.0.1", config.host)
    }

    @Test
    fun `ServerConfiguration should support custom port`() {
        val engine = MockEngine("netty")
        val config = ServerConfiguration(engine = engine, port = 9090)
        assertEquals(9090, config.port)
    }

    @Test
    fun `ServerConfiguration should support custom worker threads`() {
        val engine = MockEngine("netty")
        val config = ServerConfiguration(engine = engine, workerThreads = 16)
        assertEquals(16, config.workerThreads)
    }

    @Test
    fun `ServerConfiguration should support custom idle timeout`() {
        val engine = MockEngine("netty")
        val config = ServerConfiguration(engine = engine, connectionIdleTimeoutMs = 300000)
        assertEquals(300000L, config.connectionIdleTimeoutMs)
    }

    // ========== VALIDATION TESTS ==========

    @Test
    fun `ServerConfiguration should reject blank host`() {
        val engine = MockEngine("netty")
        assertFails {
            ServerConfiguration(engine = engine, host = "")
        }
    }

    @Test
    fun `ServerConfiguration should reject port below 1`() {
        val engine = MockEngine("netty")
        assertFails {
            ServerConfiguration(engine = engine, port = 0)
        }
    }

    @Test
    fun `ServerConfiguration should reject port above 65535`() {
        val engine = MockEngine("netty")
        assertFails {
            ServerConfiguration(engine = engine, port = 65536)
        }
    }

    @Test
    fun `ServerConfiguration should accept port 1`() {
        val engine = MockEngine("netty")
        val config = ServerConfiguration(engine = engine, port = 1)
        assertEquals(1, config.port)
    }

    @Test
    fun `ServerConfiguration should accept port 65535`() {
        val engine = MockEngine("netty")
        val config = ServerConfiguration(engine = engine, port = 65535)
        assertEquals(65535, config.port)
    }

    @Test
    fun `ServerConfiguration should reject zero worker threads`() {
        val engine = MockEngine("netty")
        assertFails {
            ServerConfiguration(engine = engine, workerThreads = 0)
        }
    }

    @Test
    fun `ServerConfiguration should reject negative worker threads`() {
        val engine = MockEngine("netty")
        assertFails {
            ServerConfiguration(engine = engine, workerThreads = -1)
        }
    }

    @Test
    fun `ServerConfiguration should reject zero idle timeout`() {
        val engine = MockEngine("netty")
        assertFails {
            ServerConfiguration(engine = engine, connectionIdleTimeoutMs = 0)
        }
    }

    @Test
    fun `ServerConfiguration should reject negative idle timeout`() {
        val engine = MockEngine("netty")
        assertFails {
            ServerConfiguration(engine = engine, connectionIdleTimeoutMs = -1)
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
        val config = ServerConfiguration(engine = engine, serverWrapper = wrapper)

        assertEquals(wrapper, config.serverWrapper)
    }

    @Test
    fun `ServerConfiguration should support application wrapper`() {
        val wrapper: ApplicationWrapper = { app -> app }
        val engine = MockEngine("netty")
        val config = ServerConfiguration(engine = engine, applicationWrapper = wrapper)

        assertEquals(wrapper, config.applicationWrapper)
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `ServerConfiguration should support copy`() {
        val engine = MockEngine("netty")
        val original = ServerConfiguration(engine = engine, port = 8080)
        val copied = original.copy(port = 9090)

        assertEquals(engine, copied.engine)
        assertEquals(9090, copied.port)
        assertEquals(8080, original.port)
    }

    @Test
    fun `ServerConfiguration should support equality`() {
        val engine = MockEngine("netty")
        val config1 = ServerConfiguration(engine = engine, port = 8080)
        val config2 = ServerConfiguration(engine = engine, port = 8080)

        assertEquals(config1, config2)
    }

    @Test
    fun `ServerConfiguration should support inequality`() {
        val engine = MockEngine("netty")
        val config1 = ServerConfiguration(engine = engine, port = 8080)
        val config2 = ServerConfiguration(engine = engine, port = 9090)

        assertNotEquals(config1, config2)
    }

    @Test
    fun `ServerConfiguration should support hashCode`() {
        val engine = MockEngine("jetty")
        val config1 = ServerConfiguration(engine = engine, port = 8080)
        val config2 = ServerConfiguration(engine = engine, port = 8080)

        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun `ServerConfiguration should support toString`() {
        val engine = MockEngine("cio")
        val config = ServerConfiguration(engine = engine, port = 3000)
        val string = config.toString()

        assertTrue(string.contains("ServerConfiguration"))
        assertTrue(string.contains("cio"))
        assertTrue(string.contains("3000"))
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `production Netty server configuration`() {
        val engine = MockEngine("netty")
        val config = ServerConfiguration(
            engine = engine,
            host = "0.0.0.0",
            port = 8080,
            workerThreads = 32,
            connectionIdleTimeoutMs = 300000
        )

        assertEquals(engine, config.engine)
        assertEquals(8080, config.port)
        assertEquals(32, config.workerThreads)
    }

    @Test
    fun `development server configuration with CIO`() {
        val engine = MockEngine("cio")
        val config = ServerConfiguration(
            engine = engine,
            host = "127.0.0.1",
            port = 3000,
            workerThreads = 4
        )

        assertEquals(engine, config.engine)
        assertEquals("127.0.0.1", config.host)
        assertEquals(3000, config.port)
    }

    @Test
    fun `high-performance Jetty configuration`() {
        val engine = MockEngine("jetty")
        val config = ServerConfiguration(
            engine = engine,
            host = "0.0.0.0",
            port = 8443,
            workerThreads = 64,
            connectionIdleTimeoutMs = 600000
        )

        assertEquals(engine, config.engine)
        assertEquals(64, config.workerThreads)
        assertEquals(600000L, config.connectionIdleTimeoutMs)
    }

    @Test
    fun `microservice configuration with CIO engine`() {
        val engine = MockEngine("cio")
        val config = ServerConfiguration(engine = engine)

        assertEquals("cio", config.engine.engineType)
        assertEquals(8080, config.port)
    }

    @Test
    fun `custom port configuration for different environments`() {
        val engine = MockEngine("netty")
        val devConfig = ServerConfiguration(engine = engine, port = 3000)
        val stagingConfig = ServerConfiguration(engine = engine, port = 8080)
        val prodConfig = ServerConfiguration(engine = engine, port = 80)

        assertEquals(3000, devConfig.port)
        assertEquals(8080, stagingConfig.port)
        assertEquals(80, prodConfig.port)
    }

    @Test
    fun `localhost-only server configuration`() {
        val engine = MockEngine("netty")
        val config = ServerConfiguration(engine = engine, host = "127.0.0.1", port = 8080)

        assertEquals("127.0.0.1", config.host)
        assertEquals(8080, config.port)
    }

    @Test
    fun `multi-threaded server configuration`() {
        val cpuCount = Runtime.getRuntime().availableProcessors()
        val engine = MockEngine("netty")
        val config = ServerConfiguration(
            engine = engine,
            workerThreads = cpuCount * 4,
            connectionIdleTimeoutMs = 180000
        )

        assertEquals(cpuCount * 4, config.workerThreads)
        assertEquals(180000L, config.connectionIdleTimeoutMs)
    }

    @Test
    fun `server with custom wrapper for SSL configuration`() {
        val sslWrapper: ServerWrapper = { engine ->
            // Simulated SSL configuration
            engine
        }

        val engine = MockEngine("netty")
        val config = ServerConfiguration(
            engine = engine,
            port = 8443,
            serverWrapper = sslWrapper
        )

        assertEquals(8443, config.port)
        assertNotNull(config.serverWrapper)
    }
}
