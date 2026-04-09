package io.github.darkryh.katalyst.di.lifecycle

import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeReadyInitializerOrderingTest {

    @Test
    fun `sorts runtime-ready initializers by order then class name`() {
        val sorted = listOf(
            RuntimeTieB(),
            RuntimeOrderMinus5(),
            RuntimeTieA()
        ).sortedWith(runtimeReadyInitializerOrderComparator)

        assertEquals(
            listOf("RuntimeOrderMinus5", "RuntimeTieA", "RuntimeTieB"),
            sorted.map { it.initializerId }
        )
    }
}

private class RuntimeOrderMinus5 : ApplicationReadyInitializer {
    override val initializerId: String = "RuntimeOrderMinus5"
    override val order: Int = -5
    override suspend fun onRuntimeReady() = Unit
}

private class RuntimeTieA : ApplicationReadyInitializer {
    override val initializerId: String = "RuntimeTieA"
    override val order: Int = 0
    override suspend fun onRuntimeReady() = Unit
}

private class RuntimeTieB : ApplicationReadyInitializer {
    override val initializerId: String = "RuntimeTieB"
    override val order: Int = 0
    override suspend fun onRuntimeReady() = Unit
}
