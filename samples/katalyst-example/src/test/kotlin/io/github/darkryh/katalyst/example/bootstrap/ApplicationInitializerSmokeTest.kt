package io.github.darkryh.katalyst.example.bootstrap

import io.github.darkryh.katalyst.di.lifecycle.ApplicationInitializer
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.github.darkryh.katalyst.testing.ktor.katalystTestApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertTrue

class ApplicationInitializerSmokeTest {
    private val probe = SmokeProbe()

    @Test
    fun `bootstrap executes custom initializer with new no-arg lifecycle contract`() = katalystTestApplication(
        configureEnvironment = {
            database(inMemoryDatabaseConfig())
            scan("io.github.darkryh.katalyst.example")

            overrideModules(
                module {
                    single { this@ApplicationInitializerSmokeTest.probe }
                    single<ApplicationInitializer> { SmokeProbeInitializer(get()) }
                }
            )
        }
    ) { _ ->
        assertTrue(this@ApplicationInitializerSmokeTest.probe.executed)
    }
}

private class SmokeProbe {
    var executed: Boolean = false
}

private class SmokeProbeInitializer(
    private val probe: SmokeProbe
) : ApplicationInitializer {
    override val initializerId: String = "sample-smoke-probe-initializer"
    override val order: Int = 5

    override suspend fun onApplicationReady() {
        probe.executed = true
    }
}
