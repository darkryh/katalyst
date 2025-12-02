package com.ead.boshi.smtp.routes

import com.ead.boshi.shared.constants.DeliveryStatus
import com.ead.boshi.shared.models.EmailStatusResponse
import com.ead.boshi.shared.models.SendEmailRequest
import com.ead.boshi.shared.models.SendEmailResponse
import com.ead.boshi.smtp.dns.MxRecordResolver
import com.ead.boshi.smtp.services.EmailService
import com.ead.katalyst.ktor.builder.katalystRouting
import com.ead.katalyst.ktor.extension.ktInject
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory


@Suppress("unused")
fun Route.emailRoutes() = katalystRouting {
    val emailService by ktInject<EmailService>()
    val mxResolver by ktInject<MxRecordResolver>()
    val logger = LoggerFactory.getLogger("EmailRoutes")

    /**
     * POST /emails/send - Submit email for delivery
     * Requires: Authorization: Bearer <API_KEY>
     * Rate limited: 100 requests per minute
     * Request body:
     * {
     *   "from": "sender@example.com",
     *   "to": "recipient@example.com",
     *   "subject": "Email Subject",
     *   "html": "<html>Email body</html>",
     *   "tags": "newsletter,marketing",
     *   "metadata": {"key": "value"}
     * }
     */
    authenticate("api-key") {
        post("/emails/send") {
            val request = call.receive<SendEmailRequest>()

            val result = emailService
                .send(
                    request = request,
                    remoteHost = call.request.local.remoteHost
                )

            call.respond(
                HttpStatusCode.Accepted,
                SendEmailResponse(
                    messageId = result.messageId,
                    status = DeliveryStatus.PENDING,
                    message = "Email accepted for delivery"
                )
            )
        }
    }

    /**
     * GET /emails/{messageId}/status - Get email delivery status
     * Requires: Authorization: Bearer <API_KEY>
     * Rate limited: 100 requests per minute
     */
    authenticate("api-key") {
        get("/emails/{messageId}/status") {

            val messageId = call.parameters["messageId"]
            val deliveryStatus = emailService.getMessageStatus(messageId)

            call.respond(
                EmailStatusResponse(
                    messageId = deliveryStatus.messageId,
                    status = deliveryStatus.status,
                    attempts = deliveryStatus.attemptCount,
                    errorMessage = deliveryStatus.errorMessage,
                    deliveredAt = deliveryStatus.deliveredAtMillis
                )
            )
        }
    }

    /**
     * GET /emails/stats - Get server statistics
     * Requires: Authorization: Bearer <API_KEY>
     */
    authenticate("api-key") {
        get("/emails/stats") {
            call.respond(emailService.getEmailStats())
        }
    }

    /**
     * GET /emails - List emails
     * Requires: Authorization: Bearer <API_KEY>
     * Query params: page (default 1), limit (default 20)
     */
    authenticate("api-key") {
        get("/emails") {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            call.respond(emailService.getEmails(page, limit))
        }
    }

    /**
     * GET /debug/dns/{domain} - Diagnostic endpoint to test DNS resolution from within JVM
     * Requires: Authorization: Bearer <API_KEY>
     * Example: GET http://localhost:8080/debug/dns/gmail.com
     */
    authenticate("api-key") {
        get("/debug/dns/{domain}") {
        val domain = call.parameters["domain"] ?: "gmail.com"

        logger.info("=== DNS Diagnostic Request for: $domain ===")

        // Test 1: System InetAddress resolution
        logger.info("Test 1: System InetAddress resolution")
        var systemDnsStatus = "failed"
        var systemDnsError = "Unknown error"
        var systemDnsIps = listOf<String>()
        try {
            val addresses = java.net.InetAddress.getAllByName(domain)
            systemDnsIps = addresses.map { it.hostAddress }
            logger.info("✓ System resolved to: $systemDnsIps")
            systemDnsStatus = "success"
        } catch (e: Exception) {
            logger.error("✗ System DNS failed: ${e.message}")
            systemDnsError = e.message ?: "Unknown error"
        }

        // Test 2: dnsjava MX record resolution
        logger.info("Test 2: dnsjava MX record resolution")
        var mxStatus = "failed"
        var mxError = "Unknown error"
        var mxRecordsCount = 0
        var mxHosts = listOf<String>()
        try {
            val mxRecords = mxResolver.resolveMxRecords(domain)
            mxHosts = mxRecords.map { "${it.mxHostname} (priority: ${it.priority})" }
            mxRecordsCount = mxRecords.size
            logger.info("✓ MX records resolved: $mxHosts")
            mxStatus = "success"
        } catch (e: Exception) {
            logger.error("✗ MX resolution failed: ${e.message}")
            mxError = e.message ?: "Unknown error"
        }

        // Test 3: Java DNS properties
        logger.info("Test 3: Java DNS configuration")
        val dnsProps = mapOf(
            "sun.net.inetaddr.ttl" to (System.getProperty("sun.net.inetaddr.ttl") ?: "not set"),
            "networkaddress.cache.ttl" to (System.getProperty("networkaddress.cache.ttl") ?: "not set"),
            "java.net.preferIPv4Stack" to (System.getProperty("java.net.preferIPv4Stack") ?: "not set")
        )
        logger.info("DNS Properties: $dnsProps")

        // Test 4: JNDI DNS resolution
        logger.info("Test 4: JNDI DNS resolution")
        var jndiStatus = "failed"
        var jndiError = "Unknown error"
        var jndiRecords = listOf<String>()
        try {
            val env = java.util.Hashtable<String, String>()
            env["java.naming.factory.initial"] = "com.sun.jndi.dns.DnsContextFactory"
            val ctx = javax.naming.directory.InitialDirContext(env)
            val attributes = ctx.getAttributes(domain, arrayOf("MX"))
            val mxAttr = attributes.get("MX")

            if (mxAttr != null) {
                val records = mutableListOf<String>()
                for (i in 0 until mxAttr.size()) {
                    records.add(mxAttr.get(i).toString())
                }
                jndiRecords = records
                logger.info("✓ JNDI MX records: $jndiRecords")
                jndiStatus = "success"
            } else {
                logger.warn("✗ JNDI: No MX records found")
                jndiStatus = "no_records"
            }
            ctx.close()
        } catch (e: Exception) {
            logger.error("✗ JNDI DNS failed: ${e.message}")
            jndiError = e.message ?: "Unknown error"
        }

        logger.info("=== Diagnostic Results ===")

        // Log all results
        logger.info("domain: $domain")
        logger.info("system_dns: status=$systemDnsStatus, ips=$systemDnsIps, error=$systemDnsError")
        logger.info("mx_records: status=$mxStatus, count=$mxRecordsCount, records=$mxHosts, error=$mxError")
        logger.info("java_dns_properties: $dnsProps")
        logger.info("jndi_dns: status=$jndiStatus, records=$jndiRecords, error=$jndiError")

        // Build response as plain text for simplicity
        val responseText = """
            DNS Diagnostic Test Results for: $domain
            ========================================

            System InetAddress Resolution:
              Status: $systemDnsStatus
              IPs: $systemDnsIps
              Error: $systemDnsError

            dnsjava MX Record Resolution:
              Status: $mxStatus
              Count: $mxRecordsCount
              Records: $mxHosts
              Error: $mxError

            Java DNS Properties:
              sun.net.inetaddr.ttl: ${dnsProps["sun.net.inetaddr.ttl"]}
              networkaddress.cache.ttl: ${dnsProps["networkaddress.cache.ttl"]}
              java.net.preferIPv4Stack: ${dnsProps["java.net.preferIPv4Stack"]}

            JNDI DNS Resolution:
              Status: $jndiStatus
              Records: $jndiRecords
              Error: $jndiError
        """.trimIndent()

        call.respondText(responseText, ContentType.Text.Plain)
        }
    }
}