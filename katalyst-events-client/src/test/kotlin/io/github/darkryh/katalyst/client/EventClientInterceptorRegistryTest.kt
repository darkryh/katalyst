package io.github.darkryh.katalyst.client

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventClientInterceptorRegistryTest {

    private val registry = DefaultEventClientInterceptorRegistry()

    @AfterTest
    fun tearDown() {
        registry.clear()
        GlobalEventClientInterceptorRegistry.consumeAll()
    }

    @Test
    fun `default registry avoids duplicates`() {
        val interceptor = object : EventClientInterceptor {}

        registry.register(interceptor)
        registry.register(interceptor)

        assertEquals(1, registry.getAll().size)
        assertTrue(registry.getAll().contains(interceptor))
    }

    @Test
    fun `global registry consumes and clears`() {
        val interceptorA = object : EventClientInterceptor {}
        val interceptorB = object : EventClientInterceptor {}

        GlobalEventClientInterceptorRegistry.register(interceptorA)
        GlobalEventClientInterceptorRegistry.registerAll(listOf(interceptorB))

        val consumed = GlobalEventClientInterceptorRegistry.consumeAll()
        assertEquals(listOf(interceptorA, interceptorB), consumed)
        assertTrue(GlobalEventClientInterceptorRegistry.consumeAll().isEmpty())
    }
}
