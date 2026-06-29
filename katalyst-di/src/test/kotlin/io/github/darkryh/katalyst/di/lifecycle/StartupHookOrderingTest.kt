package io.github.darkryh.katalyst.di.lifecycle

import kotlin.test.Test
import kotlin.test.assertEquals

class InitializerRegistryOrderingTest {

    @Test
    fun `sorts by order ascending`() {
        val sorted = listOf(
            Order10Initializer(),
            OrderMinus10Initializer(),
            Order0Initializer()
        ).sortedWith(startupHookOrderComparator)

        assertEquals(
            listOf("Order-10", "Order0", "Order10"),
            sorted.map { it.id }
        )
    }

    @Test
    fun `same order uses deterministic class-name tie-breaker`() {
        val sorted = listOf(
            TieBInitializer(),
            TieAInitializer()
        ).sortedWith(startupHookOrderComparator)

        assertEquals(
            listOf("TieA", "TieB"),
            sorted.map { it.id }
        )
    }
}

private class OrderMinus10Initializer : StartupHook {
    override val id: String = "Order-10"
    override val order: Int = -10
    override suspend fun onStartup() = Unit
}

private class Order0Initializer : StartupHook {
    override val id: String = "Order0"
    override val order: Int = 0
    override suspend fun onStartup() = Unit
}

private class Order10Initializer : StartupHook {
    override val id: String = "Order10"
    override val order: Int = 10
    override suspend fun onStartup() = Unit
}

private class TieAInitializer : StartupHook {
    override val id: String = "TieA"
    override val order: Int = 0
    override suspend fun onStartup() = Unit
}

private class TieBInitializer : StartupHook {
    override val id: String = "TieB"
    override val order: Int = 0
    override suspend fun onStartup() = Unit
}
