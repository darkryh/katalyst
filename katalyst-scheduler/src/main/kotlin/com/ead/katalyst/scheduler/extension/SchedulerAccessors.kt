package com.ead.katalyst.scheduler.extension

import com.ead.katalyst.core.component.Service
import com.ead.katalyst.scheduler.service.SchedulerService
import org.koin.core.error.NoDefinitionFoundException


fun Service.requireScheduler(): SchedulerService =
    runCatching { getKoin().get<SchedulerService>() }
        .getOrElse { error ->
            if (error is NoDefinitionFoundException) {
                throw IllegalStateException(
                    "SchedulerService is not registered. " +
                        "Ensure katalyst-scheduler is on the classpath and enableScheduler() was called."
                )
            } else {
                throw error
            }
        }
