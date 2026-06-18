package io.github.darkryh.katalyst.di.config

import io.github.darkryh.katalyst.config.spi.ConfigLoaderResolver
import io.ktor.server.application.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Shared command-line/profile preparation used by every Katalyst server engine. */
object KatalystCommandLineConfig {
    fun prepare(ktorArgs: Array<String>, force: Boolean): CommandLineConfig {
        val args = augmentWithProfile(ktorArgs)
        val config = CommandLineConfig(args)
        val hasModules = config.environment.config.propertyOrNull("ktor.application.modules") != null
        if (!force && !hasModules) return config

        return runCatching {
            val flattened = flatten(config.environment.config) { key ->
                !key.equals("ktor.application.modules", ignoreCase = true)
            }
            val originalRoot = config.rootConfig
            val environment = applicationEnvironment {
                log = originalRoot.environment.log
                classLoader = originalRoot.environment.classLoader
                this.config = MapApplicationConfig(*flattened.toTypedArray())
            }
            val root = serverConfig(environment) {
                rootPath = originalRoot.rootPath
                developmentMode = originalRoot.developmentMode
                watchPaths = readField<List<String>>(originalRoot, "watchPaths").orEmpty()
                parentCoroutineContext = readField<CoroutineContext>(originalRoot, "parentCoroutineContext")
                    ?: EmptyCoroutineContext
            }
            CommandLineConfig(root, config.engineConfig)
        }.getOrElse { config }
    }

    private fun augmentWithProfile(ktorArgs: Array<String>): Array<String> {
        if (ktorArgs.any { it.startsWith("-config") }) return ktorArgs
        val profiled = ConfigLoaderResolver.resolveProfiledPaths(baseName = "application")
        return if (profiled.isEmpty()) ktorArgs else profiled.map { "-config=$it" }.toTypedArray() + ktorArgs
    }

    private fun flatten(
        config: ApplicationConfig,
        includeKey: (String) -> Boolean,
    ): List<Pair<String, String>> = buildList {
        fun visit(prefix: String, value: Any) {
            when (value) {
                is Map<*, *> -> value.forEach { (keyPart, child) ->
                    if (keyPart == null || child == null) return@forEach
                    val key = if (prefix.isEmpty()) keyPart.toString() else "$prefix.$keyPart"
                    if (includeKey(key)) visit(key, child)
                }
                is Iterable<*> -> value.filterNotNull().forEach { child ->
                    if (includeKey(prefix)) add(prefix to child.toString())
                }
                else -> if (includeKey(prefix)) add(prefix to value.toString())
            }
        }
        visit("", config.toMap())
    }

    private inline fun <reified T> readField(target: Any, name: String): T? = runCatching {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.get(target) as? T
    }.getOrNull()
}
