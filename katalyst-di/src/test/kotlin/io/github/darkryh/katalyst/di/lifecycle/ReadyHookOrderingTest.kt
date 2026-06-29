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
        ).sortedWith(readyHookOrderComparator)

        assertEquals(
            listOf("RuntimeOrderMinus5", "RuntimeTieA", "RuntimeTieB"),
            sorted.map { it.id }
        )
    }
}

private class RuntimeOrderMinus5 : ReadyHook {
    override val id: String = "RuntimeOrderMinus5"
    override val order: Int = -5
    override suspend fun onReady() = Unit
}

private class RuntimeTieA : ReadyHook {
    override val id: String = "RuntimeTieA"
    override val order: Int = 0
    override suspend fun onReady() = Unit
}

private class RuntimeTieB : ReadyHook {
    override val id: String = "RuntimeTieB"
    override val order: Int = 0
    override suspend fun onReady() = Unit
}
