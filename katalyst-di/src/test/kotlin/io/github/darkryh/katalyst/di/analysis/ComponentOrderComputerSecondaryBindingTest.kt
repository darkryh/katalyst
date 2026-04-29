package io.github.darkryh.katalyst.di.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComponentOrderComputerSecondaryBindingTest {
    @Test
    fun `orders concrete provider before consumer of secondary interface`() {
        val graph = DependencyGraph(
            nodes = mapOf(
                SecondaryConsumer::class to ComponentNode(
                    type = SecondaryConsumer::class,
                    dependencies = listOf(
                        Dependency(
                            type = SecondaryContract::class,
                            parameterName = "contract",
                            isResolvable = true,
                        )
                    )
                ),
                SecondaryProvider::class to ComponentNode(
                    type = SecondaryProvider::class,
                    secondaryTypes = listOf(SecondaryContract::class),
                )
            ),
            edges = mapOf(
                SecondaryConsumer::class to setOf(SecondaryContract::class),
                SecondaryProvider::class to emptySet(),
            ),
            secondaryTypeBindings = mapOf(
                SecondaryContract::class to setOf(SecondaryProvider::class)
            )
        )

        val order = ComponentOrderComputer(graph).computeOrder()

        assertEquals(
            listOf(SecondaryProvider::class, SecondaryConsumer::class),
            order,
        )
    }

    @Test
    fun `detects cycles through secondary interface dependencies`() {
        val graph = DependencyGraph(
            nodes = mapOf(
                SecondaryCycleA::class to ComponentNode(
                    type = SecondaryCycleA::class,
                    dependencies = listOf(
                        Dependency(
                            type = SecondaryCycleContractB::class,
                            parameterName = "b",
                            isResolvable = true,
                        )
                    ),
                    secondaryTypes = listOf(SecondaryCycleContractA::class),
                ),
                SecondaryCycleB::class to ComponentNode(
                    type = SecondaryCycleB::class,
                    dependencies = listOf(
                        Dependency(
                            type = SecondaryCycleContractA::class,
                            parameterName = "a",
                            isResolvable = true,
                        )
                    ),
                    secondaryTypes = listOf(SecondaryCycleContractB::class),
                )
            ),
            edges = mapOf(
                SecondaryCycleA::class to setOf(SecondaryCycleContractB::class),
                SecondaryCycleB::class to setOf(SecondaryCycleContractA::class),
            ),
            secondaryTypeBindings = mapOf(
                SecondaryCycleContractA::class to setOf(SecondaryCycleA::class),
                SecondaryCycleContractB::class to setOf(SecondaryCycleB::class),
            )
        )

        assertFailsWith<IllegalStateException> {
            ComponentOrderComputer(graph).computeOrder()
        }
    }
}

private interface SecondaryContract
private class SecondaryProvider : SecondaryContract
private class SecondaryConsumer(val contract: SecondaryContract)

private interface SecondaryCycleContractA
private interface SecondaryCycleContractB
private class SecondaryCycleA(val b: SecondaryCycleContractB) : SecondaryCycleContractA
private class SecondaryCycleB(val a: SecondaryCycleContractA) : SecondaryCycleContractB
