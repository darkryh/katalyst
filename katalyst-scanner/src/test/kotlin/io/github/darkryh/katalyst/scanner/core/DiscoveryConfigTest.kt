package io.github.darkryh.katalyst.scanner.core

import io.github.darkryh.katalyst.scanner.pkgfilter.PackageFilterMarker
import io.github.darkryh.katalyst.scanner.pkgfilter.excluded.ExcludedService
import io.github.darkryh.katalyst.scanner.pkgfilter.excluded.deeper.DeeplyExcludedService
import io.github.darkryh.katalyst.scanner.pkgfilter.excludedish.PrefixLookalikeService
import io.github.darkryh.katalyst.scanner.pkgfilter.included.IncludedRootService
import io.github.darkryh.katalyst.scanner.pkgfilter.included.nested.IncludedNestedService
import io.github.darkryh.katalyst.scanner.scanner.ReflectionsTypeScanner
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
 *
 * **Package filtering is asserted through real traversal.** `includeSubPackages` and
 * `excludePackages` used to be verified by writing a value and reading it back, which passed
 * happily while the scanner ignored both settings. Every test touching those two fields now runs
 * an actual [ReflectionsTypeScanner] scan over the `io.github.darkryh.katalyst.scanner.pkgfilter`
 * fixture tree and asserts on the discovered types.
 */
class DiscoveryConfigTest {

    interface TestService
    interface TestRepository : TestService

    private companion object {
        const val ROOT = "io.github.darkryh.katalyst.scanner.pkgfilter"
        const val INCLUDED = "$ROOT.included"
        const val EXCLUDED = "$ROOT.excluded"
        const val EXCLUDEDISH = "$ROOT.excludedish"

        val ALL_FIXTURES: Set<Class<out PackageFilterMarker>> = setOf(
            IncludedRootService::class.java,
            IncludedNestedService::class.java,
            ExcludedService::class.java,
            DeeplyExcludedService::class.java,
            PrefixLookalikeService::class.java
        )
    }

    /** Runs a real classpath traversal for [config] and returns what survived filtering. */
    private fun discover(config: DiscoveryConfig<PackageFilterMarker>): Set<Class<out PackageFilterMarker>> =
        ReflectionsTypeScanner(PackageFilterMarker::class.java, config).discover()

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
        val config = DiscoveryConfig<PackageFilterMarker>(scanPackages = listOf(INCLUDED))

