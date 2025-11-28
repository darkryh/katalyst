package com.ead.boshi.smtp.dns

import com.ead.boshi.shared.exceptions.DnsException
import com.ead.boshi.shared.models.MxRecordEntity
import com.ead.katalyst.core.component.Component
import org.slf4j.LoggerFactory
import javax.naming.NamingException
import javax.naming.directory.InitialDirContext
import java.util.*

/**
 * Service for resolving MX records from DNS
 * Handles DNS lookups and caching of MX records
 */
class MxRecordResolver : Component {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val MX_RECORD_TYPE = "MX"
        private const val DNS_TIMEOUT_MS = 5000L
        private const val DEFAULT_TTL_HOURS = 24
    }

    /**
     * Resolve MX records for a domain
     * @param domain domain name to resolve
     * @return list of MX records sorted by priority
     * @throws DnsException if DNS lookup fails
     */
    fun resolveMxRecords(domain: String): List<MxRecordEntity> {
        if (domain.isBlank()) {
            throw DnsException("Domain cannot be empty")
        }

        logger.debug("Resolving MX records for domain: $domain")

        // Try DNS servers in order: system default, Google, Cloudflare
        val dnsServers = listOf(
            null,         // System default
            "8.8.8.8",    // Google Public DNS
            "1.1.1.1"     // Cloudflare DNS
        )

        var lastException: Exception? = null

        for ((index, dnsServer) in dnsServers.withIndex()) {
            try {
                return resolveMxRecordsWithServer(domain, dnsServer, index, dnsServers.size)
            } catch (e: Exception) {
                lastException = e
                logger.warn("DNS attempt ${index + 1}/${dnsServers.size} failed for $domain${if (dnsServer != null) " (server: $dnsServer)" else " (system default)"}: ${e::class.simpleName}")
                // Continue to next server
            }
        }

        // All servers failed
        logger.error("✗ DNS resolution failed for domain: $domain after ${dnsServers.size} attempts")
        throw DnsException("Failed to resolve MX records for domain: $domain", lastException)
    }

    private fun resolveMxRecordsWithServer(
        domain: String,
        dnsServer: String?,
        attemptNumber: Int,
        totalAttempts: Int
    ): List<MxRecordEntity> {
        val serverInfo = dnsServer?.let { " via $it" } ?: " (system default DNS)"
        logger.debug("Attempt ${attemptNumber + 1}/$totalAttempts: Resolving MX records for $domain$serverInfo")

        val env = Hashtable<String, String>()
        env["java.naming.factory.initial"] = "com.sun.jndi.dns.DnsContextFactory"

        // Only set provider URL if explicit DNS server specified
        if (dnsServer != null) {
            env["java.naming.provider.url"] = "dns://$dnsServer"
        }

        val ctx = InitialDirContext(env)

        return try {
            val attributes = ctx.getAttributes(domain, arrayOf(MX_RECORD_TYPE))
            val mxAttr = attributes.get(MX_RECORD_TYPE)

            if (mxAttr == null) {
                logger.warn("No MX records found for domain: $domain$serverInfo")
                throw DnsException("No MX records found for domain: $domain")
            }

            val mxRecords = mutableListOf<MxRecordEntity>()
            val now = System.currentTimeMillis()
            val expiresAt = now + (DEFAULT_TTL_HOURS * 60 * 60 * 1000)

            // Parse MX records from DNS response
            logger.debug("Parsing ${mxAttr.size()} MX record(s) for domain: $domain")
            for (i in 0 until mxAttr.size()) {
                val mxRecord = mxAttr.get(i) as? String ?: continue
                val parts = mxRecord.split("\\s+".toRegex())

                if (parts.size >= 2) {
                    val priority = parts[0].toIntOrNull() ?: continue
                    val hostname = parts[1].trimEnd('.')

                    logger.debug("MX record: priority=$priority, hostname=$hostname")
                    mxRecords.add(
                        MxRecordEntity(
                            domain = domain,
                            mxHostname = hostname,
                            priority = priority,
                            resolvedAtMillis = now,
                            expiresAtMillis = expiresAt,
                            failedAttempts = 0
                        )
                    )
                }
            }

            if (mxRecords.isEmpty()) {
                logger.warn("No valid MX records could be parsed for domain: $domain")
                throw DnsException("No valid MX records found for domain: $domain")
            }

            // Sort by priority (lower is better)
            val sorted = mxRecords.sortedBy { it.priority }
            logger.info("✓ Successfully resolved ${sorted.size} MX record(s) for domain: $domain$serverInfo")

            sorted
        } finally {
            ctx.close()
        }
    }

    /**
     * Resolve primary MX record (highest priority) for a domain
     * @param domain domain name
     * @return primary MX record or null if none found
     */
    fun resolvePrimaryMxRecord(domain: String): MxRecordEntity? {
        return try {
            resolveMxRecords(domain).firstOrNull()
        } catch (e: Exception) {
            logger.warn("Failed to resolve primary MX record for domain: $domain", e)
            null
        }
    }

    /**
     * Check if a domain has valid MX records
     * @param domain domain name
     * @return true if domain has MX records, false otherwise
     */
    fun hasMxRecords(domain: String): Boolean {
        return try {
            resolveMxRecords(domain).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Update failure tracking for MX record
     * @param mxRecord MX record entity
     * @param attemptFailed whether the attempt failed
     * @return updated MX record
     */
    fun trackMxRecordAttempt(mxRecord: MxRecordEntity, attemptFailed: Boolean): MxRecordEntity {
        return if (attemptFailed) {
            mxRecord.copy(
                failedAttempts = mxRecord.failedAttempts + 1,
                lastFailedAtMillis = System.currentTimeMillis()
            )
        } else {
            mxRecord.copy(
                failedAttempts = maxOf(0, mxRecord.failedAttempts - 1),
                lastFailedAtMillis = null
            )
        }
    }

    /**
     * Check if MX record has exceeded failure threshold
     * @param mxRecord MX record to check
     * @param maxAttempts maximum allowed failure attempts
     * @return true if failed attempts exceeds threshold
     */
    fun isBlacklisted(mxRecord: MxRecordEntity, maxAttempts: Int = 5): Boolean {
        return mxRecord.failedAttempts >= maxAttempts
    }
}
