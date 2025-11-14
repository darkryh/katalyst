package com.ead.katalyst.testing.core

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.di.config.KatalystDIOptions
import com.ead.katalyst.di.config.ServerConfiguration
import com.ead.katalyst.di.config.bootstrapKatalystDI
import com.ead.katalyst.di.config.stopKoinStandalone
import com.ead.katalyst.di.feature.KatalystFeature
import com.ead.katalyst.di.internal.KtorModuleRegistry
import com.ead.katalyst.ktor.KtorModule
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope

/**
 * Active test environment with a fully bootstrapped Katalyst DI container.
 *
 * Provides access to the underlying Koin instance plus the Ktor modules that were
 * discovered during component scanning so tests can install the exact same pipeline.
 */
class KatalystTestEnvironment internal constructor(
    val options: KatalystDIOptions,
    val koin: Koin,
    val discoveredKtorModules: List<KtorModule>,
    private val shutdownHook: () -> Unit
) : AutoCloseable {

    override fun close() {
        shutdownHook.invoke()
    }

    /**
     * Convenience accessor for typed resolves from the environment's Koin context.
     */
    inline fun <reified T : Any> get(
        qualifier: Qualifier? = null,
        noinline parameters: ParametersDefinition? = null
    ): T = koin.get(qualifier = qualifier, parameters = parameters)

    /**
     * Resolve a scoped instance from the root scope.
     */
    fun scope(id: String): Scope = koin.getScope(id)
}

/**
 * Builder DSL entry point for creating a new [KatalystTestEnvironment].
 */
fun katalystTestEnvironment(
    configure: KatalystTestEnvironmentBuilder.() -> Unit = {}
): KatalystTestEnvironment =
    KatalystTestEnvironmentBuilder().apply(configure).build()

/**
 * Builder responsible for configuring and bootstrapping a [KatalystTestEnvironment].
 */
class KatalystTestEnvironmentBuilder {
    private var databaseConfig: DatabaseConfig = inMemoryDatabaseConfig()
    private val scanPackages = linkedSetOf<String>()
    private val extraFeatures = mutableListOf<KatalystFeature>()
    private val overrideModules = mutableListOf<Module>()
    private var includeDefaultFeatures: Boolean = true

    /**
     * Whether Koin should accept overriding bindings. Enabled by default for tests.
     */
    var allowOverrides: Boolean = true

    /**
     * Server configuration forwarded to bootstrap. Defaults to test engine.
     */
    var serverConfiguration: ServerConfiguration = ServerConfiguration(engine = TestKatalystEngine)

    /**
     * Override the database configuration used for this environment.
     */
    fun database(config: DatabaseConfig) = apply {
        databaseConfig = config
    }

    /**
     * Convenience alias for [database].
     */
    fun databaseConfig(config: DatabaseConfig) = database(config)

    /**
     * Configure the packages that should be scanned for components.
     */
    fun scan(vararg packages: String) = apply {
        scanPackages += packages
    }

    /**
     * Configure scan packages via collection.
     */
    fun scan(packages: Iterable<String>) = apply {
        scanPackages += packages
    }

    /**
     * Add an optional feature to the environment.
     */
    fun feature(feature: KatalystFeature) = apply {
        extraFeatures += feature
    }

    /**
     * Add multiple features at once.
     */
    fun features(vararg features: KatalystFeature) = apply {
        extraFeatures += features
    }

    /**
     * Disable the default feature set.
     */
    fun disableDefaultFeatures() = apply {
        includeDefaultFeatures = false
    }

    /**
     * Register override modules that are loaded after the core ones.
     */
    fun overrideModules(vararg modules: Module) = apply {
        overrideModules += modules
    }

    /**
     * Register override modules via collection.
     */
    fun overrideModules(modules: Iterable<Module>) = apply {
        overrideModules += modules
    }

    /**
     * Replace the server configuration.
     */
    fun serverConfiguration(config: ServerConfiguration) = apply {
        serverConfiguration = config
    }

    fun build(): KatalystTestEnvironment {
        // Always start with a clean Koin context for deterministic tests.
        runCatching { stopKoinStandalone() }

        val effectiveFeatures = buildList {
            if (includeDefaultFeatures) {
                addAll(defaultTestFeatures())
            }
            addAll(extraFeatures)
        }

        val options = KatalystDIOptions(
            databaseConfig = databaseConfig,
            scanPackages = scanPackages.toTypedArray(),
            features = effectiveFeatures
        )

        val koin = bootstrapKatalystDI(
            databaseConfig = options.databaseConfig,
            scanPackages = options.scanPackages,
            features = options.features,
            serverConfig = serverConfiguration,
            additionalModules = overrideModules.toList(),
            allowOverrides = allowOverrides
        )

        val capturedKtorModules = captureKtorModules(koin)

        val shutdownHook = {
            runCatching { koin.get<DatabaseFactory>() }.getOrNull()?.close()
            stopKoinStandalone()
        }

        return KatalystTestEnvironment(
            options = options,
            koin = koin,
            discoveredKtorModules = capturedKtorModules,
            shutdownHook = shutdownHook
        )
    }

    private fun captureKtorModules(koin: Koin): List<KtorModule> {
        val registryModules = KtorModuleRegistry.consume()
        val koinModules = runCatching { koin.getAll<KtorModule>() }
            .getOrElse { emptyList() }
            .distinctBy { it::class }
        return (registryModules + koinModules)
            .sortedBy { it.order }
    }
}
