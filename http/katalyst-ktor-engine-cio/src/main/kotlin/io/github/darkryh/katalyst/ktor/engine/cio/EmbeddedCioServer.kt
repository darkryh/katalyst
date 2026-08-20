@file:Suppress("UNCHECKED_CAST")

package io.github.darkryh.katalyst.ktor.engine.cio

import io.github.darkryh.katalyst.di.config.KatalystCommandLineConfig
import io.github.darkryh.katalyst.di.config.BootstrapArgs
import io.github.darkryh.katalyst.di.config.BootstrapArgsHolder
import io.github.darkryh.katalyst.di.config.KatalystServerEngine
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.config.*
import io.ktor.server.engine.*

/**
 * Object-style CIO server builder for Katalyst applications.
 */
object CioServer : KatalystServerEngine {
    /**
     * Build an embedded CIO server using the CLI/profile args captured during bootstrap.
     * Falls back to empty args if no bootstrap args were recorded.
     */
    operator fun invoke(): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {
        val bootstrapArgs = BootstrapArgsHolder.current() ?: BootstrapArgs.EMPTY
        return buildEmbeddedServer(bootstrapArgs)
    }

    /**
     * Build an embedded CIO server.
     */
    override fun build(): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> = invoke()
}

private fun buildEmbeddedServer(
    bootstrapArgs: BootstrapArgs
): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {
    val config = KatalystCommandLineConfig.prepare(bootstrapArgs.ktorArgs, bootstrapArgs.forceCliConfig)

    return EmbeddedServer(config.rootConfig, CIO) {
        takeFrom(config.engineConfig)
        loadConfiguration(config.environment.config)
    } as EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>
}

internal fun CIOApplicationEngine.Configuration.loadConfiguration(config: ApplicationConfig) {
    val deploymentConfig = config.config("ktor.deployment")
    loadCommonConfiguration(deploymentConfig)
    deploymentConfig.propertyOrNull("connectionIdleTimeoutMs")?.getString()?.toLongOrNull()?.let {
        connectionIdleTimeoutSeconds = (it / 1000L).coerceAtLeast(1L).toInt()
    }
    deploymentConfig.propertyOrNull("reuseAddress")?.getString()?.toBooleanStrictOrNull()?.let {
        reuseAddress = it
    }
}
