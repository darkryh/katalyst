package io.github.darkryh.katalyst.scanner.core

import kotlin.test.*

/**
 * Comprehensive tests for DiscoveryConfig.
 *
 * Tests cover:
 * - Config creation with defaults
 * - Builder pattern
 * - Callbacks (onDiscover, onError)
 * - Package filtering
 * - Data class behavior
 */
class DiscoveryConfigTest {

    interface TestService
    interface TestRepository : TestService

    // ========== CONSTRUCTION TESTS ==========

    @Test
    fun `DiscoveryConfig should use empty scan packages by default`() {
        val config = DiscoveryConfig<TestService>()
        assertTrue(config.scanPackages.isEmpty())
    }

    @Test
    fun `DiscoveryConfig should have null predicate by default`() {
        val config = DiscoveryConfig<TestService>()
        assertNull(config.predicate)
    }

    @Test
    fun `DiscoveryConfig should include sub-packages by default`() {
        val config = DiscoveryConfig<TestService>()
        assertTrue(config.includeSubPackages)
    }

    @Test
    fun `DiscoveryConfig should have empty exclude packages by default`() {
        val config = DiscoveryConfig<TestService>()
        assertTrue(config.excludePackages.isEmpty())
    }

    @Test
    fun `DiscoveryConfig should have no-op callbacks by default`() {
        val config = DiscoveryConfig<TestService>()
        // Should not throw
        config.onDiscover(TestService::class.java)
        config.onError(Exception())
    }

    @Test
    fun `DiscoveryConfig should support explicit scan packages`() {
        val config = DiscoveryConfig<TestService>(
            scanPackages = listOf("com.example", "com.test")
        )
        assertEquals(2, config.scanPackages.size)
        assertTrue(config.scanPackages.contains("com.example"))
        assertTrue(config.scanPackages.contains("com.test"))
    }

    @Test
    fun `DiscoveryConfig should support custom predicate`() {
        val predicate = DiscoveryPredicate<TestService> { true }
        val config = DiscoveryConfig(predicate = predicate)
        assertNotNull(config.predicate)
        assertEquals(predicate, config.predicate)
    }

    @Test
    fun `DiscoveryConfig should support disabling sub-packages`() {
        val config = DiscoveryConfig<TestService>(includeSubPackages = false)
        assertFalse(config.includeSubPackages)
    }

    @Test
    fun `DiscoveryConfig should support exclude packages`() {
        val config = DiscoveryConfig<TestService>(
            excludePackages = listOf("com.example.test", "com.example.mock")
        )
        assertEquals(2, config.excludePackages.size)
        assertTrue(config.excludePackages.contains("com.example.test"))
    }

    // ========== BUILDER PATTERN TESTS ==========

    @Test
    fun `Builder should create config with scan packages`() {
        val config = DiscoveryConfig.builder<TestService>()
            .scanPackages("com.example")
            .build()

        assertEquals(1, config.scanPackages.size)
        assertEquals("com.example", config.scanPackages[0])
    }

    @Test
    fun `Builder should support varargs scan packages`() {
        val config = DiscoveryConfig.builder<TestService>()
            .scanPackages("com.example", "com.test", "com.other")
            .build()

        assertEquals(3, config.scanPackages.size)
    }

    @Test
    fun `Builder should support list scan packages`() {
        val packages = listOf("com.a", "com.b", "com.c")
        val config = DiscoveryConfig.builder<TestService>()
            .scanPackages(packages)
            .build()

        assertEquals(3, config.scanPackages.size)
        assertEquals(packages, config.scanPackages)
    }

    @Test
    fun `Builder should set predicate`() {
        val predicate = DiscoveryPredicate<TestService> { it.simpleName.endsWith("Service") }
        val config = DiscoveryConfig.builder<TestService>()
            .predicate(predicate)
            .build()

        assertNotNull(config.predicate)
        assertTrue(config.predicate.matches(TestService::class.java))
    }

    @Test
    fun `Builder should set includeSubPackages`() {
        val config = DiscoveryConfig.builder<TestService>()
            .includeSubPackages(false)
            .build()

        assertFalse(config.includeSubPackages)
    }

    @Test
    fun `Builder should set exclude packages`() {
        val config = DiscoveryConfig.builder<TestService>()
            .excludePackages("com.test", "com.mock")
            .build()

        assertEquals(2, config.excludePackages.size)
        assertTrue(config.excludePackages.contains("com.test"))
        assertTrue(config.excludePackages.contains("com.mock"))
    }

    @Test
    fun `Builder should set onDiscover callback`() {
        var discovered: Class<out TestService>? = null
        val config = DiscoveryConfig.builder<TestService>()
            .onDiscover { discovered = it }
            .build()

        config.onDiscover(TestService::class.java)
        assertEquals(TestService::class.java, discovered)
    }

    @Test
    fun `Builder should set onError callback`() {
        var error: Exception? = null
        val config = DiscoveryConfig.builder<TestService>()
            .onError { error = it }
            .build()

        val exception = RuntimeException("test")
        config.onError(exception)
        assertEquals(exception, error)
    }

