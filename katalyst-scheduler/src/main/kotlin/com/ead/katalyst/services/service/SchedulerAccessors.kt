package com.ead.katalyst.services.service

import com.ead.katalyst.services.Service
import org.koin.core.error.NoDefinitionFoundException


fun Service.requireScheduler(): SchedulerService =
    runCatching { getKoin().get<SchedulerService>() }
        .getOrElse { error ->
            if (error is NoDefinitionFoundException) {
                throw IllegalStateException(
                    "SchedulerService is not registered. " +
                        "Ensure the katalyst-scheduler module is loaded (schedulerDIModule())."
                )
            } else {
                throw error
            }
        }
