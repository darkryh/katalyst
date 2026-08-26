package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.di.config.KatalystDIOptions
import io.github.darkryh.katalyst.di.config.SchemaManagementOptions
import io.github.darkryh.katalyst.di.config.SchemaPolicy
import io.github.darkryh.katalyst.di.config.ServerConfiguration
import io.github.darkryh.katalyst.di.config.ServerDeploymentConfiguration
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.di.internal.KtorModuleRegistry
import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.ktor.KtorModule
import io.github.darkryh.katalyst.testing.core.config.FakeConfigProvider
import io.github.darkryh.katalyst.testing.core.events.EventProbe
import kotlin.reflect.KClass

/**
 * Active test environment with a fully bootstrapped Katalyst DI container.
 *
 * Provides access to the active Katalyst container plus the Ktor modules that
 * were discovered during component scanning so tests can install the exact same pipeline.
 */
class KatalystTestEnvironment internal constructor(
    val options: KatalystDIOptions,
    val container: KatalystContainer,
    val discoveredKtorModules: List<KtorModule>,
    private val shutdownHook: () -> Unit
) : AutoCloseable {

    override fun close() {
        shutdownHook.invoke()
    }

    /**
     * Convenience accessor for typed resolves from the environment's Katalyst container.
     */
    inline fun <reified T : Any> get(): T = container.get(T::class)
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
    private var schemaManagement: SchemaManagementOptions =
        SchemaManagementOptions(policy = SchemaPolicy.CREATE_MISSING)
    private val scanPackages = linkedSetOf<String>()
    private val extraFeatures = mutableListOf<KatalystFeature>()
    private val overrideModules = mutableListOf<KatalystBeanModule>()
    private val configOverrides = linkedMapOf<String, Any?>()
    private val eventProbes = mutableListOf<EventProbe<out DomainEvent>>()
    private var includeDefaultFeatures: Boolean = true
    private var defaultFeatures: KatalystTestFeaturesBuilder.() -> Unit = {}
    private var runPreStartInitializers: Boolean = true
    private var runRuntimeReadyInitializers: Boolean = true

    /**
     * Whether the backing DI container should accept overriding bindings. Enabled by default for tests.
     */
    var allowOverrides: Boolean = true

    /**
     * Server configuration forwarded to bootstrap. Defaults to test engine.
     */
    var serverConfiguration: ServerConfiguration = ServerConfiguration(
        engine = null,
        deployment = ServerDeploymentConfiguration.createDefault()
    )

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
     * Configure the schema policy applied after component discovery.
     *
     * Defaults to [SchemaPolicy.CREATE_MISSING], which is convenient for tests but also
     * *masks* schema problems: it creates tables itself, so a test cannot tell whether the
     * schema came from the code under test or from the policy. Any test asserting that
     * migrations built the schema must select [SchemaPolicy.NONE] or [SchemaPolicy.VALIDATE].
     */
    fun schema(
        policy: SchemaPolicy,
        failOnPendingStatements: Boolean = true,
        createMissingColumns: Boolean = true,
    ) = apply {
        schemaManagement = SchemaManagementOptions(
            policy = policy,
            failOnPendingStatements = failOnPendingStatements,
            createMissingColumns = createMissingColumns,
        )
    }

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
     * Configure the default test feature set without losing source compatibility.
     */
    fun features(configure: KatalystTestFeaturesBuilder.() -> Unit) = apply {
        includeDefaultFeatures = true
        defaultFeatures = configure
    }

    /**
     * Disable the default feature set.
     */
    fun disableDefaultFeatures() = apply {
        includeDefaultFeatures = false
    }

    fun disableScheduler() = apply {
        val previous = defaultFeatures
        defaultFeatures = {
            previous()
            disableScheduler()
        }
    }

    fun disableRuntimeReadyInitializers() = apply {
        runRuntimeReadyInitializers = false
    }

    fun runRuntimeReadyInitializers(enabled: Boolean) = apply {
        runRuntimeReadyInitializers = enabled
    }

    fun disablePreStartInitializers() = apply {
        runPreStartInitializers = false
    }

    fun runPreStartInitializers(enabled: Boolean) = apply {
        runPreStartInitializers = enabled
    }

    fun config(key: String, value: Any?) = apply {
        configOverrides[key] = value
    }

    fun config(entries: Map<String, Any?>) = apply {
        configOverrides += entries
    }

    fun configProvider(provider: ConfigProvider) = apply {
        overrideModules += katalystBeanModule {
            single<ConfigProvider> {
                provider
            }
        }
    }

    fun fakeConfig(entries: Map<String, Any?>) = apply {
        config(entries)
    }

    fun <T : DomainEvent> eventProbe(eventType: KClass<T>): EventProbe<T> {
        val probe = EventProbe(eventType)
        eventProbes += probe
        return probe
    }

    inline fun <reified T : DomainEvent> eventProbe(): EventProbe<T> =
        eventProbe(T::class)

    /**
     * Register override bean modules that are loaded after the core ones.
     *
     * The environment owns what it is given: closing it closes every `AutoCloseable` bean in the
     * container, these included, and it cannot tell an instance the provider constructed from one
     * it captured. A client shared across tests must therefore not be registered here — build it
     * per environment, or register a wrapper that is not `AutoCloseable`.
     */
    fun overrideBeanModules(vararg modules: KatalystBeanModule) = apply {
        overrideModules += modules
    }

    /**
     * Register override bean modules via collection.
     */
    fun overrideBeanModules(modules: Iterable<KatalystBeanModule>) = apply {
        overrideModules += modules
    }

    /**
     * Replace the server configuration.
     */
    fun serverConfiguration(config: ServerConfiguration) = apply {
        serverConfiguration = config
    }

    fun build(): KatalystTestEnvironment {
        // Always start with a clean container context for deterministic tests.
        runCatching { KatalystTestBootstrap.stopStandalone() }

        val effectiveFeatures = buildList {
            if (includeDefaultFeatures) {
                addAll(defaultTestFeatures(defaultFeatures))
            }
            addAll(extraFeatures)
        }

        val effectiveOverrideModules = buildList {
            addAll(overrideModules)
            if (configOverrides.isNotEmpty()) {
                add(katalystBeanModule {
                    single<ConfigProvider> {
                        FakeConfigProvider(configOverrides.toMap())
                    }
                })
            }
            eventProbes.forEachIndexed { index, probe ->
                add(katalystBeanModule {
                    single<EventHandler<*>>(qualifier = "katalyst-test-event-probe-$index") {
                        probe
                    }
                })
            }
        }

        val options = KatalystDIOptions(
            databaseConfig = databaseConfig,
            beanEngine = TestKatalystBeanEngine(),
            scanPackages = scanPackages.toTypedArray(),
            features = effectiveFeatures + TestOverrideFeature(effectiveOverrideModules),
            schemaManagement = schemaManagement,
        )

        val nativeContainer = KatalystTestBootstrap.bootstrapContainer(
            databaseConfig = options.databaseConfig,
            scanPackages = options.scanPackages,
            features = options.features,
            serverConfig = serverConfiguration,
            allowOverrides = allowOverrides,
            schemaManagement = options.schemaManagement,
            beanEngine = options.beanEngine,
        )
        if (runPreStartInitializers) {
            KatalystTestBootstrap.runPreStartInitializers(nativeContainer)
        }
        if (runRuntimeReadyInitializers) {
            KatalystTestBootstrap.runRuntimeReadyInitializers(nativeContainer)
        }

        val container = KatalystContainerProvider.current()
        val capturedKtorModules = captureKtorModules(container)

        val shutdownHook = {
            runCatching { container.getOrNull(DatabaseFactory::class) }.getOrNull()?.close()
            KatalystTestBootstrap.stopStandalone()
        }

        return KatalystTestEnvironment(
            options = options,
            container = container,
            discoveredKtorModules = capturedKtorModules,
            shutdownHook = shutdownHook
        )
    }

    private fun captureKtorModules(container: KatalystContainer): List<KtorModule> {
        val registryModules = KtorModuleRegistry.consume()
        val containerModules = runCatching { container.getAll(KtorModule::class) }
            .getOrElse { emptyList() }

        // Dedup across BOTH sources by identity. A scanned module is present in the registry
        // and in the container, and installing it twice would register its routes twice
        // (Ktor then fails on the duplicate path, or silently shadows it). Deduplicating only
        // within `containerModules`, as this did before, does not catch the overlap.
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<KtorModule, Boolean>())
        return (registryModules + containerModules)
            .filter { seen.add(it) }
            .sortedBy { it.order }
    }

}

