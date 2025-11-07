package com.ead.katalyst.services.service

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerServiceTest {

    private var scheduler: SchedulerService? = null

    @AfterTest
    fun tearDown() {
        scheduler?.close()
        scheduler = null
    }

    @Test
    fun `scheduleFixedDelay runs repeatedly with configured cadence`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        scheduler = SchedulerService(dispatcher)

        var executions = 0
        val config = ScheduleConfig(
            taskName = "fixed-delay",
            initialDelay = 100.milliseconds
        )
        val job = scheduler!!.scheduleFixedDelay(
            config = config,
            task = { executions++ },
            fixedDelay = 200.milliseconds
        )

        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, executions)

        advanceTimeBy(200)
        runCurrent()
        assertEquals(2, executions)

        job.cancelAndJoin()
    }

    @Test
    fun `schedule with zero fixed rate runs only once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        scheduler = SchedulerService(dispatcher)

        var executions = 0
        val config = ScheduleConfig(
            taskName = "one-off",
            initialDelay = Duration.ZERO
        )
        val job = scheduler!!.schedule(
            config = config,
            task = { executions++ },
            fixedRate = Duration.ZERO
        )

        runCurrent()
        assertEquals(1, executions)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, executions)
        assertEquals(false, job.isActive)
    }

    @Test
    fun `schedule with fixed rate executes repeatedly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        scheduler = SchedulerService(dispatcher)

        var executions = 0
        val config = ScheduleConfig(
            taskName = "repeating",
            initialDelay = 50.milliseconds
        )
        val job = scheduler!!.schedule(
            config = config,
            task = { executions++ },
            fixedRate = 100.milliseconds
        )

        advanceTimeBy(50)
        runCurrent()
        assertEquals(1, executions)

        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, executions)

        advanceTimeBy(100)
        runCurrent()
        assertEquals(3, executions)

        job.cancelAndJoin()
    }

}
