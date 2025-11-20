package com.ead.boshi.shared.constants

/**
 * Email delivery status constants
 */
object DeliveryStatus {
    const val PENDING = "PENDING"
    const val DELIVERED = "DELIVERED"
    const val FAILED = "FAILED"
    const val PERMANENTLY_FAILED = "PERMANENTLY_FAILED"

    /**
     * All valid statuses
     */
    val ALL = setOf(PENDING, DELIVERED, FAILED, PERMANENTLY_FAILED)

    /**
     * Terminal statuses (no more retries)
     */
    val TERMINAL = setOf(DELIVERED, PERMANENTLY_FAILED)

    /**
     * Retryable statuses
     */
    val RETRYABLE = setOf(FAILED)
}

/**
 * Email validation level constants
 */
object ValidationLevel {
    /** Only check format (syntax) */
    const val FORMAT = "FORMAT"

    /** Check format + spam detection */
    const val FORMAT_AND_SPAM = "FORMAT_AND_SPAM"

    /** Full validation (format, domain, spam) */
    const val FULL = "FULL"

    val ALL = setOf(FORMAT, FORMAT_AND_SPAM, FULL)
}

/**
 * SMTP protocol constants
 */
object SmtpConstants {
    /** Default SMTP port */
    const val SMTP_PORT = 25

    /** SMTP connection timeout in seconds */
    const val DEFAULT_TIMEOUT_SECONDS = 30

    /** SMTP greeting response code */
    const val GREETING_CODE = 220

    /** SMTP command success code */
    const val SUCCESS_CODE = 250

    /** SMTP ready for data code */
    const val DATA_CODE = 354

    /** SMTP service not available code */
    const val SERVICE_UNAVAILABLE_CODE = 421

    /** SMTP permanent failure code (start of 5xx range) */
    const val PERMANENT_FAILURE_START = 500

    /** Server hostname to use in EHLO */
    const val DEFAULT_SERVER_NAME = "boshi.local"

    /** CRLF line ending (RFC 5321) */
    const val CRLF = "\r\n"

    /** Email body terminator (single dot) */
    const val DATA_TERMINATOR = ".$CRLF"

    /** Connection pool size for SMTP connections */
    const val DEFAULT_CONNECTION_POOL_SIZE = 100
}

/**
 * Spam score ranges
 */
object SpamScoreRanges {
    /** Definitely not spam */
    const val NOT_SPAM = 0.0

    /** Likely spam */
    const val LIKELY_SPAM = 7.0

    /** Definitely spam */
    const val DEFINITELY_SPAM = 10.0

    /** Default threshold for blocking emails */
    const val DEFAULT_THRESHOLD = 7.0
}

/**
 * Rate limiting constants
 */
object RateLimiting {
    /** Default max emails per user per hour */
    const val DEFAULT_MAX_EMAILS_PER_HOUR = 5000

    /** Minimum burst rate for testing */
    const val MIN_BURST_RATE = 1

    /** Maximum burst rate to prevent abuse */
    const val MAX_BURST_RATE = 1000
}

/**
 * Data retention constants
 */
object DataRetention {
    /** Default retention in days */
    const val DEFAULT_RETENTION_DAYS = 14

    /** Maximum retention days */
    const val MAX_RETENTION_DAYS = 365

    /** Minimum retention days */
    const val MIN_RETENTION_DAYS = 1

    /** MX record cache TTL in hours */
    const val MX_CACHE_TTL_HOURS = 24

    /** Default cleanup batch size */
    const val DEFAULT_CLEANUP_BATCH_SIZE = 1000
}

/**
 * Retry constants
 */
object RetryPolicy {
    /** Maximum retry attempts */
    const val MAX_RETRIES = 3

    /** Initial retry delay in seconds */
    const val INITIAL_RETRY_DELAY_SECONDS = 300 // 5 minutes

    /** Whether to use exponential backoff */
    const val USE_EXPONENTIAL_BACKOFF = false

    /** Retry delay multiplier for exponential backoff */
    const val BACKOFF_MULTIPLIER = 2.0
}
