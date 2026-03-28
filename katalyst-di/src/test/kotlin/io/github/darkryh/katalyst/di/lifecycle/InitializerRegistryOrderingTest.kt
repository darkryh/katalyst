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
        ).sortedWith(initializerOrderComparator)

        assertEquals(
            listOf("Order-10", "Order0", "Order10"),
            sorted.map { it.initializerId }
        )
    }

    @Test
    fun `same order uses deterministic class-name tie-breaker`() {
        val sorted = listOf(
            TieBInitializer(),
            TieAInitializer()
        ).sortedWith(initializerOrderComparator)

        assertEquals(
            listOf("TieA", "TieB"),
            sorted.map { it.initializerId }
        )
    }
}

private class OrderMinus10Initializer : ApplicationInitializer {
    override val initializerId: String = "Order-10"
    override val order: Int = -10
    override suspend fun onApplicationReady() = Unit
}

private class Order0Initializer : ApplicationInitializer {
    override val initializerId: String = "Order0"
    override val order: Int = 0
    override suspend fun onApplicationReady() = Unit
}

private class Order10Initializer : ApplicationInitializer {
    override val initializerId: String = "Order10"
    override val order: Int = 10
    override suspend fun onApplicationReady() = Unit
}

private class TieAInitializer : ApplicationInitializer {
    override val initializerId: String = "TieA"
    override val order: Int = 0
    override suspend fun onApplicationReady() = Unit
}

private class TieBInitializer : ApplicationInitializer {
    override val initializerId: String = "TieB"
    override val order: Int = 0
    override suspend fun onApplicationReady() = Unit
}
