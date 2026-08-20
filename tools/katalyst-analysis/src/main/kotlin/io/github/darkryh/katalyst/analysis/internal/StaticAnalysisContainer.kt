package io.github.darkryh.katalyst.analysis.internal

import io.github.darkryh.katalyst.core.di.KatalystContainer
import kotlin.reflect.KClass

/**
 * A [KatalystContainer] that never resolves anything.
 *
 * katalyst-di's [io.github.darkryh.katalyst.di.analysis.DependencyAnalyzer] only touches the
 * container to *probe* whether a type is already provided (e.g. by a feature). Everything else it
 * computes by reflecting over constructors — without instantiating anything. By handing it this
 * no-op container we reuse the exact runtime graph-building and validation logic statically: the
 * analyzer still recognises framework/feature contracts via `KnownPlatformTypes`, so well-known
 * types (EventBus, ConfigProvider, DatabaseTransactionManager, …) are correctly treated as
 * available and do not produce false "missing dependency" diagnostics.
 */
internal object StaticAnalysisContainer : KatalystContainer {
    override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
        throw UnsupportedOperationException(
            "StaticAnalysisContainer does not instantiate beans (requested ${type.qualifiedName})"
        )

    override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? = null

    override fun <T : Any> getAll(type: KClass<T>): List<T> = emptyList()

    override fun contains(type: KClass<*>, qualifier: String?): Boolean = false
}
