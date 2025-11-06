package com.ead.katalyst.services.cron

/**
 * Validates cron expressions efficiently without creating unnecessary objects.
 *
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
        validateField(parts[0], 0..59, "second", errors)
        validateField(parts[1], 0..59, "minute", errors)
        validateField(parts[2], 0..23, "hour", errors)
        validateField(parts[3], 1..31, "day of month", errors)
        validateField(parts[4], 1..12, "month", errors)
        validateField(parts[5], 0..6, "day of week", errors)

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
        errors: MutableList<String>
    ) {
        try {
            // Parse the field expression directly using CronField
            // This validates without creating a full CronExpression object
            CronField(expression, range)
        } catch (e: Exception) {
            val errorMsg = e.message?.takeIf { it.isNotEmpty() } ?: "Invalid format"
            errors.add("Invalid $fieldName field '$expression': $errorMsg")
        }
    }
}