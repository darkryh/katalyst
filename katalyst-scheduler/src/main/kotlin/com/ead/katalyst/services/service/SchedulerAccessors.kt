package com.ead.katalyst.services.service

import com.ead.katalyst.services.Service
import org.koin.core.error.NoDefinitionFoundException

/**
 * Provides convenient access to the shared [SchedulerService] from any Katalyst [Service].
 */
val Service.scheduler: SchedulerService
    get() = getKoin().get()

val Service.schedulerOrNull: SchedulerService?
    get() = runCatching { getKoin().get<SchedulerService>() }.getOrNull()

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
