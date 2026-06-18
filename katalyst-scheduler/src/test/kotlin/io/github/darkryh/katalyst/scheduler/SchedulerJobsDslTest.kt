package io.github.darkryh.katalyst.scheduler

import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.internal.ServiceRegistry
import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
import io.github.darkryh.katalyst.scheduler.lifecycle.SchedulerInitializer
import io.github.darkryh.katalyst.scheduler.service.SchedulerService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SchedulerJobsDslTest {

    @BeforeTest
    fun setUp() {
        KatalystContainerProvider.reset()
        ServiceRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        ServiceRegistry.clear()
        KatalystContainerProvider.reset()
    }

    @Test
    fun `schedulerJobs creates explicit cron fixed rate and fixed delay declarations`() {
        val definitions = schedulerJobs {
            cron("scheduler.test.cron", "0 0 * * * ?") {}
            fixedRate("scheduler.test.fixed-rate", 30.seconds) {}
            fixedDelay(
                config = ScheduleConfig(
                    taskName = "scheduler.test.fixed-delay",
                    tags = setOf("maintenance")
                ),
                delay = 1.minutes
            ) {}
        }.toList()

        assertEquals(3, definitions.size)
        assertIs<CronSchedulerJobDefinition>(definitions[0])
        assertIs<FixedRateSchedulerJobDefinition>(definitions[1])
        assertIs<FixedDelaySchedulerJobDefinition>(definitions[2])
        assertEquals("scheduler.test.fixed-delay", definitions[2].config.taskName)
        assertEquals(setOf("maintenance"), definitions[2].config.tags)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `runtime ready registers service scheduler DSL jobs`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val schedulerService = SchedulerService(dispatcher)

        KatalystContainerProvider.set(
            TestSchedulerContainer(
                mapOf(SchedulerService::class to schedulerService)
            )
        )
        val service = ExplicitOneTimeSchedulerService()
        ServiceRegistry.register(service)

        SchedulerInitializer().onRuntimeReady()
        advanceUntilIdle()

        assertEquals(1, service.runCount)
        schedulerService.stop()
    }
}

private class ExplicitOneTimeSchedulerService : Service {
    private val scheduler = requireScheduler()
    var runCount: Int = 0

    fun scheduledJob() = scheduler.jobs {
        oneTime("scheduler.test.explicit-one-time") {
            runCount++
        }
    }
}
