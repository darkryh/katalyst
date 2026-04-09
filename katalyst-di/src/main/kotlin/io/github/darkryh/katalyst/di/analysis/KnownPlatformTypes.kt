package io.github.darkryh.katalyst.di.analysis

import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.core.transaction.DatabaseTransactionManager
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.events.bus.EventBus
import org.koin.core.Koin
import org.slf4j.Logger
import kotlin.reflect.KClass

/**
 * Central registry for framework/feature contracts used by dependency analysis.
 *
 * Keeping this in one place avoids string drift across analyzer call sites.
 */
internal object KnownPlatformTypes {
    val alwaysAvailableContracts: Set<KClass<*>> = setOf(
        DatabaseFactory::class,
        DatabaseTransactionManager::class,
        ConfigProvider::class,
        EventBus::class,
        Koin::class
    )

    // The scheduler module may or may not be on the classpath of katalyst-di.
    private val schedulerServiceCandidates = listOf(
        "io.github.darkryh.katalyst.scheduler.service.SchedulerService"
    )

    fun schedulerServiceKClassOrNull(): KClass<*>? =
        schedulerServiceCandidates.firstNotNullOfOrNull { loadKClassOrNull(it) }

    fun loadOptionalFeatureContracts(logger: Logger): Set<KClass<*>> {
        val resolved = mutableSetOf<KClass<*>>()

        schedulerServiceCandidates.forEach { className ->
            val loaded = loadKClassOrNull(className)
            if (loaded != null) {
                resolved += loaded
                logger.debug("Optional platform feature available: {}", className)
            } else {
                logger.debug("Optional platform feature not present: {}", className)
            }
        }

        return resolved
    }

    fun isKnownPlatformType(type: KClass<*>, optionalContracts: Set<KClass<*>>): Boolean {
        return type in alwaysAvailableContracts || type in optionalContracts
    }

    private fun loadKClassOrNull(className: String): KClass<*>? {
        return runCatching { Class.forName(className).kotlin }.getOrNull()
    }
}
