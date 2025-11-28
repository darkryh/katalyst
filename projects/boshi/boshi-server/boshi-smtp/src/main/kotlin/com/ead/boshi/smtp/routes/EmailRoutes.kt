package com.ead.boshi.smtp.routes

import com.ead.boshi.shared.constants.DeliveryStatus
import com.ead.boshi.shared.models.EmailStatusResponse
import com.ead.boshi.shared.models.SendEmailRequest
import com.ead.boshi.shared.models.SendEmailResponse
import com.ead.boshi.smtp.services.EmailService
import com.ead.katalyst.ktor.builder.katalystRouting
import com.ead.katalyst.ktor.extension.ktInject
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


@Suppress("unused")
fun Route.emailRoutes() = katalystRouting {
    val emailService by ktInject<EmailService>()

    /**
     * POST /emails/send - Submit email for delivery
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

    /**
     * GET /emails/{messageId}/status - Get email delivery status
     * Rate limited: 100 requests per minute
     */
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

    /**
     * GET /emails/stats - Get server statistics
     */
    get("/emails/stats") {
        call.respond(emailService.getEmailStats())
    }

    /**
     * GET /emails - List emails
     * Query params: page (default 1), limit (default 20)
     */
    get("/emails") {
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        call.respond(emailService.getEmails(page, limit))
    }
}