private class TestOverrideFeature(
    private val modules: List<KatalystBeanModule>
) : KatalystFeature {
    override val id: String = "katalyst-testing-overrides"

    override fun provideBeanModules(): List<KatalystBeanModule> = modules
}

private object KatalystTestBootstrap {
    private val configurationClass: Class<*> by lazy {
        Class.forName("io.github.darkryh.katalyst.di.config.DIConfigurationKt")
    }

    fun bootstrapContainer(
        databaseConfig: DatabaseConfig,
        scanPackages: Array<String>,
        features: List<KatalystFeature>,
        serverConfig: ServerConfiguration,
        allowOverrides: Boolean,
        schemaManagement: SchemaManagementOptions,
        beanEngine: Any?,
    ): Any {
        val method = configurationClass.methods.single {
            it.name == "bootstrapKatalystContainer" && it.parameterTypes.size == 8
        }
        return method.invoke(
            null,
            databaseConfig,
            scanPackages,
            features,
            serverConfig,
            emptyList<Any>(),
            allowOverrides,
            schemaManagement,
            beanEngine,
        )
    }

    fun runPreStartInitializers(nativeContainer: Any) {
        invokeNativeContainerFunction("runPreStartInitializers", nativeContainer)
    }

    fun runRuntimeReadyInitializers(nativeContainer: Any) {
        invokeNativeContainerFunction("runRuntimeReadyInitializers", nativeContainer)
    }

    fun stopStandalone() {
        configurationClass.methods.single {
            it.name == "stopKatalystStandalone" && it.parameterTypes.isEmpty()
        }.invoke(null)
    }

    private fun invokeNativeContainerFunction(name: String, nativeContainer: Any) {
        configurationClass.methods.single {
            it.name == name && it.parameterTypes.size == 1
        }.invoke(null, nativeContainer)
    }
}
