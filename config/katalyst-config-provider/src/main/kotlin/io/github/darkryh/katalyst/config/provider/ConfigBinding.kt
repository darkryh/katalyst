package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.core.config.ConfigProvider

/**
 * Marker interface for the code escape hatch of the configuration binding system.
 *
 * Unlike annotation-driven binding (see [ConfigPrefix]/[ConfigKey]), a `ConfigBinding`
 * implementor reads configuration imperatively. This is useful when keys map to values
 * through custom logic (parsing, derived defaults, cross-key validation) that cannot be
 * expressed declaratively.
 *
 * **Contract:** implementors MUST declare a primary constructor that takes a single
 * [ConfigProvider] parameter. The framework discovers implementors during component
 * scanning, instantiates each one with the active [ConfigProvider], and registers the
 * instance by its concrete type so components can receive it via constructor injection.
 *
 * ```kotlin
 * class SmtpConfig(provider: ConfigProvider) : ConfigBinding {
 *     val host: String = provider.requiredString("smtp.host")
 *     val port: Int = provider.intOrNull("smtp.port") ?: 25
 * }
 * ```
 */
interface ConfigBinding
