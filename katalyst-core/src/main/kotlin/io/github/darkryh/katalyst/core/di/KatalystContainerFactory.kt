package io.github.darkryh.katalyst.core.di

import java.util.ServiceLoader

/**
 * Factory implemented by DI adapter modules.
 *
 * Katalyst core does not own Koin, Hilt, or any other container directly. A
 * runtime application must add exactly one adapter module that can wrap the
 * native DI container created by the bootstrap engine.
 */
interface KatalystContainerFactory {
    val id: String

    fun supports(nativeContainer: Any): Boolean

    fun create(nativeContainer: Any): KatalystContainer
}

object KatalystContainerFactories {
    fun create(nativeContainer: Any): KatalystContainer {
        val factories = ServiceLoader.load(KatalystContainerFactory::class.java)
            .toList()

        return factories.firstOrNull { it.supports(nativeContainer) }
            ?.create(nativeContainer)
            ?: throw IllegalStateException(
                "No Katalyst DI adapter is installed for ${nativeContainer::class.qualifiedName}. " +
                    "Add a DI adapter module to the application runtime. For Koin, add " +
                    "`io.github.darkryh.katalyst:katalyst-koin-bean`."
            )
    }
}
