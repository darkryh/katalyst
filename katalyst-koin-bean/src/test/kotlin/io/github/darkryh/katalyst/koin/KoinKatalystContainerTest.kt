package io.github.darkryh.katalyst.koin

import io.github.darkryh.katalyst.core.di.KatalystContainerFactories
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.get
import io.github.darkryh.katalyst.core.di.getOrNull
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class KoinKatalystContainerTest {
    @AfterTest
    fun tearDown() {
        KatalystContainerProvider.reset()
        runCatching { stopKoin() }
    }

    @Test
    fun `container resolves singletons and optional values`() {
        val koin = startKoin {
            modules(
                module {
                    single { SampleDependency("primary") }
                }
            )
        }.koin

        val container = KoinKatalystContainer(koin)

        assertEquals("primary", container.get<SampleDependency>().id)
        assertNotNull(container.getOrNull<SampleDependency>())
        assertNull(container.getOrNull<MissingDependency>())
        assertTrue(container.contains(SampleDependency::class))
        assertFalse(container.contains(MissingDependency::class))
    }

    @Test
    fun `service loader creates koin container`() {
        val koin = startKoin {
            modules(module { single { SampleDependency("provider") } })
        }.koin

        val container = KatalystContainerFactories.create(koin)
        KatalystContainerProvider.set(container)

        assertEquals("provider", KatalystContainerProvider.current().get<SampleDependency>().id)
    }

    private data class SampleDependency(val id: String)

    private class MissingDependency
}
