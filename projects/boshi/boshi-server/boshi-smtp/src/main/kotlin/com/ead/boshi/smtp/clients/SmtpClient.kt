package com.ead.boshi.smtp.clients

import com.ead.boshi.shared.config.models.SmtpConfig
import com.ead.boshi.shared.exceptions.DeliveryException
import com.ead.katalyst.core.component.Component
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.time.Instant
import java.util.Base64
import javax.net.ssl.SSLSocketFactory

/**
 * SMTP client for sending emails to mail servers
 * Handles SMTP protocol communication and error handling
 *
 * SmtpConfig is auto-injected by the DI container (from AutomaticServiceConfigLoader)
 * No manual loadConfig() calls needed!
 */
class SmtpClient(
    val smtpConfig: SmtpConfig
) : Component {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val CRLF = "\r\n"
        private const val SMTP_OK = 250
        private const val SMTP_AUTH_OK = 334
        private const val SMTP_AUTH_SUCCESS = 235
        private const val SMTP_DATA_OK = 354
        private const val SMTP_ACCEPTED = 250

        private const val SMTP_READ_TIMEOUT_MS = 30000
        private const val SMTP_CONNECT_TIMEOUT_MS = 10000
    }

    /**
     * Send email via SMTP to specified host
     * @param smtpHost SMTP server hostname or IP
     * @param senderEmail sender email address
     * @param recipientEmail recipient email address
     * @param subject email subject
     * @param body email body (HTML/plain text)
     * @param messageId unique message identifier
     * @return SMTP server response message
     * @throws DeliveryException if sending fails
     */
    fun sendEmail(
        smtpHost: String,
        senderEmail: String,
        recipientEmail: String,
        subject: String,
        body: String,
        messageId: String,
        port: Int = smtpConfig.port,
        useTls: Boolean = smtpConfig.useTls
    ): String {
        logger.debug("Sending email via SMTP to $smtpHost for message: $messageId")

        // Port 465 uses implicit TLS (immediate SSL), others use STARTTLS
        val implicitTls = port == 465
        val socket = if (implicitTls) {
            val sslFactory = SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
            sslFactory.createSocket() as javax.net.ssl.SSLSocket
        } else {
            Socket()
        }

        try {
            socket.soTimeout = SMTP_READ_TIMEOUT_MS

            logger.debug("Connecting to $smtpHost:$port (TLS: $useTls, ImplicitTLS: $implicitTls)")
            socket.connect(
                java.net.InetSocketAddress(smtpHost, port),
                SMTP_CONNECT_TIMEOUT_MS
            )

            BufferedReader(InputStreamReader(socket.inputStream)).use { reader ->
                BufferedWriter(OutputStreamWriter(socket.outputStream)).use { writer ->
                    return performSmtpExchange(
                        reader,
                        writer,
                        socket,
                        smtpHost,
                        senderEmail,
                        recipientEmail,
                        subject,
                        body,
                        messageId,
                        useTls,
                        implicitTls
                    )
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            logger.error("SMTP timeout connecting to $smtpHost", e)
            throw DeliveryException("SMTP connection timeout for $smtpHost", e)
        } catch (e: java.net.ConnectException) {
            logger.error("Failed to connect to SMTP server $smtpHost", e)
            throw DeliveryException("Cannot connect to SMTP server $smtpHost", e)
        } catch (e: Exception) {
            logger.error("SMTP error while sending to $smtpHost", e)
            throw DeliveryException("SMTP error: ${e.message}", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                logger.warn("Error closing SMTP socket", e)
            }
        }
    }

    /**
     * Perform SMTP protocol conversation with mail server
     */
    private fun performSmtpExchange(
        initialReader: BufferedReader,
        initialWriter: BufferedWriter,
        initialSocket: Socket,
        smtpHost: String,
        senderEmail: String,
        recipientEmail: String,
        subject: String,
        body: String,
        messageId: String,
        useTls: Boolean,
        implicitTls: Boolean = false
    ): String {
        var reader = initialReader
        var writer = initialWriter
        var socket = initialSocket

        try {
            // Read initial server response
            val welcomeResponse = reader.readLine() ?: throw DeliveryException("No welcome response from $smtpHost")
            logger.trace("SMTP << $welcomeResponse")

            if (!welcomeResponse.startsWith("220")) {
                throw DeliveryException("Unexpected SMTP welcome response: $welcomeResponse")
            }

            // Send EHLO
            sendCommand(writer, "EHLO ${smtpConfig.localHostname}")
            val ehloResponse = readSmtpResponse(reader)

            if (!ehloResponse.startsWith("250")) {
                throw DeliveryException("EHLO rejected by $smtpHost: $ehloResponse")
            }

            // Upgrade to TLS if required (skip if we already have implicit TLS on port 465)
            if (useTls && !implicitTls) {
                logger.debug("Upgrading connection to $smtpHost to TLS via STARTTLS")
                sendCommand(writer, "STARTTLS")
                val starttlsResponse = readSmtpResponse(reader)

                if (!starttlsResponse.startsWith("220")) {
                    throw DeliveryException("STARTTLS not supported: $starttlsResponse")
                }

                // Close old streams
                writer.close()
                reader.close()

                // Upgrade to SSL socket
                val sslFactory = SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
                val sslSocket = sslFactory.createSocket(
                    socket,
                    smtpHost,
                    smtpConfig.port,
                    true
                ) as javax.net.ssl.SSLSocket
                sslSocket.soTimeout = SMTP_READ_TIMEOUT_MS

                socket = sslSocket
                reader = BufferedReader(InputStreamReader(socket.inputStream))
                writer = BufferedWriter(OutputStreamWriter(socket.outputStream))

                // Send EHLO again after TLS
                sendCommand(writer, "EHLO ${smtpConfig.localHostname}")
                val ehloAfterTls = readSmtpResponse(reader)
                if (!ehloAfterTls.startsWith("250")) {
                    throw DeliveryException("EHLO after STARTTLS rejected: $ehloAfterTls")
                }
            }

            // Authenticate if credentials provided
            if (smtpConfig.username.isNotEmpty() && smtpConfig.password.isNotEmpty()) {
                performSmtpAuth(reader, writer)
            }

            // Send message
            sendCommand(writer, "MAIL FROM:<$senderEmail>")
            val mailFromResponse = readSmtpResponse(reader)
            if (mailFromResponse.code() != SMTP_OK) {
                throw DeliveryException("MAIL FROM rejected: $mailFromResponse")
            }

            // Send recipient
            sendCommand(writer, "RCPT TO:<$recipientEmail>")
            val rcptToResponse = readSmtpResponse(reader)
            if (rcptToResponse.code() != SMTP_OK) {
                throw DeliveryException("RCPT TO rejected: $rcptToResponse")
            }

            // Send data
            sendCommand(writer, "DATA")
            val dataResponse = readSmtpResponse(reader)
            if (dataResponse.code() != SMTP_DATA_OK) {
                throw DeliveryException("DATA command rejected: $dataResponse")
            }

            // Send email headers and body
            sendEmailData(writer, senderEmail, recipientEmail, subject, body, messageId)

            // Complete message
            sendCommand(writer, ".")
            val acceptResponse = readSmtpResponse(reader)
            if (acceptResponse.code() != SMTP_ACCEPTED) {
                throw DeliveryException("Message rejected by server: $acceptResponse")
            }

            // Quit
            sendCommand(writer, "QUIT")
            readSmtpResponse(reader)

            logger.debug("Email sent successfully via $smtpHost for message: $messageId")
            return acceptResponse
        } finally {
            try {
                writer.close()
                reader.close()
            } catch (e: Exception) {
                logger.warn("Error closing SMTP streams", e)
            }
        }
    }

    /**
     * Perform SMTP authentication (LOGIN mechanism)
     */
    private fun performSmtpAuth(reader: BufferedReader, writer: BufferedWriter) {
        sendCommand(writer, "AUTH LOGIN")
        val authResponse = readSmtpResponse(reader)
        if (authResponse.code() != SMTP_AUTH_OK) {
            throw DeliveryException("AUTH LOGIN not supported: $authResponse")
        }

        // Send encoded username
        val encodedUsername = Base64.getEncoder().encodeToString(smtpConfig.username.toByteArray())
        sendCommand(writer, encodedUsername)
        val usernameResponse = readSmtpResponse(reader)
        if (usernameResponse.code() != SMTP_AUTH_OK) {
            throw DeliveryException("Username rejected: $usernameResponse")
        }

        // Send encoded password
        val encodedPassword = Base64.getEncoder().encodeToString(smtpConfig.password.toByteArray())
        sendCommand(writer, encodedPassword)
        val authCompleteResponse = readSmtpResponse(reader)
        if (authCompleteResponse.code() != SMTP_AUTH_SUCCESS) {
            throw DeliveryException("Authentication failed: $authCompleteResponse")
        }
    }

    /**
     * Send email message data (headers and body)
     */
    private fun sendEmailData(
        writer: BufferedWriter,
        senderEmail: String,
        recipientEmail: String,
        subject: String,
        body: String,
        messageId: String
    ) {
        val now = Instant.now()

        writer.write("From: <$senderEmail>$CRLF")
        writer.write("To: <$recipientEmail>$CRLF")
        writer.write("Subject: $subject$CRLF")
        writer.write("Message-ID: <$messageId>$CRLF")
        writer.write("Date: $now$CRLF")
        writer.write("Content-Type: text/html; charset=UTF-8$CRLF")
        writer.write("Content-Transfer-Encoding: 8bit$CRLF")
        writer.write(CRLF)
        writer.write(body)
        writer.write(CRLF)
        writer.flush()
    }

    /**
     * Send SMTP command
     */
    private fun sendCommand(writer: BufferedWriter, command: String) {
        logger.trace("SMTP >> $command")
        writer.write(command + CRLF)
        writer.flush()
    }

    /**
     * Read complete SMTP response (may be multi-line)
     */
    private fun readSmtpResponse(reader: BufferedReader): String {
        val lines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine() ?: throw DeliveryException("SMTP connection closed unexpectedly")
            logger.trace("SMTP << $line")
            lines.add(line)

            // Check if this is the final line (no hyphen after code)
            if (line.length > 3 && line[3] != '-') {
                break
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Extract response code from first line of SMTP response
     */
    private fun String.code(): Int {
        return try {
            this.split("\n")[0].take(3).toInt()
        } catch (e: Exception) {
            0
        }
    }
}
