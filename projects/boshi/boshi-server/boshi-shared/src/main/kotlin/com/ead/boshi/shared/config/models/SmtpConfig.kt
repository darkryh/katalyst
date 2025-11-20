package com.ead.boshi.shared.config.models

/**
 * SMTP client configuration for sending emails
 */
data class SmtpConfig(
    val host: String,
    val port: Int = 25,
    val localHostname: String = "boshi.local",
    val username: String = "",
    val password: String = "",
    val useTls: Boolean = false,
    val connectionTimeoutSeconds: Int = 30,
    val readTimeoutSeconds: Int = 30
)