package io.github.darkryh.katalyst.config.provider.binderfixtures

import io.github.darkryh.katalyst.config.provider.ConfigBinding
import io.github.darkryh.katalyst.config.provider.ConfigPrefix
import io.github.darkryh.katalyst.config.provider.booleanOrNull
import io.github.darkryh.katalyst.core.config.ConfigProvider

/**
 * Top-level fixtures in a dedicated package so [ConfigBinder.discoverConfigTypes] /
 * [ConfigBinder.bindAll] can find them by classpath scanning. Kept all-optional so
 * binding succeeds with any provider.
 */
@ConfigPrefix("alpha")
data class AlphaConfig(
    val name: String = "default-name",
    val size: Int = 10,
)

class BetaBinding(provider: ConfigProvider) : ConfigBinding {
    val flag: Boolean = provider.booleanOrNull("beta.flag") ?: false
}