        assertTrue(config.includeSubPackages)
        assertEquals(
            setOf(IncludedRootService::class.java, IncludedNestedService::class.java),
            discover(config),
            "The default includeSubPackages=true must traverse into sub-packages"
        )
    }

    @Test
    fun `DiscoveryConfig should have empty exclude packages by default`() {
        val config = DiscoveryConfig<PackageFilterMarker>(scanPackages = listOf(ROOT))

        assertTrue(config.excludePackages.isEmpty())
        assertEquals(ALL_FIXTURES, discover(config), "An empty excludePackages must drop nothing")
    }

    @Test
    fun `DiscoveryConfig should have no-op callbacks by default`() {
        val config = DiscoveryConfig<TestService>()
        // Should not throw
        config.onDiscover(TestService::class.java)
        config.onError(Exception())
    }

    @Test
    fun `DiscoveryConfig should use WARN for empty results by default`() {
        val config = DiscoveryConfig<TestService>()
        assertEquals(EmptyDiscoverySeverity.WARN, config.emptyResultSeverity)
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
        val config = DiscoveryConfig<PackageFilterMarker>(
            scanPackages = listOf(INCLUDED),
            includeSubPackages = false
        )

        assertFalse(config.includeSubPackages)
        assertEquals(
            setOf(IncludedRootService::class.java),
            discover(config),
            "includeSubPackages=false must stop at the named package"
        )
    }

    @Test
    fun `DiscoveryConfig should support exclude packages`() {
        val config = DiscoveryConfig<PackageFilterMarker>(
            scanPackages = listOf(ROOT),
            excludePackages = listOf(EXCLUDED, EXCLUDEDISH)
        )

        assertEquals(2, config.excludePackages.size)
        assertEquals(
            setOf(IncludedRootService::class.java, IncludedNestedService::class.java),
            discover(config),
            "Both excluded packages (and everything below them) must be gone"
        )
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
        val config = DiscoveryConfig.builder<PackageFilterMarker>()
            .scanPackages(INCLUDED)
            .includeSubPackages(false)
            .build()

        assertFalse(config.includeSubPackages)
        assertEquals(
            setOf(IncludedRootService::class.java),
            discover(config),
            "A builder-configured includeSubPackages=false must reach the traversal"
        )
    }

    @Test
    fun `Builder should set exclude packages`() {
        val config = DiscoveryConfig.builder<PackageFilterMarker>()
            .scanPackages(ROOT)
            .excludePackages(EXCLUDED, EXCLUDEDISH)
            .build()

        assertEquals(2, config.excludePackages.size)
        assertEquals(
            setOf(IncludedRootService::class.java, IncludedNestedService::class.java),
            discover(config),
            "Builder-configured excludePackages must reach the traversal"
        )
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
        val discoveredClasses = mutableListOf<Class<*>>()
        val errors = mutableListOf<Exception>()

        val config = DiscoveryConfig.builder<PackageFilterMarker>()
            .scanPackages(INCLUDED, EXCLUDED)
            .predicate(DiscoveryPredicate { true })
            .includeSubPackages(false)
            .excludePackages(EXCLUDED)
            .onDiscover { discoveredClasses.add(it) }
            .onError { errors.add(it) }
            .emptyResultSeverity(EmptyDiscoverySeverity.INFO)
            .build()

        assertEquals(2, config.scanPackages.size)
        assertNotNull(config.predicate)
        assertFalse(config.includeSubPackages)
        assertEquals(1, config.excludePackages.size)
        assertEquals(EmptyDiscoverySeverity.INFO, config.emptyResultSeverity)

        // Every chained setting must survive into a real traversal: sub-packages are cut off and
        // the excluded package is dropped, leaving only the type declared directly in `included`.
        assertEquals(setOf(IncludedRootService::class.java), discover(config))
        assertEquals(listOf<Class<*>>(IncludedRootService::class.java), discoveredClasses)

        config.onError(RuntimeException())
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
        val original = DiscoveryConfig<PackageFilterMarker>(
            scanPackages = listOf(ROOT),
            excludePackages = listOf(EXCLUDED, EXCLUDEDISH)
        )

        val copied = original.copy(scanPackages = listOf(INCLUDED))

        assertEquals(listOf(INCLUDED), copied.scanPackages)
        assertEquals(listOf(ROOT), original.scanPackages)

        // The copy keeps the filters, and they still take effect against the new scan root.
        assertEquals(
            setOf(IncludedRootService::class.java, IncludedNestedService::class.java),
            discover(copied)
        )
        assertEquals(
            setOf(IncludedRootService::class.java, IncludedNestedService::class.java),
            discover(original)
        )
    }

    @Test
    fun `DiscoveryConfig should support equality`() {
        val config1 = DiscoveryConfig<PackageFilterMarker>(
            scanPackages = listOf(INCLUDED),
            includeSubPackages = false
        )

        val config2 = DiscoveryConfig<PackageFilterMarker>(
            scanPackages = listOf(INCLUDED),
            includeSubPackages = false
        )

        // Note: Lambdas won't be equal, so we only test the data properties match
        assertEquals(config1.scanPackages, config2.scanPackages)
        assertEquals(config1.includeSubPackages, config2.includeSubPackages)

        // Equal filter settings must also produce identical discovery results.
        assertEquals(discover(config1), discover(config2))
        assertEquals(setOf(IncludedRootService::class.java), discover(config1))
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical service discovery configuration`() {
        val discovered = mutableListOf<String>()

        val config = DiscoveryConfig.builder<PackageFilterMarker>()
            .scanPackages(ROOT)
            .predicate(DiscoveryPredicate { it.simpleName.endsWith("Service") })
            .includeSubPackages(true)
            .excludePackages(EXCLUDED)
            .onDiscover { discovered.add(it.simpleName) }
            .build()

        val result = discover(config)

        assertEquals(listOf(ROOT), config.scanPackages)
        assertTrue(config.includeSubPackages)
        assertEquals(listOf(EXCLUDED), config.excludePackages)
        assertEquals(
            setOf(
                IncludedRootService::class.java,
                IncludedNestedService::class.java,
                PrefixLookalikeService::class.java
            ),
            result,
            "Sub-packages are kept, the excluded package (and everything below it) is not"
        )
        assertEquals(3, discovered.size)
        assertFalse(discovered.contains("ExcludedService"))
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
        val config = DiscoveryConfig.builder<PackageFilterMarker>()
            .scanPackages(INCLUDED, EXCLUDED, EXCLUDEDISH)
            .includeSubPackages(true)
            .build()

        assertEquals(3, config.scanPackages.size)
        assertTrue(config.scanPackages.contains(INCLUDED))
        assertTrue(config.scanPackages.contains(EXCLUDED))
        assertTrue(config.scanPackages.contains(EXCLUDEDISH))

        // All three roots plus their sub-packages are traversed.
        assertEquals(ALL_FIXTURES, discover(config))
    }

    @Test
    fun `exclude test and mock packages`() {
        val config = DiscoveryConfig.builder<PackageFilterMarker>()
            .scanPackages(ROOT)
            .excludePackages(
                EXCLUDED,
                EXCLUDEDISH,
                "$INCLUDED.nested"
            )
            .build()

        assertEquals(3, config.excludePackages.size)
        assertEquals(
            setOf(IncludedRootService::class.java),
            discover(config),
            "Each excluded package must be honoured, including a nested one"
        )
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
