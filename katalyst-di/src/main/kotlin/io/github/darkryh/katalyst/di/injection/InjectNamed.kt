package io.github.darkryh.katalyst.di.injection

/**
 * Qualifies a constructor parameter dependency by name.
 *
 * This annotation is optional and only needed when multiple implementations of the same type exist.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class InjectNamed(val value: String)
