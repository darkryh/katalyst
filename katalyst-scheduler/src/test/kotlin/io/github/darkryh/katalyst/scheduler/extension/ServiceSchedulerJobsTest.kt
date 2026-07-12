package io.github.darkryh.katalyst.scheduler.extension

import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.scheduler.TestSchedulerContainer
import io.github.darkryh.katalyst.scheduler.service.SchedulerService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceSchedulerJobsTest {

    private var scheduler: SchedulerService? = null

    @AfterTest
    fun tearDown() {
        scheduler?.close()
        scheduler = null
        KatalystContainerProvider.reset()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `jobs with an empty block returns an already-completed handle`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val schedulerService = SchedulerService(dispatcher)
        scheduler = schedulerService
        KatalystContainerProvider.set(
            TestSchedulerContainer(mapOf(SchedulerService::class to schedulerService))
        )

        val service = EmptyJobsService()
        val handle = service.registerNothing()

        // Before the fix this hung forever: an empty jobs {} block never completed
        // the composite handle it returned, since there were no child handles to
        // trigger the completion callback.
        withTimeout(2_000) {
            handle.join()
        }

        assertEquals(true, handle.isCompleted)
        assertEquals(false, handle.isActive)
    }
}

private class EmptyJobsService : Service {
    private val scheduler = requireScheduler()

    fun registerNothing() = scheduler.jobs { }
}
