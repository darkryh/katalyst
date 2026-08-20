package io.github.darkryh.katalyst.config.provider

/**
 * Marks a configuration class for automatic binding under a shared key prefix.
 *
 * Each primary-constructor property of the annotated class binds to the key
 * `value + "." + kebab-case(propertyName)`. For example, a class annotated with
 * `@ConfigPrefix("mail")` and a property `webhookSecret` binds to `mail.webhook-secret`.
 *
 * Use [ConfigKey] on an individual constructor parameter to override the derived key
 * with an absolute one.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigPrefix(val value: String)
