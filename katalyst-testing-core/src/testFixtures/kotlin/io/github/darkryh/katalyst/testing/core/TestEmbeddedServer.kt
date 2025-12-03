package io.github.darkryh.katalyst.testing.core

import io.ktor.events.Events
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.application.serverConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Minimal EmbeddedServer stub for tests; does not start a real engine.
 */
object TestApplicationEnvironment : ApplicationEnvironment {
    override val classLoader: ClassLoader = Thread.currentThread().contextClassLoader
    override val log = KtorSimpleLogger("TestEngine")
    override val config = MapApplicationConfig()
    @Deprecated("Use Application.monitor", level = DeprecationLevel.WARNING)
    override val monitor: Events = Events()
}

class TestApplicationEngine(
    override val environment: ApplicationEnvironment,
    private val appProvider: () -> Application
) : ApplicationEngine {
    override fun start(wait: Boolean): ApplicationEngine = this
    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {}
    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = emptyList()
    val application: Application get() = appProvider()
    val coroutineContext: CoroutineContext = EmptyCoroutineContext
}

class TestEngineFactory : ApplicationEngineFactory<TestApplicationEngine, ApplicationEngine.Configuration> {
    override fun configuration(block: ApplicationEngine.Configuration.() -> Unit): ApplicationEngine.Configuration =
        ApplicationEngine.Configuration().apply(block)

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: ApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): TestApplicationEngine = TestApplicationEngine(environment, applicationProvider)
}

fun testEmbeddedServer(): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {
    val cfg = serverConfig(TestApplicationEnvironment)
    return EmbeddedServer(cfg, TestEngineFactory()) { }
}
