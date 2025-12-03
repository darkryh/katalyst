package com.ead.boshi.smtp.dns

import com.ead.boshi.shared.exceptions.DnsException
import com.ead.boshi.shared.models.MxRecordEntity
import io.github.darkryh.katalyst.core.component.Component
import org.slf4j.LoggerFactory
import org.xbill.DNS.Lookup
import org.xbill.DNS.MXRecord
import org.xbill.DNS.Resolver
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.TextParseException
import org.xbill.DNS.Type
import java.time.Duration

/**
 * Service for resolving MX records from DNS
 * Handles DNS lookups and caching of MX records
 */
class MxRecordResolver : Component {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val DNS_TIMEOUT_MS = 5000L
        private const val DEFAULT_TTL_HOURS = 24
        private val DEFAULT_RESOLVERS = listOf("8.8.8.8", "1.1.1.1")
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

        val resolvers = buildResolvers()
        var lastError: Exception? = null

        for ((idx, resolver) in resolvers.withIndex()) {
            try {
                return resolveWithResolver(domain, resolver, idx, resolvers.size)
            } catch (e: Exception) {
                lastError = e
                logger.warn("DNS attempt ${idx + 1}/${resolvers.size} failed for $domain (${resolverAddress(resolver)}): ${e::class.simpleName}")
            }
        }

        logger.error("✗ DNS resolution failed for domain: $domain after ${resolvers.size} attempts")
        throw DnsException("Failed to resolve MX records for domain: $domain", lastError)
    }

    private fun buildResolvers(): List<Resolver> {
        // Try UDP first, then TCP for each host to handle networks that block one or the other.
        return DEFAULT_RESOLVERS.flatMap { host ->
            listOf(false, true).mapNotNull { useTcp ->
                runCatching {
                    SimpleResolver(host).apply {
                        tcp = useTcp
                        // dnsjava expects seconds + nanos
                        timeout = Duration.ofMillis(DNS_TIMEOUT_MS)
                    }
                }.getOrNull()
            }
        }
    }

    private fun resolveWithResolver(domain: String, resolver: Resolver, attemptNumber: Int, totalAttempts: Int): List<MxRecordEntity> {
        logger.debug("Attempt ${attemptNumber + 1}/$totalAttempts: Resolving MX records for $domain via ${resolverAddress(resolver)}")

        val lookup = Lookup(domain, Type.MX).apply {
            this.setResolver(resolver)
        }

        val result = runCatching { lookup.run() }.getOrElse { throwable ->
            if (throwable is TextParseException) throw DnsException("Invalid domain: $domain", throwable)
            throw throwable
        }

        val rcode = lookup.result
        val err = lookup.errorString
        if (rcode != Lookup.SUCCESSFUL) {
            logger.warn("DNS lookup for MX {} via {} failed: {} ({})", domain, resolverAddress(resolver), err, rcode)
        }

        val answers = result?.filterIsInstance<MXRecord>().orEmpty()
        if (answers.isEmpty()) {
            logger.warn("No MX records found for domain: $domain via ${resolverAddress(resolver)}")
            throw DnsException("No MX records found for domain: $domain")
        }

        val now = System.currentTimeMillis()
        val expiresAt = now + (DEFAULT_TTL_HOURS * 60 * 60 * 1000)

        val mxRecords = answers.map { mx ->
            MxRecordEntity(
                domain = domain,
                mxHostname = mx.target.toString(true),
                priority = mx.priority,
                resolvedAtMillis = now,
                expiresAtMillis = expiresAt,
                failedAttempts = 0
            )
        }.sortedBy { it.priority }

        logger.info("✓ Successfully resolved ${mxRecords.size} MX record(s) for domain: $domain via ${resolverAddress(resolver)}")
        return mxRecords
    }

    private fun resolverAddress(resolver: Resolver): String =
        runCatching { (resolver as? SimpleResolver)?.address?.hostString ?: "unknown" }.getOrElse { "unknown" }

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
