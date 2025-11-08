package com.ead.katalyst.scheduler

import com.ead.katalyst.di.DIConfigurationBuilder

/**
 * Adds the scheduler Koin modules when using the lower-level DI builder API.
 */
fun DIConfigurationBuilder.schedulerModules(): DIConfigurationBuilder =
    customModules(schedulerModule())
