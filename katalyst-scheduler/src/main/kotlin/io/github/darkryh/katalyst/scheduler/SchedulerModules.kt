package io.github.darkryh.katalyst.scheduler

import io.github.darkryh.katalyst.di.lifecycle.ApplicationInitializer
import io.github.darkryh.katalyst.scheduler.lifecycle.SchedulerInitializer
import io.github.darkryh.katalyst.scheduler.service.SchedulerService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module that registers:
 * - [SchedulerService]: The core scheduler implementation
 * - [SchedulerInitializer]: The lifecycle initializer for scheduler method discovery and invocation
 *
 * The [SchedulerInitializer] is registered as [ApplicationInitializer] so it is automatically
 * discovered by [InitializerRegistry] during the application initialization phase.
 */
fun schedulerModule(): Module = module {
    single { SchedulerService() }
    single<ApplicationInitializer> { SchedulerInitializer() }
}
