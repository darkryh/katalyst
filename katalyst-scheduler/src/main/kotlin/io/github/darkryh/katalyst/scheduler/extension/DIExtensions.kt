package io.github.darkryh.katalyst.scheduler.extension

import io.github.darkryh.katalyst.di.config.DIConfigurationBuilder
import io.github.darkryh.katalyst.scheduler.schedulerModule

/**
 * Adds the scheduler Koin modules when using the lower-level DI builder API.
 */
fun DIConfigurationBuilder.schedulerModules(): DIConfigurationBuilder =
    customModules(schedulerModule())
