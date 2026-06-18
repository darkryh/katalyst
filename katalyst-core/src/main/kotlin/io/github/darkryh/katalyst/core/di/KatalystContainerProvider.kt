package io.github.darkryh.katalyst.core.di

/**
 * Process-wide holder for the active Katalyst container.
 *
 * This mirrors the current global Koin bootstrap model while giving Katalyst
 * APIs a framework-owned entry point. Tests and application shutdown should
 * call [reset] when the backing DI context is stopped.
 */
object KatalystContainerProvider {
    @Volatile
    private var activeContainer: KatalystContainer? = null

    fun set(container: KatalystContainer) {
        activeContainer = container
    }

    fun current(): KatalystContainer =
        currentOrNull()
            ?: error(
                "Katalyst container is not initialized. Install a DI adapter before resolving dependencies. " +
                    "For Koin, add `io.github.darkryh.katalyst:katalyst-koin-bean` to the application runtime."
            )

    fun currentOrNull(): KatalystContainer? = activeContainer

    fun reset() {
        activeContainer = null
    }
}
