package io.github.darkryh.katalyst.scanner.scanner

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.scanner.core.DiscoveryConfig
import io.github.darkryh.katalyst.scanner.core.EmptyDiscoverySeverity
import io.github.darkryh.katalyst.scanner.pkgfilter.PackageFilterMarker
import io.github.darkryh.katalyst.scanner.pkgfilter.excluded.ExcludedService
import io.github.darkryh.katalyst.scanner.pkgfilter.excluded.deeper.DeeplyExcludedService
import io.github.darkryh.katalyst.scanner.pkgfilter.excludedish.PrefixLookalikeService
import io.github.darkryh.katalyst.scanner.pkgfilter.included.IncludedRootService
import io.github.darkryh.katalyst.scanner.pkgfilter.included.nested.IncludedNestedService
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioural tests for [DiscoveryConfig.excludePackages] and [DiscoveryConfig.includeSubPackages].
 *
 * Every assertion here goes through a real Reflections traversal of the
 * `io.github.darkryh.katalyst.scanner.pkgfilter` fixture tree - never through a
 * configuration round-trip - so the tests fail if the settings are accepted and ignored.
 */
class ReflectionsTypeScannerPackageFilterTest {

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

    private val logger = LoggerFactory.getLogger(ReflectionsTypeScanner::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    init {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterTest
    fun resetAppender() {
        appender.list.clear()
    }

    private fun scan(config: DiscoveryConfig<PackageFilterMarker>): Set<Class<out PackageFilterMarker>> =
        ReflectionsTypeScanner(PackageFilterMarker::class.java, config).discover()

    // ========== BASELINE: the fixture tree is fully discoverable ==========

    @Test
    fun `unfiltered scan discovers every fixture in the package tree`() {
        val discovered = scan(DiscoveryConfig(scanPackages = listOf(ROOT)))

        assertEquals(ALL_FIXTURES, discovered)
    }

    // ========== excludePackages ==========

    @Test
    fun `excludePackages removes types declared in the excluded package`() {
        val discovered = scan(
            DiscoveryConfig(
                scanPackages = listOf(ROOT),
                excludePackages = listOf(EXCLUDED)
            )
        )

        assertFalse(
            discovered.contains(ExcludedService::class.java),
            "excludePackages=[$EXCLUDED] must drop ExcludedService, but it was discovered"
        )
        assertTrue(discovered.contains(IncludedRootService::class.java))
    }

    @Test
    fun `excludePackages removes types nested below the excluded package`() {
        val discovered = scan(
            DiscoveryConfig(
                scanPackages = listOf(ROOT),
                excludePackages = listOf(EXCLUDED)
            )
        )

        assertFalse(
            discovered.contains(DeeplyExcludedService::class.java),
            "Exclusion must be transitive: $EXCLUDED.deeper is below the excluded package"
        )
    }

    @Test
    fun `excludePackages leaves exactly the surviving types`() {
        val discovered = scan(
            DiscoveryConfig(
                scanPackages = listOf(ROOT),
                excludePackages = listOf(EXCLUDED)
            )
        )

        assertEquals(
            setOf(
                IncludedRootService::class.java,
                IncludedNestedService::class.java,
                PrefixLookalikeService::class.java
            ),
            discovered
        )
    }

    @Test
    fun `excludePackages matches on package boundaries not raw string prefixes`() {
        val discovered = scan(
            DiscoveryConfig(
                scanPackages = listOf(ROOT),
                excludePackages = listOf(EXCLUDED)
            )
        )

        assertTrue(
            discovered.contains(PrefixLookalikeService::class.java),
            "$EXCLUDEDISH merely shares a string prefix with $EXCLUDED and must survive"
        )
    }

    @Test
    fun `multiple exclude packages are all honoured`() {
        val discovered = scan(
            DiscoveryConfig(
                scanPackages = listOf(ROOT),
                excludePackages = listOf(EXCLUDED, EXCLUDEDISH)
            )
        )

        assertEquals(
            setOf(IncludedRootService::class.java, IncludedNestedService::class.java),
            discovered
        )
    }

    @Test
    fun `excluded types are never reported to onDiscover`() {
        val announced = mutableListOf<String>()

        scan(
            DiscoveryConfig(
                scanPackages = listOf(ROOT),
                excludePackages = listOf(EXCLUDED),
                onDiscover = { announced.add(it.simpleName) }
            )
        )

        assertFalse(announced.contains("ExcludedService"), "onDiscover fired for an excluded type: $announced")
        assertFalse(announced.contains("DeeplyExcludedService"), "onDiscover fired for an excluded type: $announced")
        assertEquals(3, announced.size, "onDiscover should only fire for surviving types, got: $announced")
    }

    @Test
    fun `excluding the scanned package itself yields an empty result and honours the empty severity`() {
        val discovered = scan(
            DiscoveryConfig(
                scanPackages = listOf(ROOT),
                excludePackages = listOf(ROOT),
                emptyResultSeverity = EmptyDiscoverySeverity.WARN
            )
        )

        assertTrue(discovered.isEmpty(), "Excluding the scanned root must remove everything, got: $discovered")
        assertTrue(
            appender.list.any {
                it.level == Level.WARN && it.formattedMessage.contains("No PackageFilterMarker implementations")
            },
            "Filtering everything out must go through the empty-result severity path"
        )
    }

    @Test
    fun `empty excludePackages excludes nothing`() {
        val discovered = scan(
            DiscoveryConfig(
                scanPackages = listOf(ROOT),
                excludePackages = emptyList()
            )
        )

        assertEquals(ALL_FIXTURES, discovered)
    }

    // ========== includeSubPackages ==========

    @Test
    fun `includeSubPackages false restricts results to exactly the named package`() {
        val discovered = scan(
            DiscoveryConfig(
                scanPackages = listOf(INCLUDED),
                includeSubPackages = false
            )
        )

        assertEquals(setOf(IncludedRootService::class.java), discovered)
        assertFalse(
            discovered.contains(IncludedNestedService::class.java),
            "includeSubPackages=false must not return types from $INCLUDED.nested"
        )
    }

    @Test
    fun `includeSubPackages false with several named packages keeps only their direct members`() {
        val discovered = scan(
            DiscoveryConfig(
                scanPackages = listOf(INCLUDED, EXCLUDED),
                includeSubPackages = false
            )
        )

        assertEquals(
            setOf(IncludedRootService::class.java, ExcludedService::class.java),
            discovered
        )
    }

    @Test
    fun `includeSubPackages true keeps sub-package types`() {
        val discovered = scan(
            DiscoveryConfig(
                scanPackages = listOf(INCLUDED),
                includeSubPackages = true
            )
        )

        assertEquals(
            setOf(IncludedRootService::class.java, IncludedNestedService::class.java),
            discovered
        )
    }

    @Test
    fun `includeSubPackages false and excludePackages compose`() {
        val discovered = scan(
            DiscoveryConfig(
                scanPackages = listOf(INCLUDED, EXCLUDED),
                includeSubPackages = false,
                excludePackages = listOf(EXCLUDED)
            )
        )

        assertEquals(setOf(IncludedRootService::class.java), discovered)
    }

    @Test
    fun `package filtering runs before the predicate filter`() {
        val inspected = mutableListOf<String>()

        val discovered = scan(
            DiscoveryConfig(
                scanPackages = listOf(ROOT),
                excludePackages = listOf(EXCLUDED),
                predicate = io.github.darkryh.katalyst.scanner.core.DiscoveryPredicate { clazz ->
                    inspected.add(clazz.simpleName)
                    true
                }
            )
        )

        assertFalse(
            inspected.contains("ExcludedService"),
            "The predicate should never even see excluded types, saw: $inspected"
        )
        assertEquals(3, discovered.size)
    }
}
