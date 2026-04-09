package io.github.darkryh.katalyst.di.lifecycle

import org.koin.core.Koin
import org.slf4j.LoggerFactory

/**
 * Executes runtime-ready initializers after application readiness.
 */
internal class RuntimeReadyInitializerRunner(private val koin: Koin) {
    private val logger = LoggerFactory.getLogger("RuntimeReadyInitializerRunner")

    suspend fun invokeAll() {
        val lifecycleStart = System.currentTimeMillis()
        val koinInitializers = runCatching {
            koin.getAll<ApplicationReadyInitializer>()
        }.getOrElse { emptyList() }
        val registryInitializers = ApplicationReadyInitializerRegistry.getAll()
        val initializers = (registryInitializers + koinInitializers)
            .distinctBy { it::class }
            .sortedWith(runtimeReadyInitializerOrderComparator)

        if (initializers.isEmpty()) {
            logger.info("Runtime-ready lifecycle completed: no initializers registered")
            return
        }

        logger.info("Runtime-ready lifecycle starting: {} initializer(s)", initializers.size)
        if (logger.isDebugEnabled) {
            initializers.forEach { init ->
                logger.debug(
                    "Runtime-ready initializer queued [order={}]: {}",
                    init.order,
                    init.initializerId
                )
            }
        }

        initializers.forEach { initializer ->
            val startTime = System.currentTimeMillis()
            logger.debug("Runtime-ready initializer starting: {}", initializer.initializerId)

            runCatching {
                initializer.onRuntimeReady()
            }.onFailure { e ->
                val duration = System.currentTimeMillis() - startTime
                logger.error("✗  Runtime-ready initializer failed: {} ({} ms) - {}",
                    initializer.initializerId, duration, e.message)
                val initException = e as? LifecycleException ?: InitializerFailedException(
                    initializerName = initializer.initializerId,
                    message = e.message ?: "Unknown runtime-ready initializer error",
                    cause = e
                )
                throw initException
            }

            val duration = System.currentTimeMillis() - startTime
            logger.debug(
                "Runtime-ready initializer completed: {} ({} ms)",
                initializer.initializerId,
                duration
            )
        }

        val totalDuration = System.currentTimeMillis() - lifecycleStart
        logger.info(
            "Runtime-ready lifecycle completed: {} initializer(s) in {} ms",
            initializers.size,
            totalDuration
        )
    }
}

internal val runtimeReadyInitializerOrderComparator: Comparator<ApplicationReadyInitializer> =
    compareBy<ApplicationReadyInitializer> { it.order }
        .thenBy { it::class.qualifiedName ?: it::class.simpleName ?: "" }
