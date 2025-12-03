package io.github.darkryh.katalyst.testing.ktor

import io.github.darkryh.katalyst.di.config.KatalystDIOptions
import io.github.darkryh.katalyst.ktor.KtorModule
import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.ktor.client.request.get
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.koin.dsl.koinApplication

class KatalystKtorTestSupportTest {

    @Test
    fun `installKatalystDiscoveredModules installs modules in order`() = testApplication {
        val events = mutableListOf<String>()
        val moduleA = RecordingModule("A", order = 2, events = events)
        val moduleB = RecordingModule("B", order = 1, events = events)
        val environment = fakeEnvironment(listOf(moduleA, moduleB))

        application {
            installKatalystDiscoveredModules(environment)
        }

        client.get("/")
        assertEquals(listOf("B", "A"), events)
    }

    @Test
    fun `katalystTestApplication installs modules and closes environment`() {
        val events = mutableListOf<String>()
        var closed = false
        val environment = fakeEnvironment(
            modules = listOf(RecordingModule("auto", order = 0, events = events))
        ) {
            closed = true
        }

        katalystTestApplication(
            applicationConfig = { events += "config" },
            environmentFactory = { environment }
        ) {
            client.get("/")
            events += "test"
        }

        assertEquals(listOf("auto", "config", "test"), events)
        assertTrue(closed, "Environment should be closed after test completes")
    }

    @Test
    fun `katalystTestApplication skips auto install when disabled`() {
        val events = mutableListOf<String>()
        val environment = fakeEnvironment(listOf(RecordingModule("auto", order = 0, events = events)))

        katalystTestApplication(
            autoInstallDiscoveredModules = false,
            environmentFactory = { environment },
            applicationConfig = { events += "config" }
        ) {
            client.get("/")
            events += "test"
        }

        assertEquals(listOf("config", "test"), events)
    }

    @Test
    fun `katalystTestApplication closes environment when test body fails`() {
        var closed = false
        val environment = fakeEnvironment(emptyList()) {
            closed = true
        }

        assertFailsWith<IllegalStateException> {
            katalystTestApplication(
                environmentFactory = { environment }
            ) {
                client.get("/")
                throw IllegalStateException("boom")
            }
        }

        assertTrue(closed, "Environment close hook should run even on failure")
    }

    private fun fakeEnvironment(
        modules: List<KtorModule>,
        onClose: () -> Unit = {}
    ): KatalystTestEnvironment {
        val constructor = KatalystTestEnvironment::class.java.getDeclaredConstructor(
            KatalystDIOptions::class.java,
            org.koin.core.Koin::class.java,
            List::class.java,
            kotlin.jvm.functions.Function0::class.java
        )
        constructor.isAccessible = true

        val options = KatalystDIOptions(
            databaseConfig = inMemoryDatabaseConfig(),
            scanPackages = emptyArray(),
            features = emptyList()
        )

        val koin = koinApplication { }.koin

        return constructor.newInstance(
            options,
            koin,
            modules.sortedBy { it.order },
            { onClose() }
        )
    }

    private class RecordingModule(
        private val id: String,
        override val order: Int,
        private val events: MutableList<String>
    ) : KtorModule {
        override fun install(application: Application) {
            events += id
        }
    }
}
