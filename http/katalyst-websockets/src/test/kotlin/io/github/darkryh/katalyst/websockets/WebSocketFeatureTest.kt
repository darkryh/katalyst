package io.github.darkryh.katalyst.websockets

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.ktor.KtorModule
import io.github.darkryh.katalyst.ktor.websocket.WebSocketOptions
import io.github.darkryh.katalyst.ktor.websocket.WebSocketPluginModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

class WebSocketFeatureTest {
    @Test
    fun `feature modules register enable flag and plugin module`() {
        val container = evaluateFeatureModules(WebSocketFeature.provideBeanModules().flatMap { it.definitions })

        assertTrue(container.get(Boolean::class, "enableWebSockets"))
        val installedModules = container.getAll(KtorModule::class)
        assertTrue(installedModules.any { it is WebSocketPluginModule })
    }

    @Test
    fun `enable feature can register configured options`() {
        WebSocketFeature.configure(
            WebSocketOptions(
                pingPeriod = 3.seconds,
                timeout = 9.seconds,
                maxFrameSize = 2048L,
                masking = true,
            )
        )
        val container = evaluateFeatureModules(WebSocketFeature.provideBeanModules().flatMap { it.definitions })

        val options = container.get(WebSocketOptions::class)
        assertEquals(3.seconds, options.pingPeriod)
        assertEquals(9.seconds, options.timeout)
        assertEquals(2048L, options.maxFrameSize)
        assertTrue(options.masking)
    }

    private fun evaluateFeatureModules(
        definitions: List<io.github.darkryh.katalyst.di.feature.KatalystBeanDefinition>,
    ): TestContainer {
        val container = TestContainer()
        definitions.forEach { definition ->
            container.register(
                type = definition.type,
                qualifier = definition.qualifier,
                value = definition.provider(KatalystBeanContext(container)),
            )
        }
        return container
    }

    private class TestContainer : KatalystContainer {
        private val beans = linkedMapOf<Pair<KClass<*>, String?>, Any>()

        fun register(type: KClass<*>, qualifier: String?, value: Any) {
            beans[type to qualifier] = value
        }

        override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
            getOrNull(type, qualifier) ?: error("No bean registered for ${type.qualifiedName}")

        override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? =
            beans[type to qualifier]?.let(type::cast)

        override fun <T : Any> getAll(type: KClass<T>): List<T> =
            beans.filterKeys { it.first == type }.values.map(type::cast)

        override fun contains(type: KClass<*>, qualifier: String?): Boolean =
            beans.containsKey(type to qualifier)
    }
}
