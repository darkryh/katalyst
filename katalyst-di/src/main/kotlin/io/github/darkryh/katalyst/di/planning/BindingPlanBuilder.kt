package io.github.darkryh.katalyst.di.planning

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.di.analysis.DependencyAnalyzer
import io.github.darkryh.katalyst.di.discovery.DiscoverySnapshot
import kotlin.reflect.KClass

internal class BindingPlanBuilder(
    private val container: KatalystContainer,
    private val scanPackages: Array<String>
) {
    fun build(
        discovery: DiscoverySnapshot,
        additionalAvailableTypes: Set<KClass<*>> = emptySet()
    ): BindingPlan {
        val analyzer = DependencyAnalyzer(
            discoveredTypes = discovery.asValidationMap(),
            container = container,
            scanPackages = scanPackages,
            additionalAvailableTypes = additionalAvailableTypes
        )

        return BindingPlan(
            discovery = discovery,
            graph = analyzer.buildGraph()
        )
    }
}
