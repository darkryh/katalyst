@file:Suppress("UNCHECKED_CAST")

package com.ead.katalyst.com.ead.katalyst.ktor.engine.netty

import com.ead.katalyst.di.config.BootstrapArgs
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun Array<String>.embeddedServer(): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {
    val bootstrapArgs = BootstrapArgs.parse(this).also { it.applyProfileOverride() }
    val rawConfig = CommandLineConfig(bootstrapArgs.ktorArgs)
    val config = sanitizeCommandLineConfig(rawConfig, force = bootstrapArgs.forceCliConfig)

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

private fun sanitizeCommandLineConfig(
    config: CommandLineConfig,
    force: Boolean
): CommandLineConfig {
    val hasModules = config.environment.config.propertyOrNull("ktor.application.modules") != null
    if (!force && !hasModules) return config

    return runCatching {
        val flattened = flattenConfig(config.environment.config) { key -> !key.equals("ktor.application.modules", ignoreCase = true) }
        val sanitizedConfig = MapApplicationConfig(*flattened.toTypedArray())

        val originalRoot = config.rootConfig
        val watchPaths = readField<List<String>>(originalRoot, "watchPaths") ?: emptyList()
        val parentContext = readField<kotlin.coroutines.CoroutineContext>(originalRoot, "parentCoroutineContext")
            ?: kotlin.coroutines.EmptyCoroutineContext

        val env = applicationEnvironment {
            log = originalRoot.environment.log
            classLoader = originalRoot.environment.classLoader
            this.config = sanitizedConfig
        }

        val sanitizedRoot = serverConfig(env) {
            rootPath = originalRoot.rootPath
            developmentMode = originalRoot.developmentMode
            this.watchPaths = watchPaths
            parentCoroutineContext = parentContext
        }

        CommandLineConfig(sanitizedRoot, config.engineConfig)
    }.onFailure {
        // If sanitization fails for any reason, fall back to the original config
    }.getOrElse { config }
}

private fun flattenConfig(
    config: ApplicationConfig,
    includeKey: (String) -> Boolean
): List<Pair<String, String>> {
    val pairs = mutableListOf<Pair<String, String>>()

    fun recurse(prefix: String, value: Any) {
        when (value) {
            is Map<*, *> -> value.forEach { (k, v) ->
                if (k == null || v == null) return@forEach
                val key = if (prefix.isEmpty()) "$k" else "$prefix.$k"
                if (!includeKey(key)) return@forEach
                recurse(key, v)
            }
            is Iterable<*> -> value.filterNotNull().forEach { element ->
                if (includeKey(prefix)) {
                    pairs += prefix to element.toString()
                }
            }
            else -> if (includeKey(prefix)) {
                pairs += prefix to value.toString()
            }
        }
    }

    recurse("", config.toMap())
    return pairs
}

private inline fun <reified T> readField(target: Any, name: String): T? {
    return runCatching {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.get(target) as? T
    }.getOrNull()
}
