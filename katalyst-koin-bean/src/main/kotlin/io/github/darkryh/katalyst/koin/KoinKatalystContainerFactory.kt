package io.github.darkryh.katalyst.koin

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerFactory
import org.koin.core.Koin

/**
 * ServiceLoader factory that installs Koin as the active Katalyst container.
 */
class KoinKatalystContainerFactory : KatalystContainerFactory {
    override val id: String = "koin"

    override fun supports(nativeContainer: Any): Boolean =
        nativeContainer is Koin

    override fun create(nativeContainer: Any): KatalystContainer =
        KoinKatalystContainer(nativeContainer as Koin)
}
