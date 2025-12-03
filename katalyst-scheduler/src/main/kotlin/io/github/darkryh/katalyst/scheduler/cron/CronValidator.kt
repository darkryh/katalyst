package io.github.darkryh.katalyst.scheduler.cron

/**
 * Validates cron expressions efficiently without creating unnecessary objects.
 *
 * Supports standard 6-field cron format and Quartz cron syntax with '?'.
 * Reuses the parsing logic from CronField to validate expressions.
 */
object CronValidator {

    /**
     * Validates a cron expression and returns a list of validation errors.
     *
     * This is efficient because it directly parses fields without creating
     * a full CronExpression object.
     *
     * @param expression The cron expression to validate
     * @return List of error messages (empty if valid)
     */
    fun validate(expression: String): List<String> {
        val errors = mutableListOf<String>()

        if (expression.isBlank()) {
            errors.add("Cron expression cannot be empty")
            return errors
        }

        val parts = expression.trim().split("\\s+".toRegex())

        if (parts.size != 6) {
            errors.add("Cron expression must have exactly 6 fields: second minute hour dayOfMonth month dayOfWeek")
            return errors
        }

        // Validate each field using direct parsing (reusing CronField logic)
        validateField(parts[0], 0..59, "second", allowQuestion = false, errors)
        validateField(parts[1], 0..59, "minute", allowQuestion = false, errors)
        validateField(parts[2], 0..23, "hour", allowQuestion = false, errors)
        validateField(parts[3], 1..31, "day of month", allowQuestion = true, errors)
        validateField(parts[4], 1..12, "month", allowQuestion = false, errors)
        validateField(parts[5], 0..6, "day of week", allowQuestion = true, errors)

        // Additional validation: at least one of day-of-month or day-of-week must be restricted
        if (parts[3] == "?" && parts[5] == "?") {
            errors.add("At least one of day-of-month or day-of-week must be restricted (not both '?')")
        }

        return errors
    }

    /**
     * Checks if a cron expression is valid.
     *
     * @param expression The cron expression to validate
     * @return true if valid, false otherwise
     */
    fun isValid(expression: String): Boolean {
        return validate(expression).isEmpty()
    }

    private fun validateField(
        expression: String,
        range: IntRange,
        fieldName: String,
        allowQuestion: Boolean = false,
        errors: MutableList<String>
    ) {
        try {
            // Parse the field expression directly using CronField
            // This validates without creating a full CronExpression object
            CronField(expression, range, fieldName, allowQuestion)
        } catch (e: Exception) {
            val errorMsg = e.message?.takeIf { it.isNotEmpty() } ?: "Invalid format"
            errors.add("Invalid $fieldName field '$expression': $errorMsg")
        }
    }
}