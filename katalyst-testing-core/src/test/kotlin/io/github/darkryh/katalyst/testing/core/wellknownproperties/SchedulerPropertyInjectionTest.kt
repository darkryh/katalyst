package io.github.darkryh.katalyst.testing.core.wellknownproperties

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.config.KatalystDIOptions
import io.github.darkryh.katalyst.di.config.ServerConfiguration
import io.github.darkryh.katalyst.di.config.ServerDeploymentConfiguration
import io.github.darkryh.katalyst.di.config.initializeKatalystStandalone
import io.github.darkryh.katalyst.di.config.stopKatalystStandalone
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.scheduler.SchedulerFeature
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.koin.core.context.stopKoin

/**
 * Well-known property injection for `SchedulerService` has to actually happen.
 *
 * The dependency analyzer records `var scheduler: SchedulerService` as a satisfied well-known
 * property dependency, so boot succeeds; if the registrar never assigns it, the application only
 * finds out when the property is first read and throws
 * `UninitializedPropertyAccessException`. This test boots the production engine and reads the
 * property.
 */
class SchedulerPropertyInjectionTest {

    @AfterTest
    fun tearDown() {
        stopKatalystStandalone()
        KatalystContainerProvider.reset()
        runCatching { stopKoin() }
    }

    @Test
    fun `a component declaring a SchedulerService property gets it injected`() {
        val container = initializeKatalystStandalone(
            options = KatalystDIOptions(
                databaseConfig = inMemoryDatabaseConfig(),
                beanEngine = KoinBeanEngine,
                scanPackages = arrayOf("io.github.darkryh.katalyst.testing.core.wellknownproperties"),
                features = listOf(SchedulerFeature),
            ),
            serverConfiguration = ServerConfiguration(
                engine = null,
                deployment = ServerDeploymentConfiguration.createDefault(),
            ),
            allowOverrides = true,
            activateRuntimeReadyInitializers = false,
        )

        val component = container.get(SchedulerPropertyComponent::class)

        val injected = component.schedulerOrNull()
        assertNotNull(
            injected,
            "the framework must assign the well-known SchedulerService property during registration"
        )
        assertSame(
            container.get(io.github.darkryh.katalyst.scheduler.service.SchedulerService::class),
            injected,
            "the injected scheduler must be the container's own SchedulerService"
        )
    }
}
