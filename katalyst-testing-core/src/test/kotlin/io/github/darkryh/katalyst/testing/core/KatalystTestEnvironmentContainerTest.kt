package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import kotlin.test.Test
import kotlin.test.assertEquals

class KatalystTestEnvironmentContainerTest {
    @Test
    fun `environment exposes Katalyst container facade`() {
        katalystTestEnvironment {
            disableDefaultFeatures()
            disablePreStartInitializers()
            disableRuntimeReadyInitializers()
            overrideBeanModules(
                katalystBeanModule {
                    single { SampleDependency("test-container") }
                }
            )
        }.use { environment ->
            assertEquals("test-container", environment.container.get(SampleDependency::class).id)
            assertEquals("test-container", environment.get<SampleDependency>().id)
        }
    }

    private data class SampleDependency(val id: String)
}
