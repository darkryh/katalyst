package io.github.darkryh.katalyst.scanner.scanner

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.scanner.core.DiscoveryConfig
import io.github.darkryh.katalyst.scanner.core.EmptyDiscoverySeverity
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReflectionsTypeScannerEmptyResultSeverityTest {

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

    @Test
    fun `empty discovery logs INFO when configured as INFO`() {
        val scanner = ReflectionsTypeScanner(
            Marker::class.java,
            DiscoveryConfig(
                scanPackages = listOf("io.github.darkryh.katalyst.scanner.no_such_package"),
                emptyResultSeverity = EmptyDiscoverySeverity.INFO
            )
        )

        scanner.discover()

        assertTrue(appender.list.any { it.level == Level.INFO && it.formattedMessage.contains("No Marker implementations") })
        assertFalse(appender.list.any { it.level == Level.WARN && it.formattedMessage.contains("No Marker implementations") })
    }

    @Test
    fun `empty discovery can suppress logs with NONE`() {
        val scanner = ReflectionsTypeScanner(
            Marker::class.java,
            DiscoveryConfig(
                scanPackages = listOf("io.github.darkryh.katalyst.scanner.no_such_package"),
                emptyResultSeverity = EmptyDiscoverySeverity.NONE
            )
        )

        scanner.discover()

        assertFalse(appender.list.any { it.formattedMessage.contains("No Marker implementations") })
    }
}

private interface Marker
