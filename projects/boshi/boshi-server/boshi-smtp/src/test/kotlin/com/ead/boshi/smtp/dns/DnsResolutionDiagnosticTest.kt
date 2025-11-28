package com.ead.boshi.smtp.dns

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import javax.naming.directory.InitialDirContext
import java.util.*

/**
 * Diagnostic test for DNS resolution issues
 * Tests both system DNS and JNDI DNS to compare behavior
 */
class DnsResolutionDiagnosticTest {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Test
    fun testJndiDnsResolution() {
        logger.info("=== JNDI DNS Resolution Test ===")

        // List of domains to test
        val testDomains = listOf(
            "gmail.com",
            "outlook.com",
            "yahoo.com",
            "yopmail.com",
            "google.com",
            "example.com"
        )

        testDomains.forEach { domain ->
            testDomainResolution(domain)
        }
    }

    private fun testDomainResolution(domain: String) {
        logger.info("Testing domain: $domain")

        try {
            val env = Hashtable<String, String>()
            env["java.naming.factory.initial"] = "com.sun.jndi.dns.DnsContextFactory"
            // Try adding DNS server configuration
            env["java.naming.provider.url"] = "dns://" // Use system default

            val ctx = InitialDirContext(env)
            val attributes = ctx.getAttributes(domain, arrayOf("MX"))
            val mxAttr = attributes.get("MX")

            if (mxAttr != null) {
                logger.info("✓ Domain $domain has ${mxAttr.size()} MX records:")
                for (i in 0 until mxAttr.size()) {
                    logger.info("  - ${mxAttr.get(i)}")
                }
            } else {
                logger.warn("✗ Domain $domain has no MX records")
            }

            ctx.close()
        } catch (e: Exception) {
            logger.error("✗ Failed to resolve $domain: ${e.javaClass.simpleName}: ${e.message}", e)

            // Log additional diagnostic info
            logger.info("System DNS servers:")
            try {
                val dnsServers = java.net.InetAddress.getAllByName(domain)
                logger.info("System can resolve $domain to: ${dnsServers.joinToString { it.hostAddress }}")
            } catch (e2: Exception) {
                logger.error("System cannot resolve $domain: ${e2.message}")
            }
        }
    }

    @Test
    fun testWithExplicitDnsServers() {
        logger.info("=== JNDI DNS with Explicit Servers ===")

        val testDomains = listOf("yopmail.com", "gmail.com")
        val dnsServers = listOf(
            "8.8.8.8",      // Google Public DNS
            "1.1.1.1",      // Cloudflare DNS
            "208.67.222.123" // OpenDNS
        )

        testDomains.forEach { domain ->
            logger.info("\nTesting $domain with explicit DNS servers:")

            dnsServers.forEach { dnsServer ->
                try {
                    val env = Hashtable<String, String>()
                    env["java.naming.factory.initial"] = "com.sun.jndi.dns.DnsContextFactory"
                    env["java.naming.provider.url"] = "dns://$dnsServer"

                    val ctx = InitialDirContext(env)
                    val attributes = ctx.getAttributes(domain, arrayOf("MX"))
                    val mxAttr = attributes.get("MX")

                    if (mxAttr != null) {
                        logger.info("✓ Via $dnsServer: $domain has ${mxAttr.size()} MX records")
                    } else {
                        logger.warn("✗ Via $dnsServer: $domain has no MX records")
                    }

                    ctx.close()
                } catch (e: Exception) {
                    logger.error("✗ Via $dnsServer: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    @Test
    fun testJavaNetDns() {
        logger.info("=== Java InetAddress DNS Test ===")

        val testDomains = listOf(
            "gmail.com",
            "outlook.com",
            "yopmail.com"
        )

        testDomains.forEach { domain ->
            try {
                logger.info("Resolving $domain via InetAddress:")
                val addresses = java.net.InetAddress.getAllByName(domain)
                addresses.forEach { addr ->
                    logger.info("  - ${addr.hostAddress} (${addr.hostName})")
                }
            } catch (e: Exception) {
                logger.error("Failed to resolve $domain: ${e.message}")
            }
        }
    }

    @Test
    fun checkJavaDnsProperties() {
        logger.info("=== Java DNS Configuration ===")

        // Print relevant Java DNS properties
        val dnsProps = mapOf(
            "sun.net.inetaddr.ttl" to "DNS cache TTL",
            "sun.net.inetaddr.negative.ttl" to "Negative DNS cache TTL",
            "networkaddress.cache.ttl" to "Network address cache TTL",
            "networkaddress.cache.negative.ttl" to "Negative network address cache TTL",
            "java.net.preferIPv4Stack" to "Prefer IPv4 stack",
            "java.net.preferIPv6Addresses" to "Prefer IPv6 addresses"
        )

        dnsProps.forEach { (prop, desc) ->
            val value = System.getProperty(prop) ?: "not set"
            logger.info("$desc ($prop): $value")
        }

        // Also print resolver configuration
        logger.info("\nNetwork configuration:")
        logger.info("Default timeout: ${System.getProperty("sun.net.connect.timeout", "not set")}")
        logger.info("Default read timeout: ${System.getProperty("sun.net.soTimeout", "not set")}")
    }
}
