package io.github.darkryh.katalyst.example.bootstrap

import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.di.lifecycle.StartupHook
import io.github.darkryh.katalyst.example.sampleJwtTestConfig
import io.github.darkryh.katalyst.examplefixtures.SmokeProbe
import io.github.darkryh.katalyst.examplefixtures.SmokeProbeStartupHook
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.github.darkryh.katalyst.testing.ktor.katalystTestApplication
import kotlin.test.Test
import kotlin.test.assertTrue

class StartupHookSmokeTest {
    private val probe = SmokeProbe()

    @Test
    fun `bootstrap executes custom startup hook`() = katalystTestApplication(
        configureEnvironment = {
            database(inMemoryDatabaseConfig())
            config(sampleJwtTestConfig())
            scan("io.github.darkryh.katalyst.example")

            overrideBeanModules(
                katalystBeanModule {
                    single { this@StartupHookSmokeTest.probe }
                    single<StartupHook> { SmokeProbeStartupHook(get()) }
                }
            )
        }
    ) { _ ->
        assertTrue(this@StartupHookSmokeTest.probe.executed)
    }
}

