package io.github.darkryh.katalyst.scheduler

import io.github.darkryh.katalyst.di.KatalystFeaturesBuilder
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import org.slf4j.LoggerFactory

/**
 * Public feature object so advanced users can register the scheduler via [io.github.darkryh.katalyst.di.config.KatalystDIOptions].
 */
object SchedulerFeature : KatalystFeature {
    private val logger = LoggerFactory.getLogger("SchedulerFeature")
    override val id: String = "scheduler"

    override fun provideBeanModules(): List<KatalystBeanModule> {
        logger.info("Loading scheduler feature modules")
        return listOf(schedulerModule())
    }

    override fun onReady(context: KatalystBeanContext) {
        logger.info("Scheduler feature ready (SchedulerService available)")
    }
}

/**
 * Enables scheduler support for the current Katalyst application by registering
 * the scheduler feature with the application builder.
 */
fun KatalystFeaturesBuilder.enableScheduler(): KatalystFeaturesBuilder =
    feature(SchedulerFeature)
