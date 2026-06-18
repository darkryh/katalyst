package io.github.darkryh.katalyst.di.feature

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.get
import io.github.darkryh.katalyst.core.di.getAll
import io.github.darkryh.katalyst.core.di.getOrNull
import kotlin.reflect.KClass

/**
 * Represents an optional Katalyst feature (scheduler, events, websockets, etc.)
 * that can be plugged into the bootstrap process when its module is on the classpath.
 *
 * Features contribute Katalyst-owned bean modules and use [KatalystBeanContext]
 * after bootstrap. Concrete DI engines, such as the Koin adapter, interpret
 * those definitions in adapter modules.
 */
interface KatalystFeature {
    /** Stable identifier mainly used for logging/debugging. */
    val id: String

    /**
     * Bean modules loaded during bootstrap before auto-discovery runs.
     */
    fun provideBeanModules(): List<KatalystBeanModule> = emptyList()

    /**
     * Optional hook executed after the bean container is fully initialized.
     */
    fun onReady(context: KatalystBeanContext) {}
}

/**
 * Katalyst-owned bean module contributed by a feature.
 */
class KatalystBeanModule(
    val definitions: List<KatalystBeanDefinition>,
)

class KatalystBeanDefinition(
    val type: KClass<*>,
    val qualifier: String?,
    val provider: KatalystBeanContext.() -> Any,
)

/**
 * Katalyst-owned bean module builder.
 */
fun katalystBeanModule(configure: KatalystBeanModuleBuilder.() -> Unit): KatalystBeanModule {
    val builder = KatalystBeanModuleBuilder()
    builder.configure()
    return KatalystBeanModule(builder.definitions)
}

class KatalystBeanModuleBuilder internal constructor() {
    @PublishedApi
    internal val definitions = mutableListOf<KatalystBeanDefinition>()

    inline fun <reified T : Any> single(
        qualifier: String? = null,
        noinline provider: KatalystBeanContext.() -> T,
    ) {
        definitions += KatalystBeanDefinition(T::class, qualifier) { provider() }
    }
}

interface KatalystBeanEngine {
    val id: String

    fun start(
        modules: List<KatalystBeanModule>,
        allowOverrides: Boolean = false,
    ): KatalystContainer

    fun loadModules(
        modules: List<KatalystBeanModule>,
        allowOverrides: Boolean = false,
    )

    fun registerInstance(
        instance: Any,
        primaryType: KClass<*>,
        secondaryTypes: List<KClass<*>> = emptyList(),
        qualifier: String? = null,
    )

    fun currentOrNull(): KatalystContainer?

    fun stop()
}

object KatalystBeanEngines {
    @Volatile
    private var activeEngine: KatalystBeanEngine? = null

    fun activate(engine: KatalystBeanEngine): KatalystBeanEngine {
        activeEngine = engine
        return engine
    }

    fun activeOrNull(): KatalystBeanEngine? = activeEngine

    fun clearActive() {
        activeEngine = null
    }
}

/**
 * Framework-owned bean context exposed to features after bootstrap.
 */
class KatalystBeanContext(
    val container: KatalystContainer,
) {
    inline fun <reified T : Any> get(): T = container.get()

    inline fun <reified T : Any> getOrNull(): T? = container.getOrNull()

    inline fun <reified T : Any> getAll(): List<T> = container.getAll()
}
