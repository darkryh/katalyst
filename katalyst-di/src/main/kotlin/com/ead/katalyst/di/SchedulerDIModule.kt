package com.ead.katalyst.di

import com.ead.katalyst.scheduler.schedulerModule
import org.koin.core.module.Module
import org.slf4j.LoggerFactory

/**
 * Registers the shared scheduler module for use across the application.
 *
 * The scheduler now relies on explicit registration by services, so the DI layer
 * only needs to provide the singleton [com.ead.katalyst.services.service.SchedulerService].
 */
fun schedulerDIModule(): Module {
    val logger = LoggerFactory.getLogger("SchedulerDIModule")
    logger.info("Initializing Scheduler DI Module")
    return schedulerModule()
}
