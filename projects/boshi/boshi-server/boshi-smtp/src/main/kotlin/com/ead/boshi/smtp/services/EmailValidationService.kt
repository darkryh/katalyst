package com.ead.boshi.smtp.services

import com.ead.boshi.shared.constants.ValidationLevel
import com.ead.boshi.shared.exceptions.InvalidEmailException
import com.ead.boshi.shared.exceptions.ValidationException
import com.ead.boshi.shared.models.EmailValidationResult
import io.github.darkryh.katalyst.core.component.Component
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

/**
 * Service for validating email addresses and detecting spam patterns
 */
class EmailValidationService : Component {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        // RFC 5321/5322 simplified email regex
        private val EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"
        )

        // Common disposable email domains (sample list)
        private val DISPOSABLE_DOMAINS = setOf(
            "mailinator.com",
            "10minutemail.com",
            "tempmail.com",
            "throwaway.email",
            "yopmail.com"
        )

        // Spam keywords and patterns
        private val SPAM_KEYWORDS = setOf(
            "free money",
            "you have won",
            "click here",
            "buy now",
            "act now",
            "limited time",
            "verify account",
            "confirm password",
            "urgent action"
        )
    }

    /**
     * Validate email address format and detect spam characteristics
     * @param senderEmail sender email address
     * @param recipientEmail recipient email address
     * @param subject email subject
     * @param body email body
     * @param validationLevel validation strictness level (FORMAT, FORMAT_AND_SPAM, FULL)
     * @return validation result with spam detection
     */
    fun validateEmail(
        senderEmail: String,
        recipientEmail: String,
        subject: String,
        body: String,
        validationLevel: String = "FORMAT_AND_SPAM"
    ): EmailValidationResult {
        logger.debug("Validating email from $senderEmail to $recipientEmail with level: $validationLevel")

        val validationDetails = mutableMapOf<String, Any>()

        // Validate email formats
        val senderValid = isValidEmailFormat(senderEmail)
        val recipientValid = isValidEmailFormat(recipientEmail)

        if (!senderValid || !recipientValid) {
            val reason = when {
                !senderValid -> "Invalid sender email format: $senderEmail"
                !recipientValid -> "Invalid recipient email format: $recipientEmail"
                else -> "Invalid email format"
            }
            throw InvalidEmailException(reason)
        }

        validationDetails["senderValid"] = senderValid
        validationDetails["recipientValid"] = recipientValid

        // Calculate spam score
        val spamScore = calculateSpamScore(senderEmail, recipientEmail, subject, body, validationLevel)
        val isSpam = spamScore > 0.6 // Threshold: 60% confidence

        validationDetails["spamScore"] = spamScore
        validationDetails["isSpam"] = isSpam
        validationDetails["validationLevel"] = validationLevel

        // Check for disposable email domains if full validation
        if (validationLevel == "FULL") {
            val senderDomain = senderEmail.substringAfterLast("@")
            val recipientDomain = recipientEmail.substringAfterLast("@")

            if (isDisposableDomain(senderDomain) || isDisposableDomain(recipientDomain)) {
                validationDetails["disposableDomain"] = true
                throw ValidationException("Disposable email domain not allowed in full validation mode")
            }
        }

        logger.debug("Email validation completed. Spam score: $spamScore, IsSpam: $isSpam")

        return EmailValidationResult(
            isValid = !isSpam,
            senderValidated = senderValid,
            recipientValidated = recipientValid,
            spamScore = spamScore,
            isSpam = isSpam,
            validationDetails = validationDetails
        )
    }

    /**
     * Validate email format using regex
     */
    private fun isValidEmailFormat(email: String): Boolean {
        if (email.isBlank() || email.length > 254) {
            return false
        }
        val matcher = EMAIL_PATTERN.matcher(email.trim())
        return matcher.matches()
    }

    /**
     * Check if domain is a known disposable/temporary email service
     */
    private fun isDisposableDomain(domain: String): Boolean {
        return DISPOSABLE_DOMAINS.contains(domain.lowercase())
    }

    /**
     * Calculate spam probability score (0.0 to 1.0)
     */
    private fun calculateSpamScore(
        senderEmail: String,
        recipientEmail: String,
        subject: String,
        body: String,
        validationLevel: String
    ): Double {
        var score = 0.0
        var weight = 0

        // Check sender domain legitimacy (5 points max)
        if (!isTrustedDomain(senderEmail.substringAfterLast("@"))) {
            score += 3.0
            weight += 5
        }

        // Check for spam keywords in subject (10 points max)
        val subjectLower = subject.lowercase()
        val matchingKeywords = SPAM_KEYWORDS.count { subjectLower.contains(it) }
        if (matchingKeywords > 0) {
            score += minOf(matchingKeywords * 2.0, 10.0)
            weight += 10
        }

        // Check for suspicious patterns in body (10 points max)
        val bodyLower = body.lowercase()
        val suspiciousPatterns = listOf(
            Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE),  // URLs
            Regex("\\b[A-Z]{4,}\\b"),  // EXCESSIVE CAPITALS
            Regex("!{2,}")  // Multiple exclamation marks
        )

        var patternScore = 0.0
        for (pattern in suspiciousPatterns) {
            val matches = pattern.findAll(bodyLower).count()
            if (matches > 5) {
                patternScore += minOf(matches.toDouble() * 0.5, 10.0)
            }
        }
        score += patternScore
        weight += 10

        // Full validation increases spam detection sensitivity
        if (validationLevel == "FULL") {
            score *= 1.2
        }

        // Normalize score to 0.0-1.0 range
        return minOf(score / (weight + 1), 1.0)
    }

    /**
     * Check if sender domain is from a trusted provider
     */
    private fun isTrustedDomain(domain: String): Boolean {
        val trustedDomains = setOf(
            "gmail.com",
            "yahoo.com",
            "outlook.com",
            "hotmail.com",
            "aol.com",
            "icloud.com",
            "protonmail.com",
            "fastmail.com"
        )

        return trustedDomains.contains(domain.lowercase())
    }
}
