package io.github.darkryh.katalyst.scheduler

import io.github.darkryh.katalyst.di.KatalystApplicationBuilder
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import org.koin.core.Koin
import org.koin.core.module.Module
import org.slf4j.LoggerFactory

/**
 * Public feature object so advanced users can register the scheduler via [io.github.darkryh.katalyst.di.config.KatalystDIOptions].
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
