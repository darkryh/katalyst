@file:Suppress("unused")

package com.ead.boshi.app.config

import com.ead.katalyst.config.provider.ServiceConfigLoader
import com.ead.katalyst.config.provider.ConfigLoaders
import com.ead.katalyst.core.config.ConfigProvider


data class DnsConfig(
    val dnsServers: List<String>,
    val mxCacheHours: Int,
    val mxLookupTimeoutSeconds: Int
)


/**
 * Loads DNS configuration from YAML files
 */
object DnsConfigLoader : ServiceConfigLoader<DnsConfig> {
    override fun loadConfig(provider: ConfigProvider): DnsConfig {
        val dnsServersStr = ConfigLoaders.loadOptionalString(provider, "dns.servers","8.8.8.8,8.8.4.4")
        val dnsServers = dnsServersStr.split(",").map { it.trim() }
        val mxCacheHours = ConfigLoaders.loadOptionalInt(provider, "dns.mxCacheHours",24)
        val mxLookupTimeout = ConfigLoaders.loadOptionalInt(provider, "dns.mxLookupTimeoutSeconds",10)

        return DnsConfig(
            dnsServers = dnsServers,
            mxCacheHours = mxCacheHours,
            mxLookupTimeoutSeconds = mxLookupTimeout
        )
    }

    override fun validate(config: DnsConfig) {
        require(config.dnsServers.isNotEmpty()) { "At least one DNS server is required" }
        require(config.mxCacheHours > 0) { "mxCacheHours must be > 0" }
        require(config.mxLookupTimeoutSeconds > 0) { "mxLookupTimeoutSeconds must be > 0" }
    }
}