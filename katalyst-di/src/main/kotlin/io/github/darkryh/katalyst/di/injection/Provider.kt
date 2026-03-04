package io.github.darkryh.katalyst.di.injection

/**
 * Deferred dependency provider.
 *
 * Unlike direct constructor injection, the target dependency is resolved when [get] is invoked.
 */
fun interface Provider<T> {
    fun get(): T
}
