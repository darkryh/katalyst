package io.github.darkryh.katalyst.scheduler.extension

import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.scheduler.SchedulerJobsBuilder
import io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle
import io.github.darkryh.katalyst.scheduler.schedulerJobs
import io.github.darkryh.katalyst.scheduler.service.SchedulerService
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.InternalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job


fun Service.requireScheduler(): ServiceScheduler =
    KatalystContainerProvider.current().getOrNull(SchedulerService::class)?.let(::ServiceScheduler)
        ?: throw IllegalStateException(
            "SchedulerService is not registered. " +
                "Ensure katalyst-scheduler is on the classpath and features { enableScheduler() } was called."
        )

class ServiceScheduler internal constructor(
    private val scheduler: SchedulerService,
) {
    fun jobs(block: SchedulerJobsBuilder.() -> Unit): SchedulerJobHandle {
        val handles = schedulerJobs(block).registerWith(scheduler)
        return CompositeSchedulerJobHandle(handles)
    }
}

@OptIn(InternalForInheritanceCoroutinesApi::class)
private class CompositeSchedulerJobHandle(
    private val handles: List<SchedulerJobHandle>,
    private val delegate: CompletableJob = Job(),
) : SchedulerJobHandle, Job by delegate {
    init {
        delegate.invokeOnCompletion {
            handles.forEach { handle -> handle.cancel() }
        }
        if (handles.isEmpty()) {
            // An empty jobs {} block has nothing to wait on: complete immediately so the
            // returned handle isn't a no-op that never finishes.
            delegate.complete()
        } else {
            handles.forEach { handle ->
                handle.invokeOnCompletion {
                    if (handles.all { it.isCompleted }) {
                        delegate.complete()
                    }
                }
            }
        }
    }
}
