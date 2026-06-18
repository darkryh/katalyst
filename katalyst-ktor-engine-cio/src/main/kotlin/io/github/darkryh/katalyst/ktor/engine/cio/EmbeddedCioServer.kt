@file:Suppress("UNCHECKED_CAST")

package io.github.darkryh.katalyst.ktor.engine.cio

import io.github.darkryh.katalyst.config.spi.ConfigLoaderResolver
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
    val argsWithProfileConfig = augmentArgsWithProfileConfig(bootstrapArgs)
    val rawConfig = CommandLineConfig(argsWithProfileConfig)
    val config = sanitizeCommandLineConfig(rawConfig, force = bootstrapArgs.forceCliConfig)

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

private fun sanitizeCommandLineConfig(
    config: CommandLineConfig,
    force: Boolean
): CommandLineConfig {
    val hasModules = config.environment.config.propertyOrNull("ktor.application.modules") != null
    if (!force && !hasModules) return config

    return runCatching {
        val flattened = flattenConfig(config.environment.config) { key ->
            !key.equals("ktor.application.modules", ignoreCase = true)
        }
        val sanitizedConfig = MapApplicationConfig(*flattened.toTypedArray())

        val originalRoot = config.rootConfig
        val watchPaths = readField<List<String>>(originalRoot, "watchPaths") ?: emptyList()
        val parentContext = readField<kotlin.coroutines.CoroutineContext>(
            originalRoot,
            "parentCoroutineContext",
        ) ?: kotlin.coroutines.EmptyCoroutineContext

        val environment = applicationEnvironment {
            log = originalRoot.environment.log
            classLoader = originalRoot.environment.classLoader
            this.config = sanitizedConfig
        }

        val sanitizedRoot = serverConfig(environment) {
            rootPath = originalRoot.rootPath
            developmentMode = originalRoot.developmentMode
            this.watchPaths = watchPaths
            parentCoroutineContext = parentContext
        }

        CommandLineConfig(sanitizedRoot, config.engineConfig)
    }.getOrElse { config }
}

private fun augmentArgsWithProfileConfig(bootstrapArgs: BootstrapArgs): Array<String> {
    if (bootstrapArgs.ktorArgs.any { it.startsWith("-config") }) return bootstrapArgs.ktorArgs

    val profiled = ConfigLoaderResolver.resolveProfiledPaths(baseName = "application")
    if (profiled.isEmpty()) return bootstrapArgs.ktorArgs

    val profileArgs = profiled.map { "-config=$it" }.toTypedArray()
    return profileArgs + bootstrapArgs.ktorArgs
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

private inline fun <reified T> readField(target: Any, name: String): T? =
    runCatching {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.get(target) as? T
    }.getOrNull()
