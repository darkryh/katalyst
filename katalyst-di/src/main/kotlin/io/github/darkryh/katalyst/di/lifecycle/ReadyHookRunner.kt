package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.getAll
import org.slf4j.LoggerFactory

/**
 * Executes ready hooks after application readiness.
 */
internal class ReadyHookRunner(private val container: KatalystContainer) {
    private val logger = LoggerFactory.getLogger("ReadyHookRunner")

    suspend fun invokeAll() {
        val lifecycleStart = System.currentTimeMillis()
        val containerHooks = runCatching {
            container.getAll<ReadyHook>()
        }.getOrElse { emptyList() }
        val registryHooks = ReadyHookRegistry.getAll()
        val hooks = (registryHooks + containerHooks)
            .distinctBy { it::class }
            .sortedWith(readyHookOrderComparator)

        if (hooks.isEmpty()) {
            logger.info("Ready hook lifecycle completed: no hooks registered")
            return
        }

        logger.info("Ready hook lifecycle starting: {} hook(s)", hooks.size)
        if (logger.isDebugEnabled) {
            hooks.forEach { hook ->
                logger.debug(
                    "Ready hook queued [order={}]: {}",
                    hook.order,
                    hook.id
                )
            }
        }

        hooks.forEach { hook ->
            val startTime = System.currentTimeMillis()
            logger.debug("Ready hook starting: {}", hook.id)

            runCatching {
                hook.onReady()
            }.onFailure { e ->
                val duration = System.currentTimeMillis() - startTime
                logger.error("✗  Ready hook failed: {} ({} ms) - {}",
                    hook.id, duration, e.message)
                val initException = e as? LifecycleException ?: InitializerFailedException(
                    initializerName = hook.id,
                    message = e.message ?: "Unknown ready hook error",
                    cause = e
                )
                throw initException
            }

            val duration = System.currentTimeMillis() - startTime
            logger.debug(
                "Ready hook completed: {} ({} ms)",
                hook.id,
                duration
            )
        }

        val totalDuration = System.currentTimeMillis() - lifecycleStart
        logger.info(
            "Ready hook lifecycle completed: {} hook(s) in {} ms",
            hooks.size,
            totalDuration
        )
    }
}

internal val readyHookOrderComparator: Comparator<ReadyHook> =
    compareBy<ReadyHook> { it.order }
        .thenBy { it::class.qualifiedName ?: it::class.simpleName ?: "" }
