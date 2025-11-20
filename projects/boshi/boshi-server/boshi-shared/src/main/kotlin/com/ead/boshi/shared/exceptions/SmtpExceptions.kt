package com.ead.boshi.shared.exceptions

/**
 * Base exception for all SMTP operations
 */
open class SmtpException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Email validation failed (format, domain, spam check)
 */
class SmtpValidationException(message: String) : SmtpException(message)

/**
 * Validation error (generic)
 */
class ValidationException(message: String) : SmtpException(message)

/**
 * Email address format is invalid
 */
class InvalidEmailException(message: String) : SmtpException(message)

class EmailSpamException(val score : Double) : Exception("Email detected as spam, score $score")

class EmailRequestedNotFound() : Exception("email requested not found")
class EmailCantBeBlankOrNull() : Exception("email cannot be blank or null")

/**
 * Email delivery attempt failed
 */
class DeliveryException(
    message: String,
    cause: Throwable? = null
) : SmtpException(message, cause)

/**
 * DNS/MX record resolution failed
 */
class DnsException(
    message: String,
    cause: Throwable? = null
) : SmtpException(message, cause)

/**
 * Database storage operation failed
 */
class StorageException(
    message: String,
    cause: Throwable? = null
) : SmtpException(message, cause)

/**
 * Rate limiting threshold exceeded
 */
class RateLimitExceededException(message: String) : SmtpException(message)

/**
 * Configuration is invalid or missing
 */
class ConfigurationException(message: String) : SmtpException(message)
