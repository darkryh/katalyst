@file:Suppress("UNCHECKED_CAST")

package io.github.darkryh.katalyst.ktor.engine.jetty

import io.github.darkryh.katalyst.di.config.KatalystCommandLineConfig
import io.github.darkryh.katalyst.di.config.BootstrapArgs
import io.github.darkryh.katalyst.di.config.BootstrapArgsHolder
import io.github.darkryh.katalyst.di.config.KatalystServerEngine
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import org.eclipse.jetty.util.thread.QueuedThreadPool
import kotlin.time.Duration.Companion.milliseconds

/**
 * Object-style Jetty server builder for Katalyst applications.
 */
object JettyServer : KatalystServerEngine {
    /**
     * Build an embedded Jetty server using the CLI/profile args captured during bootstrap.
     * Falls back to empty args if no bootstrap args were recorded.
     */
    operator fun invoke(): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {
        val bootstrapArgs = BootstrapArgsHolder.current() ?: BootstrapArgs.EMPTY
        return buildEmbeddedServer(bootstrapArgs)
    }

    /**
     * Build an embedded Jetty server.
     */
    override fun build(): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> = invoke()
}

private fun buildEmbeddedServer(
    bootstrapArgs: BootstrapArgs
): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {
    val config = KatalystCommandLineConfig.prepare(bootstrapArgs.ktorArgs, bootstrapArgs.forceCliConfig)

    return EmbeddedServer(config.rootConfig, Jetty) {
        takeFrom(config.engineConfig)
        loadConfiguration(config.environment.config)
    } as EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>
}

internal fun JettyApplicationEngineBase.Configuration.loadConfiguration(config: ApplicationConfig) {
    val deploymentConfig = config.config("ktor.deployment")
    loadCommonConfiguration(deploymentConfig)

    deploymentConfig.propertyOrNull("connectionIdleTimeoutMs")?.getString()?.toLong()?.let {
        idleTimeout = it.milliseconds
    }

    val maxThreads = deploymentConfig.propertyOrNull("maxThreads")?.getString()?.toIntOrNull()
    val minThreads = deploymentConfig.propertyOrNull("minThreads")?.getString()?.toIntOrNull()
    if (maxThreads != null || minThreads != null) {
        configureServer = {
            (threadPool as? QueuedThreadPool)?.let { pool ->
                maxThreads?.let { pool.maxThreads = it }
                minThreads?.let { pool.minThreads = it }
            }
        }
    }
}
