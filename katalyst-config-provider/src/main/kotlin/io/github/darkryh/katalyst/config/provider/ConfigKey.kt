package io.github.darkryh.katalyst.config.provider

/**
 * Overrides the configuration key for a single bound property with an absolute key.
 *
 * When present on a constructor parameter, the [value] is used verbatim and the enclosing
 * class [ConfigPrefix] (if any) is ignored for that parameter.
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigKey(val value: String)