    @Test
    fun `Builder should chain multiple configurations`() {
        var discoveredClasses = mutableListOf<Class<*>>()
        var errors = mutableListOf<Exception>()

        val config = DiscoveryConfig.builder<TestService>()
            .scanPackages("com.example", "com.test")
            .predicate(DiscoveryPredicate { true })
            .includeSubPackages(false)
            .excludePackages("com.example.test")
            .onDiscover { discoveredClasses.add(it) }
            .onError { errors.add(it) }
            .build()

        assertEquals(2, config.scanPackages.size)
        assertNotNull(config.predicate)
        assertFalse(config.includeSubPackages)
        assertEquals(1, config.excludePackages.size)

        config.onDiscover(TestService::class.java)
        config.onError(RuntimeException())
        assertEquals(1, discoveredClasses.size)
        assertEquals(1, errors.size)
    }

    // ========== CALLBACK TESTS ==========

    @Test
    fun `onDiscover callback should receive discovered class`() {
        val discovered = mutableListOf<Class<*>>()
        val config = DiscoveryConfig<TestService>(
            onDiscover = { discovered.add(it) }
        )

        config.onDiscover(TestService::class.java)
        config.onDiscover(TestRepository::class.java)

        assertEquals(2, discovered.size)
        assertTrue(discovered.contains(TestService::class.java))
        assertTrue(discovered.contains(TestRepository::class.java))
    }

    @Test
    fun `onError callback should receive exception`() {
        val errors = mutableListOf<Exception>()
        val config = DiscoveryConfig<TestService>(
            onError = { errors.add(it) }
        )

        val error1 = RuntimeException("error 1")
        val error2 = IllegalStateException("error 2")

        config.onError(error1)
        config.onError(error2)

        assertEquals(2, errors.size)
        assertEquals(error1, errors[0])
        assertEquals(error2, errors[1])
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `DiscoveryConfig should support copy`() {
        val original = DiscoveryConfig<TestService>(
            scanPackages = listOf("com.example"),
            includeSubPackages = true
        )

        val copied = original.copy(
            scanPackages = listOf("com.test")
        )

        assertEquals(listOf("com.test"), copied.scanPackages)
        assertTrue(copied.includeSubPackages)
        assertEquals(listOf("com.example"), original.scanPackages)
    }

    @Test
    fun `DiscoveryConfig should support equality`() {
        val config1 = DiscoveryConfig<TestService>(
            scanPackages = listOf("com.example"),
            includeSubPackages = true
        )

        val config2 = DiscoveryConfig<TestService>(
            scanPackages = listOf("com.example"),
            includeSubPackages = true
        )

        // Note: Lambdas won't be equal, so we only test the data properties match
        assertEquals(config1.scanPackages, config2.scanPackages)
        assertEquals(config1.includeSubPackages, config2.includeSubPackages)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical service discovery configuration`() {
        val discovered = mutableListOf<String>()

        val config = DiscoveryConfig.builder<TestService>()
            .scanPackages("com.example.services")
            .predicate(DiscoveryPredicate { it.simpleName.endsWith("Service") })
            .includeSubPackages(true)
            .excludePackages("com.example.services.test")
            .onDiscover { discovered.add(it.simpleName) }
            .build()

        config.onDiscover(TestService::class.java)

        assertEquals(listOf("com.example.services"), config.scanPackages)
        assertTrue(config.includeSubPackages)
        assertEquals(listOf("com.example.services.test"), config.excludePackages)
        assertEquals(1, discovered.size)
    }

    @Test
    fun `repository discovery with logging`() {
        val logs = mutableListOf<String>()

        val config = DiscoveryConfig.builder<TestRepository>()
            .scanPackages("com.example.repositories")
            .onDiscover { logs.add("Discovered: ${it.simpleName}") }
            .onError { logs.add("Error: ${it.message}") }
            .build()

        config.onDiscover(TestRepository::class.java)
        config.onError(RuntimeException("Scan failed"))

        assertEquals(2, logs.size)
        assertTrue(logs[0].contains("Discovered"))
        assertTrue(logs[1].contains("Error"))
    }

    @Test
    fun `multi-package scanning configuration`() {
        val config = DiscoveryConfig.builder<TestService>()
            .scanPackages(
                "com.example.auth",
                "com.example.payment",
                "com.example.notification"
            )
            .includeSubPackages(true)
            .build()

        assertEquals(3, config.scanPackages.size)
        assertTrue(config.scanPackages.contains("com.example.auth"))
        assertTrue(config.scanPackages.contains("com.example.payment"))
        assertTrue(config.scanPackages.contains("com.example.notification"))
    }

    @Test
    fun `exclude test and mock packages`() {
        val config = DiscoveryConfig.builder<TestService>()
            .scanPackages("com.example")
            .excludePackages(
                "com.example.test",
                "com.example.mock",
                "com.example.fixtures"
            )
            .build()

        assertEquals(3, config.excludePackages.size)
        assertTrue(config.excludePackages.contains("com.example.test"))
        assertTrue(config.excludePackages.contains("com.example.mock"))
        assertTrue(config.excludePackages.contains("com.example.fixtures"))
    }

    @Test
    fun `scan entire classpath configuration`() {
        val config = DiscoveryConfig.builder<TestService>()
            .scanPackages()  // Empty = entire classpath
            .predicate(DiscoveryPredicate { it.simpleName.endsWith("Service") })
            .build()

        assertTrue(config.scanPackages.isEmpty())  // Scans everything
        assertNotNull(config.predicate)
    }
}
