package io.github.darkryh.katalyst.scheduler

import io.github.darkryh.katalyst.di.lifecycle.ApplicationReadyInitializer
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.scheduler.lifecycle.SchedulerInitializer
import io.github.darkryh.katalyst.scheduler.service.SchedulerService

/**
 * Katalyst bean module that registers:
 * - [SchedulerService]: The core scheduler implementation
 * - [SchedulerInitializer]: Runtime-ready initializer for scheduler method discovery/invocation
 *
 * The [SchedulerInitializer] is registered as [ApplicationReadyInitializer] so it is
 * executed only after runtime readiness is confirmed.
 */
internal fun schedulerModule(): KatalystBeanModule = katalystBeanModule {
    single<SchedulerService> { SchedulerService() }
    single<ApplicationReadyInitializer> { SchedulerInitializer() }
}
