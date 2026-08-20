package io.github.darkryh.katalyst.ktor

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.ktor.extension.katalystContainer
import io.github.darkryh.katalyst.ktor.extension.ktInject
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class KatalystContainerExtensionsTest {
    @AfterTest
    fun tearDown() {
        KatalystContainerProvider.reset()
    }

    @Test
    fun `ktInject resolves through active Katalyst container`() {
        val container = TestKatalystContainer(
            mapOf(TestKatalystContainer.Key(SampleDependency::class) to SampleDependency("container"))
        )
        KatalystContainerProvider.set(container)

        testApplication {
            application {
                val dependency by ktInject<SampleDependency>()

                assertEquals("container", dependency.id)
                assertSame(container, katalystContainer())
            }
        }
    }

    private data class SampleDependency(val id: String)
}
