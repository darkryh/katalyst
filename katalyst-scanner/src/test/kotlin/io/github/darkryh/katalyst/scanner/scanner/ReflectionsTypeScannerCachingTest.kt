package io.github.darkryh.katalyst.scanner.scanner

import io.github.darkryh.katalyst.scanner.core.DiscoveryConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private interface MarkerOne
private interface MarkerTwo

private class MarkerOneImpl : MarkerOne
private class MarkerTwoImpl : MarkerTwo

/**
 * Verifies that [ReflectionsTypeScanner] builds its (expensive) [org.reflections.Reflections]
 * index once per scan-package-set and reuses it across [ReflectionsTypeScanner.discover] calls,
 * even across separate scanner instances discovering different marker types - mirroring how
 * bootstrap discovers many marker interfaces (Service, Component, CrudRepository, ...) against
 * the same configured packages.
 */
class ReflectionsTypeScannerCachingTest {

    @BeforeTest
    fun resetCache() {
        ReflectionsTypeScanner.resetCache()
    }

    @AfterTest
    fun cleanupCache() {
        ReflectionsTypeScanner.resetCache()
    }

    @Test
    fun `repeated discover calls for different marker types reuse a single cached scan`() {
        val scanPackages = listOf("io.github.darkryh.katalyst.scanner.scanner")

        val scannerOne = ReflectionsTypeScanner(
            MarkerOne::class.java,
            DiscoveryConfig(scanPackages = scanPackages)
        )
        val scannerTwo = ReflectionsTypeScanner(
            MarkerTwo::class.java,
            DiscoveryConfig(scanPackages = scanPackages)
        )

        assertEquals(0, ReflectionsTypeScanner.scanBuildCount.get())

        val resultsOne = scannerOne.discover()
        assertEquals(1, ReflectionsTypeScanner.scanBuildCount.get(), "First discover() should build the index once")

        val resultsTwo = scannerTwo.discover()
        assertEquals(
            1,
            ReflectionsTypeScanner.scanBuildCount.get(),
            "discover() for a different marker type over the same packages must reuse the cached index"
        )

        // Calling discover() again (same scanner, and a brand-new scanner instance over the
        // same package set) must still not trigger another scan.
        scannerOne.discover()
        ReflectionsTypeScanner(MarkerOne::class.java, DiscoveryConfig(scanPackages = scanPackages)).discover()
        assertEquals(1, ReflectionsTypeScanner.scanBuildCount.get())

        // Discovery results themselves must be unaffected by the caching.
        assertTrue(resultsOne.contains(MarkerOneImpl::class.java))
        assertTrue(resultsTwo.contains(MarkerTwoImpl::class.java))
    }

    @Test
    fun `a different scan-package-set triggers a fresh scan`() {
        val scannerA = ReflectionsTypeScanner(
            MarkerOne::class.java,
            DiscoveryConfig(scanPackages = listOf("io.github.darkryh.katalyst.scanner.scanner"))
        )
        val scannerB = ReflectionsTypeScanner(
            MarkerOne::class.java,
            DiscoveryConfig(scanPackages = listOf("io.github.darkryh.katalyst.scanner.core"))
        )

        scannerA.discover()
        assertEquals(1, ReflectionsTypeScanner.scanBuildCount.get())

        scannerB.discover()
        assertEquals(2, ReflectionsTypeScanner.scanBuildCount.get(), "A distinct package set must build its own index")
    }
}
