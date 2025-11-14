package com.ead.katalyst.di.config

import io.ktor.server.application.Application
import io.ktor.server.engine.ApplicationEngine
import kotlin.test.*

/**
 * Comprehensive tests for ServerConfiguration and related classes.
 *
 * Tests cover:
 * - ServerConfiguration data class
 * - Factory methods (netty, jetty, cio)
 * - Input validation
 * - ServerConfigurationBuilder
 * - ServerEngines utility
 * - Extension functions (wrap)
 */
class ServerConfigurationTest {

    // ========== BASIC CONSTRUCTION TESTS ==========

    @Test
    fun `ServerConfiguration should use default values`() {
        val config = ServerConfiguration()

        assertEquals("netty", config.engineType)
        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
        assertEquals(Runtime.getRuntime().availableProcessors() * 2, config.workerThreads)
        assertEquals(180000L, config.connectionIdleTimeoutMs)
        assertNull(config.serverWrapper)
        assertNull(config.applicationWrapper)
    }

    @Test
    fun `ServerConfiguration should support custom engine type`() {
        val config = ServerConfiguration(engineType = "jetty")
        assertEquals("jetty", config.engineType)
    }

    @Test
    fun `ServerConfiguration should support custom host`() {
        val config = ServerConfiguration(host = "127.0.0.1")
        assertEquals("127.0.0.1", config.host)
    }

    @Test
    fun `ServerConfiguration should support custom port`() {
        val config = ServerConfiguration(port = 9090)
        assertEquals(9090, config.port)
    }

    @Test
    fun `ServerConfiguration should support custom worker threads`() {
        val config = ServerConfiguration(workerThreads = 16)
        assertEquals(16, config.workerThreads)
    }

    @Test
    fun `ServerConfiguration should support custom idle timeout`() {
        val config = ServerConfiguration(connectionIdleTimeoutMs = 300000)
        assertEquals(300000L, config.connectionIdleTimeoutMs)
    }

    // ========== VALIDATION TESTS ==========

    @Test
    fun `ServerConfiguration should reject blank engine type`() {
        assertFails {
            ServerConfiguration(engineType = "")
        }
    }

    @Test
    fun `ServerConfiguration should reject blank host`() {
        assertFails {
            ServerConfiguration(host = "")
        }
    }

    @Test
    fun `ServerConfiguration should reject port below 1`() {
        assertFails {
            ServerConfiguration(port = 0)
        }
    }

    @Test
    fun `ServerConfiguration should reject port above 65535`() {
        assertFails {
            ServerConfiguration(port = 65536)
        }
    }

    @Test
    fun `ServerConfiguration should accept port 1`() {
        val config = ServerConfiguration(port = 1)
        assertEquals(1, config.port)
    }

    @Test
    fun `ServerConfiguration should accept port 65535`() {
        val config = ServerConfiguration(port = 65535)
        assertEquals(65535, config.port)
    }

    @Test
    fun `ServerConfiguration should reject zero worker threads`() {
        assertFails {
            ServerConfiguration(workerThreads = 0)
        }
    }

    @Test
    fun `ServerConfiguration should reject negative worker threads`() {
        assertFails {
            ServerConfiguration(workerThreads = -1)
        }
    }

    @Test
    fun `ServerConfiguration should reject zero idle timeout`() {
        assertFails {
            ServerConfiguration(connectionIdleTimeoutMs = 0)
        }
    }

    @Test
    fun `ServerConfiguration should reject negative idle timeout`() {
        assertFails {
            ServerConfiguration(connectionIdleTimeoutMs = -1)
        }
    }

    // ========== FACTORY METHOD TESTS ==========

    @Test
    fun `ServerConfiguration netty() should create Netty configuration`() {
        val config = ServerConfiguration.netty()

        assertEquals("netty", config.engineType)
        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
    }

    @Test
    fun `ServerConfiguration jetty() should create Jetty configuration`() {
        val config = ServerConfiguration.jetty()

        assertEquals("jetty", config.engineType)
        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
    }

