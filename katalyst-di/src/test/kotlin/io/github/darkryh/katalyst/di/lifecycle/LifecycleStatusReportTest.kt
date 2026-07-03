package io.github.darkryh.katalyst.di.lifecycle

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LifecycleStatusReportTest {

    @AfterTest
    fun tearDown() {
        BootstrapProgress.clear()
        StartupWarnings.clear()
    }

    @Test
    fun `snapshot includes lifecycle statuses and warning counters`() {
        BootstrapProgress.clear()
        StartupWarnings.clear()
        BootstrapProgress.startLifecycle(BootstrapLifecycle.BEAN_CONTAINER_BOOTSTRAP)
        BootstrapProgress.completeLifecycle(BootstrapLifecycle.BEAN_CONTAINER_BOOTSTRAP, "bootstrapped")
        StartupWarnings.add("scheduler", "none", StartupWarningsAggregator.WarningSeverity.INFO)

        val report = LifecycleStatusReport.snapshot()

        assertTrue(
            report.lifecycles.any {
                it.lifecycleRef == BootstrapLifecycle.BEAN_CONTAINER_BOOTSTRAP.lifecycleRef &&
                    it.status == "COMPLETED"
            }
        )
        assertTrue(
            report.lifecycles.any {
                it.lifecycleRef == BootstrapLifecycle.RUNTIME_READY_INITIALIZERS.lifecycleRef
            }
        )
        assertEquals(1, report.warningCounts.info)
    }
}
