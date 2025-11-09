package com.ead.katalyst.scheduler.extension

import com.ead.katalyst.di.config.DIConfigurationBuilder
import com.ead.katalyst.scheduler.schedulerModule

/**
 * Adds the scheduler Koin modules when using the lower-level DI builder API.
 */
fun DIConfigurationBuilder.schedulerModules(): DIConfigurationBuilder =
    customModules(schedulerModule())
