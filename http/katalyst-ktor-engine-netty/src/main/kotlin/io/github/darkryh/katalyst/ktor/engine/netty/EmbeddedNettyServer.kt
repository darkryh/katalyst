@file:Suppress("UNCHECKED_CAST")

package io.github.darkryh.katalyst.ktor.engine.netty

import io.github.darkryh.katalyst.di.config.BootstrapArgs
import io.github.darkryh.katalyst.di.config.BootstrapArgsHolder
import io.github.darkryh.katalyst.di.config.KatalystServerEngine
import io.github.darkryh.katalyst.di.config.KatalystCommandLineConfig
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

/**
 * Object-style Netty server builder for Katalyst applications.
 */
object NettyServer : KatalystServerEngine {
    /**
     * Build an embedded Netty server using the CLI/profile args captured during bootstrap.
     * Falls back to empty args if no bootstrap args were recorded.
     */
    operator fun invoke(): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {
        val bootstrapArgs = BootstrapArgsHolder.current() ?: BootstrapArgs.EMPTY
        return buildEmbeddedServer(bootstrapArgs)
    }

    /**
     * Build an embedded Netty server.
     */
    override fun build(): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> = invoke()
}

private fun buildEmbeddedServer(
    bootstrapArgs: BootstrapArgs
): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {
    val config = KatalystCommandLineConfig.prepare(bootstrapArgs.ktorArgs, bootstrapArgs.forceCliConfig)

    return EmbeddedServer(config.rootConfig, Netty) {
        takeFrom(config.engineConfig)
        loadConfiguration(config.environment.config)
    } as EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>
}

internal fun NettyApplicationEngine.Configuration.loadConfiguration(config: ApplicationConfig) {
    val deploymentConfig = config.config("ktor.deployment")
    loadCommonConfiguration(deploymentConfig)
    deploymentConfig.propertyOrNull("runningLimit")?.getString()?.toInt()?.let {
        runningLimit = it
    }
    deploymentConfig.propertyOrNull("shareWorkGroup")?.getString()?.toBoolean()?.let {
        shareWorkGroup = it
    }
    deploymentConfig.propertyOrNull("responseWriteTimeoutSeconds")?.getString()?.toInt()?.let {
        responseWriteTimeoutSeconds = it
    }
    deploymentConfig.propertyOrNull("requestReadTimeoutSeconds")?.getString()?.toInt()?.let {
        requestReadTimeoutSeconds = it
    }
    deploymentConfig.propertyOrNull("tcpKeepAlive")?.getString()?.toBoolean()?.let {
        tcpKeepAlive = it
    }
    deploymentConfig.propertyOrNull("maxInitialLineLength")?.getString()?.toInt()?.let {
        maxInitialLineLength = it
    }
    deploymentConfig.propertyOrNull("maxHeaderSize")?.getString()?.toInt()?.let {
        maxHeaderSize = it
    }
    deploymentConfig.propertyOrNull("maxChunkSize")?.getString()?.toInt()?.let {
        maxChunkSize = it
    }
    deploymentConfig.propertyOrNull("enableHttp2")?.getString()?.toBoolean()?.let {
        enableHttp2 = it
    }
    deploymentConfig.propertyOrNull("enableH2c")?.getString()?.toBoolean()?.let {
        enableH2c = it
    }
}
