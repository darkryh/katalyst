package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.di.feature.eventSystemFeature
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.di.lifecycle.ApplicationInitializer
import io.github.darkryh.katalyst.di.lifecycle.ApplicationReadyInitializer
import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.bus.EventBus
import io.github.darkryh.katalyst.testing.core.events.EventProbe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

class KatalystTestEnvironmentBuilderTest {

    @Test
    fun `features DSL disables selected default features`() {
        val features = defaultTestFeatures {
            disableScheduler()
            disableMigrations()
        }

        assertFalse(features.any { it.id == "scheduler" })
        assertFalse(features.any { it.id == "migrations" })
    }

    @Test
    fun `config overrides register fake config provider`() {
        katalystTestEnvironment {
            disableDefaultFeatures()
            disableRuntimeReadyInitializers()
            config(
                mapOf(
                    "app.name" to "test-app",
                    "app.port" to 9001,
                    "app.enabled" to true,
                )
            )
        }.use { env ->
            val provider = env.get<ConfigProvider>()

            assertEquals("test-app", provider.getString("app.name", "missing"))
            assertEquals(9001, provider.getInt("app.port", 0))
            assertEquals(true, provider.getBoolean("app.enabled", false))
        }
    }

    @Test
    fun `runtime-ready initializers can be disabled`() {
        val counter = Counter()

        katalystTestEnvironment {
            disableDefaultFeatures()
            disableRuntimeReadyInitializers()
            overrideBeanModules(
                katalystBeanModule {
                    single<ApplicationReadyInitializer> {
                        CountingRuntimeReadyInitializer(counter)
                    }
                }
            )
        }.use {
            assertEquals(0, counter.value)
        }
    }

    @Test
    fun `pre-start initializers can be disabled`() {
        val counter = Counter()

        katalystTestEnvironment {
            disableDefaultFeatures()
            disablePreStartInitializers()
            disableRuntimeReadyInitializers()
            overrideBeanModules(
                katalystBeanModule {
                    single<ApplicationInitializer> {
                        CountingApplicationInitializer(counter)
                    }
                }
            )
        }.use {
            assertEquals(0, counter.value)
        }
    }

    @Test
    fun `event probe captures published events`() = runTest {
        lateinit var probe: EventProbe<TestPublishedEvent>

        katalystTestEnvironment {
            disableDefaultFeatures()
            features(eventSystemFeature())
            disableRuntimeReadyInitializers()
            probe = eventProbe()
        }.use { env ->
            env.get<EventBus>().publish(TestPublishedEvent("created"))
        }

        probe.assertPublished()
        assertEquals("created", probe.last?.name)
    }

    private class Counter {
        var value: Int = 0
    }

    private class CountingRuntimeReadyInitializer(
        private val counter: Counter
    ) : ApplicationReadyInitializer {
        override suspend fun onRuntimeReady() {
            counter.value++
        }
    }

    private class CountingApplicationInitializer(
        private val counter: Counter
    ) : ApplicationInitializer {
        override suspend fun onApplicationReady() {
            counter.value++
        }
    }

    private data class TestPublishedEvent(
        val name: String
    ) : DomainEvent
}
