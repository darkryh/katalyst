package io.github.darkryh.katalyst.di.lifecycle

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StartupWarningsAggregatorTest {

    private val aggregator = StartupWarningsAggregator()

    @AfterTest
    fun tearDown() {
        aggregator.clear()
        StartupWarnings.clear()
    }

    @Test
    fun `aggregator tracks warnings by severity`() {
        aggregator.addWarning("Config", "Missing DATABASE_URL", StartupWarningsAggregator.WarningSeverity.CRITICAL)
        aggregator.addWarning("Scheduler", "No jobs registered", StartupWarningsAggregator.WarningSeverity.WARNING)
        aggregator.addWarning("Feature", "Websocket feature disabled")

        assertEquals(3, aggregator.getWarnings().size)
        assertEquals(1, aggregator.getCountBySeverity(StartupWarningsAggregator.WarningSeverity.CRITICAL))
        assertEquals(1, aggregator.getCountBySeverity(StartupWarningsAggregator.WarningSeverity.WARNING))
        assertEquals(1, aggregator.getCountBySeverity(StartupWarningsAggregator.WarningSeverity.INFO))
    }

    @Test
    fun `startup warnings facade delegates to aggregator`() {
        StartupWarnings.clear()
        StartupWarnings.add("Config", "DB URL missing", StartupWarningsAggregator.WarningSeverity.CRITICAL)
        StartupWarnings.add("Feature", "Scheduler disabled", StartupWarningsAggregator.WarningSeverity.INFO)

        assertEquals(2, StartupWarnings.get().size)
        assertEquals(1, StartupWarnings.countCritical())
        assertEquals(0, StartupWarnings.countWarning())
        assertEquals(1, StartupWarnings.countInfo())

        StartupWarnings.clear()
        assertEquals(0, StartupWarnings.get().size)
    }
}
