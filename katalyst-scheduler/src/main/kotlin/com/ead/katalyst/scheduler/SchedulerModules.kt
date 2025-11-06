package com.ead.katalyst.scheduler

import com.ead.katalyst.services.service.SchedulerService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Minimal Koin module that registers the shared [SchedulerService].
 */
fun schedulerModule(): Module = module {
    single { SchedulerService() }
}
