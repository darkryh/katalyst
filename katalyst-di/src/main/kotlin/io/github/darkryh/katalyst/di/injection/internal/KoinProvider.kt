package io.github.darkryh.katalyst.di.injection.internal

import io.github.darkryh.katalyst.di.injection.Provider

internal class KoinProvider<T>(
    private val resolver: () -> T
) : Provider<T> {
    override fun get(): T = resolver()
}
