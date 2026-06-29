package io.github.darkryh.katalyst.example.bootstrap

import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.di.lifecycle.StartupHook
import io.github.darkryh.katalyst.example.sampleJwtTestConfig
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

private class SmokeProbe {
    var executed: Boolean = false
}

private class SmokeProbeStartupHook(
    private val probe: SmokeProbe
) : StartupHook {
    override val id: String = "sample-smoke-probe-startup-hook"
    override val order: Int = 5

    override suspend fun onStartup() {
        probe.executed = true
    }
}