    @Test
    fun `ServerConfiguration cio() should create CIO configuration`() {
        val config = ServerConfiguration.cio()

        assertEquals("cio", config.engineType)
        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
    }

    @Test
    fun `ServerConfiguration netty() should support custom wrappers`() {
        val serverWrapper: ServerWrapper = { it }
        val appWrapper: ApplicationWrapper = { it }

        val config = ServerConfiguration.netty(
            serverWrapper = serverWrapper,
            applicationWrapper = appWrapper
        )

        assertEquals("netty", config.engineType)
        assertEquals(serverWrapper, config.serverWrapper)
        assertEquals(appWrapper, config.applicationWrapper)
    }

    // ========== WRAPPER TESTS ==========

    @Test
    fun `ServerConfiguration should support server wrapper`() {
        val wrapper: ServerWrapper = { engine -> engine }
        val config = ServerConfiguration(serverWrapper = wrapper)

        assertEquals(wrapper, config.serverWrapper)
    }

    @Test
    fun `ServerConfiguration should support application wrapper`() {
        val wrapper: ApplicationWrapper = { app -> app }
        val config = ServerConfiguration(applicationWrapper = wrapper)

        assertEquals(wrapper, config.applicationWrapper)
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `ServerConfiguration should support copy`() {
        val original = ServerConfiguration(engineType = "netty", port = 8080)
        val copied = original.copy(port = 9090)

        assertEquals("netty", copied.engineType)
        assertEquals(9090, copied.port)
        assertEquals(8080, original.port)
    }

    @Test
    fun `ServerConfiguration should support equality`() {
        val config1 = ServerConfiguration(engineType = "netty", port = 8080)
        val config2 = ServerConfiguration(engineType = "netty", port = 8080)

        assertEquals(config1, config2)
    }

    @Test
    fun `ServerConfiguration should support inequality`() {
        val config1 = ServerConfiguration(port = 8080)
        val config2 = ServerConfiguration(port = 9090)

        assertNotEquals(config1, config2)
    }

    @Test
    fun `ServerConfiguration should support hashCode`() {
        val config1 = ServerConfiguration(engineType = "jetty", port = 8080)
        val config2 = ServerConfiguration(engineType = "jetty", port = 8080)

        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun `ServerConfiguration should support toString`() {
        val config = ServerConfiguration(engineType = "cio", port = 3000)
        val string = config.toString()

        assertTrue(string.contains("ServerConfiguration"))
        assertTrue(string.contains("cio"))
        assertTrue(string.contains("3000"))
    }

    // ========== BUILDER TESTS ==========

    @Test
    fun `ServerConfigurationBuilder should build with defaults`() {
        val config = ServerConfigurationBuilder().build()

        assertEquals("netty", config.engineType)
        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
    }

    @Test
    fun `ServerConfigurationBuilder should support engineType`() {
        val config = ServerConfigurationBuilder()
            .engineType("jetty")
            .build()

        assertEquals("jetty", config.engineType)
    }

    @Test
    fun `ServerConfigurationBuilder should support netty() shortcut`() {
        val config = ServerConfigurationBuilder()
            .netty()
            .build()

        assertEquals("netty", config.engineType)
    }

    @Test
    fun `ServerConfigurationBuilder should support jetty() shortcut`() {
        val config = ServerConfigurationBuilder()
            .jetty()
            .build()

        assertEquals("jetty", config.engineType)
    }

    @Test
    fun `ServerConfigurationBuilder should support cio() shortcut`() {
        val config = ServerConfigurationBuilder()
            .cio()
            .build()

        assertEquals("cio", config.engineType)
    }

    @Test
    fun `ServerConfigurationBuilder should support host`() {
        val config = ServerConfigurationBuilder()
            .host("192.168.1.1")
            .build()

        assertEquals("192.168.1.1", config.host)
    }

    @Test
    fun `ServerConfigurationBuilder should support port`() {
        val config = ServerConfigurationBuilder()
            .port(3000)
            .build()

        assertEquals(3000, config.port)
    }

    @Test
    fun `ServerConfigurationBuilder should support workerThreads`() {
        val config = ServerConfigurationBuilder()
            .workerThreads(24)
            .build()

        assertEquals(24, config.workerThreads)
    }

    @Test
    fun `ServerConfigurationBuilder should support connectionIdleTimeout`() {
        val config = ServerConfigurationBuilder()
            .connectionIdleTimeout(600000)
            .build()

        assertEquals(600000L, config.connectionIdleTimeoutMs)
    }

    @Test
    fun `ServerConfigurationBuilder should support server wrapper`() {
        val wrapper: ServerWrapper = { it }
        val config = ServerConfigurationBuilder()
            .withServerWrapper(wrapper)
            .build()

        assertEquals(wrapper, config.serverWrapper)
    }

    @Test
    fun `ServerConfigurationBuilder should support application wrapper`() {
        val wrapper: ApplicationWrapper = { it }
        val config = ServerConfigurationBuilder()
            .withApplicationWrapper(wrapper)
            .build()

        assertEquals(wrapper, config.applicationWrapper)
    }

    @Test
    fun `ServerConfigurationBuilder should support method chaining`() {
        val config = ServerConfigurationBuilder()
            .jetty()
            .host("localhost")
            .port(9000)
            .workerThreads(8)
            .connectionIdleTimeout(120000)
            .build()

        assertEquals("jetty", config.engineType)
        assertEquals("localhost", config.host)
        assertEquals(9000, config.port)
        assertEquals(8, config.workerThreads)
        assertEquals(120000L, config.connectionIdleTimeoutMs)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `production Netty server configuration`() {
        val config = ServerConfiguration(
            engineType = "netty",
            host = "0.0.0.0",
            port = 8080,
            workerThreads = 32,
            connectionIdleTimeoutMs = 300000
        )

        assertEquals("netty", config.engineType)
        assertEquals(8080, config.port)
        assertEquals(32, config.workerThreads)
    }

    @Test
    fun `development server configuration`() {
        val config = ServerConfiguration(
            engineType = "cio",
            host = "127.0.0.1",
            port = 3000,
            workerThreads = 4
        )

        assertEquals("cio", config.engineType)
        assertEquals("127.0.0.1", config.host)
        assertEquals(3000, config.port)
    }

    @Test
    fun `high-performance Jetty configuration`() {
        val config = ServerConfigurationBuilder()
            .jetty()
            .host("0.0.0.0")
            .port(8443)
            .workerThreads(64)
            .connectionIdleTimeout(600000)
            .build()

        assertEquals("jetty", config.engineType)
        assertEquals(64, config.workerThreads)
        assertEquals(600000L, config.connectionIdleTimeoutMs)
    }

    @Test
    fun `microservice configuration with CIO`() {
        val config = ServerConfiguration.cio()

        assertEquals("cio", config.engineType)
        assertEquals(8080, config.port)
    }

    @Test
    fun `custom port configuration for different environments`() {
        val devConfig = ServerConfiguration(port = 3000)
        val stagingConfig = ServerConfiguration(port = 8080)
        val prodConfig = ServerConfiguration(port = 80)

        assertEquals(3000, devConfig.port)
        assertEquals(8080, stagingConfig.port)
        assertEquals(80, prodConfig.port)
    }

    @Test
    fun `localhost-only server configuration`() {
        val config = ServerConfiguration(host = "127.0.0.1", port = 8080)

        assertEquals("127.0.0.1", config.host)
        assertEquals(8080, config.port)
    }

    @Test
    fun `multi-threaded server configuration`() {
        val cpuCount = Runtime.getRuntime().availableProcessors()
        val config = ServerConfiguration(
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

        val config = ServerConfiguration(
            engineType = "netty",
            port = 8443,
            serverWrapper = sslWrapper
        )

        assertEquals(8443, config.port)
        assertNotNull(config.serverWrapper)
    }
}
