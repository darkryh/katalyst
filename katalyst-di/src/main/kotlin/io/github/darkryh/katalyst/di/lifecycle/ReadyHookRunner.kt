package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.di.internal.distinctByIdentity

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
        val registryHooks = ReadyHookRegistry.getAll()
        // A container-side failure here reduces the hook set to whatever the registry happens to
        // hold. That is exactly how #31 killed every scheduled job: the scheduler reaches this
        // runner through the container, so a swallowed failure takes the whole hook set with it and
        // the application starts looking healthy. WARN, always - there is no routine reason for
        // enumerating beans of a type to fail.
        val containerHooks = runCatching {
            container.getAll<ReadyHook>()
        }.onFailure { error ->
            logger.warn(
                "Could not enumerate {} beans from the container; only the {} hook(s) already held " +
                    "by ReadyHookRegistry will run and every container-only ready hook is skipped. " +
                    "Reason: {}: {}",
                ReadyHook::class.simpleName,
                registryHooks.size,
                error::class.simpleName,
                error.message,
                error,
            )
        }.getOrElse { emptyList() }
        // Dedup by identity, not by runtime class: the same hook instance can be
        // discovered through both the registry and the container, but two distinct
        // instances that happen to share a class are both legitimate and must both run.
        val hooks = (registryHooks + containerHooks)
            .distinctByIdentity()
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
