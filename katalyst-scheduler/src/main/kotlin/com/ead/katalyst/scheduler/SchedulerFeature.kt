package com.ead.katalyst.scheduler

import com.ead.katalyst.di.KatalystApplicationBuilder
import com.ead.katalyst.di.features.KatalystFeature
import org.koin.core.Koin
import org.koin.core.module.Module
import org.slf4j.LoggerFactory

/**
 * Public feature object so advanced users can register the scheduler via [KatalystDIOptions].
 */
object SchedulerFeature : KatalystFeature {
    private val logger = LoggerFactory.getLogger("SchedulerFeature")
    override val id: String = "scheduler"

    override fun provideModules(): List<Module> {
        logger.info("Loading scheduler feature modules")
        return listOf(schedulerModule())
    }

    override fun onKoinReady(koin: Koin) {
        logger.info("Scheduler feature ready (SchedulerService available)")
    }
}

/**
 * Enables scheduler support for the current Katalyst application by registering
 * the scheduler feature with the application builder.
 */
fun KatalystApplicationBuilder.enableScheduler(): KatalystApplicationBuilder =
    feature(SchedulerFeature)
