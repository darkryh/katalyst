package io.github.darkryh.katalyst.scheduler

import io.github.darkryh.katalyst.di.lifecycle.ApplicationReadyInitializer
import io.github.darkryh.katalyst.scheduler.lifecycle.SchedulerInitializer
import io.github.darkryh.katalyst.scheduler.service.SchedulerService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module that registers:
 * - [SchedulerService]: The core scheduler implementation
 * - [SchedulerInitializer]: Runtime-ready initializer for scheduler method discovery/invocation
 *
 * The [SchedulerInitializer] is registered as [ApplicationReadyInitializer] so it is
 * executed only after runtime readiness is confirmed.
 */
fun schedulerModule(): Module = module {
    single { SchedulerService() }
    single<ApplicationReadyInitializer> { SchedulerInitializer() }
}
