package com.ead.katalyst.services.cron

import java.time.LocalDateTime
import java.time.DayOfWeek

/**
 * Represents a cron expression for scheduling tasks.
 * Supports standard 6-field cron format: second minute hour dayOfMonth month dayOfWeek
 * Also supports Quartz cron syntax with '?' for day-of-month or day-of-week.
 *
 * Optimized with O(1) algorithm for calculating next execution time.
 * Extensible design allows adding support for additional cron patterns (L, W, #, etc).
 */
class CronExpression(private val expression: String) {
    private val secondField: CronField
    private val minuteField: CronField
    private val hourField: CronField
    private val dayOfMonthField: CronField
    private val monthField: CronField
    private val dayOfWeekField: CronField

    init {
        val parts = expression.trim().split("\\s+".toRegex())
        require(parts.size == 6) { "Cron expression must have exactly 6 fields: second minute hour dayOfMonth month dayOfWeek" }

        secondField = CronField(parts[0], 0..59, "second")
        minuteField = CronField(parts[1], 0..59, "minute")
        hourField = CronField(parts[2], 0..23, "hour")
        dayOfMonthField = CronField(parts[3], 1..31, "day of month", allowQuestion = true)
        monthField = CronField(parts[4], 1..12, "month")
        dayOfWeekField = CronField(parts[5], 0..6, "day of week", allowQuestion = true)

        // Validate that at least one of day-of-month or day-of-week is restricted
        require(!(parts[3] == "?" && parts[5] == "?")) {
            "At least one of day-of-month or day-of-week must be restricted (not both '?')"
        }
    }

    /**
     * Calculates the next execution time after the given date/time.
     *
     * Algorithm:
     * 1. Start from next second
     * 2. Find next valid second (may wrap to next minute)
     * 3. Find next valid minute (reset seconds, may wrap to next hour)
     * 4. Find next valid hour (reset lower fields, may wrap to next day)
     * 5. Find next valid month (with wraparound, checking if day exists in month)
     * 6. Find next valid day (considering both day-of-month and day-of-week with proper OR logic)
     * 7. Return result
     */
    fun nextExecutionAfter(after: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var candidate = after.plusSeconds(1).withNano(0)

        // Find next valid second
        while (!secondField.matches(candidate.second)) {
            candidate = candidate.plusSeconds(1)
        }

        // Find next valid minute (reset seconds to minimum valid)
        while (!minuteField.matches(candidate.minute)) {
            candidate = candidate.plusMinutes(1).withSecond(secondField.firstValidValue())
        }

        // Find next valid hour (reset lower fields)
        while (!hourField.matches(candidate.hour)) {
            candidate = candidate.plusHours(1)
                .withMinute(minuteField.firstValidValue())
                .withSecond(secondField.firstValidValue())
        }

        // Find next valid month and day (with proper handling of invalid days in months)
        candidate = findNextMonthAndDay(candidate)

        return candidate
    }

    /**
     * Finds the next valid month and day together, handling edge cases like:
     * - Days that don't exist in certain months (e.g., Feb 31)
     * - Leap years (Feb 29)
     * - Multi-year transitions for specific dates
     *
     * Algorithm:
     * 1. Find next valid month (may advance year)
     * 2. Adjust day-of-month to be valid in that month (e.g., Feb 31 → Feb 28/29)
     * 3. Find next valid day within the valid date range
     * 4. May need to advance to next month if no valid day found
     */
    private fun findNextMonthAndDay(candidate: LocalDateTime): LocalDateTime {
        var current = candidate
        var monthAttempts = 0
        val maxMonthAttempts = 120 // 10 years to handle leap year cycles (Feb 29 repeats every 4 years typically)

        while (monthAttempts < maxMonthAttempts) {
            // Find next valid month
            if (!monthField.matches(current.monthValue)) {
                current = current.plusMonths(1).withDayOfMonth(1)
                    .withHour(hourField.firstValidValue())
                    .withMinute(minuteField.firstValidValue())
                    .withSecond(secondField.firstValidValue())
                monthAttempts++
                continue
            }

            // Month is valid, now find valid day within this month
            val result = findNextDayInMonth(current)
            if (result != null) {
                return result
            }

            // No valid day in this month, advance to next month
            current = current.plusMonths(1).withDayOfMonth(1)
                .withHour(hourField.firstValidValue())
                .withMinute(minuteField.firstValidValue())
                .withSecond(secondField.firstValidValue())
            monthAttempts++
        }

        throw IllegalStateException("Could not find next execution time for cron expression: $expression within 10 years")
    }

    /**
     * Finds the next valid day within a specific month.
     * Returns null if no valid day exists in this month (e.g., need to try next month).
     *
     * Handles:
     * - Day-of-month matching
     * - Day-of-week matching
     * - OR logic between them
     * - Invalid days in month (e.g., Feb 31)
     */
    private fun findNextDayInMonth(startOfMonth: LocalDateTime): LocalDateTime? {
        val daysInCurrentMonth = daysInMonth(startOfMonth.monthValue, startOfMonth.year)
        var current = startOfMonth
        var dayAttempts = 0

        while (dayAttempts <= daysInCurrentMonth) {
            // Check if current day is valid
            if (current.dayOfMonth <= daysInCurrentMonth) {
                val dayOfMonthMatch = dayOfMonthField.matches(current.dayOfMonth)
                val dayOfWeekMatch = dayOfWeekField.matches(current.dayOfWeek.value % 7)

                val dayMatches = when {
                    dayOfMonthField.isUnrestricted() && dayOfWeekField.isUnrestricted() -> true
                    dayOfMonthField.isUnrestricted() -> dayOfWeekMatch
                    dayOfWeekField.isUnrestricted() -> dayOfMonthMatch
                    else -> dayOfMonthMatch || dayOfWeekMatch  // OR logic: either condition can match
                }

                if (dayMatches) {
                    return current
                }
            }

            current = current.plusDays(1)
            dayAttempts++

            // If we've gone past the end of the month, we're done
            if (current.monthValue != startOfMonth.monthValue) {
                return null
            }
        }

        return null
    }

    /**
     * Returns the number of days in a given month/year, accounting for leap years.
     */
    private fun daysInMonth(month: Int, year: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> throw IllegalArgumentException("Invalid month: $month")
        }
    }

    /**
     * Checks if a year is a leap year.
     * Leap year rules:
     * - Divisible by 400 → leap year
     * - Divisible by 100 → not a leap year
     * - Divisible by 4 → leap year
     * - Otherwise → not a leap year
     */
    private fun isLeapYear(year: Int): Boolean {
        return when {
            year % 400 == 0 -> true
            year % 100 == 0 -> false
            year % 4 == 0 -> true
            else -> false
        }
    }

    override fun toString(): String = expression
}

/**
 * Represents a single field in a cron expression.
 * Uses lazy evaluation with range predicates instead of Set allocation for memory efficiency.
 * Supports extensible parsing through FieldParser strategy.
 */
internal class CronField(
    private val expression: String,
    internal val range: IntRange,
    private val fieldName: String = "field",
    private val allowQuestion: Boolean = false
) {
    @Suppress("unused")
    internal val rangeSize: Int = range.last - range.first + 1
    private val matcher: (Int) -> Boolean
    private val minValue: Int
    private val maxValue: Int
    private val unrestricted: Boolean

    init {
        val (matcherFunc, min, max, isUnrestricted) = parseCronField(expression, range)
        matcher = matcherFunc
        minValue = min
        maxValue = max
        unrestricted = isUnrestricted
    }

    fun matches(value: Int): Boolean {
        require(value in range) { "Value $value is not within valid range $range" }
        return matcher(value)
    }

    fun firstValidValue(): Int = minValue

    fun isWildcard(): Boolean = expression == "*"

    fun isUnrestricted(): Boolean = unrestricted

    /**
     * Parses a cron field expression and returns:
     * - A matcher function: (Int) -> Boolean
     * - The minimum valid value
     * - The maximum valid value
     * - Whether field is unrestricted (wildcard or ?)
     */
    private fun parseCronField(expr: String, range: IntRange): Tuple4<(Int) -> Boolean, Int, Int, Boolean> {
        return when {
            expr == "*" -> {
                // Wildcard: match all values in range
                Tuple4({ it in range }, range.first, range.last, true)
            }
            expr == "?" && allowQuestion -> {
                // Question mark (Quartz syntax): no specific value, unrestricted
                Tuple4({ it in range }, range.first, range.last, true)
            }
            expr == "?" && !allowQuestion -> {
                throw IllegalArgumentException("'?' is not allowed in $fieldName field")
            }
            expr.contains("/") -> {
                // Step values: "*/5" or "0-30/5" or "*/2" or "1-23/2" or "0/2" (every 2 seconds/odd hours/every 2 from 0)
                val (base, stepStr) = expr.split("/")
                val step = stepStr.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid step value: $stepStr in $fieldName field")

                require(step > 0) { "Step value must be positive in $fieldName field" }

                val baseRange = if (base == "*") {
                    // Wildcard: apply step to full range
                    range
                } else if (base.contains("-")) {
                    // Range like "1-23" - use the range start for step alignment
                    val (baseMatcher, baseMin, baseMax, _) = parseCronField(base, range)
                    (baseMin..baseMax)
                } else {
                    // Single value like "0/2" - apply step starting from that value
                    val startValue = base.toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid value: $base in $fieldName field")
                    require(startValue in range) {
                        "Value $startValue is not within valid range $range in $fieldName field"
                    }
                    // Create range from start value to end of field range
                    (startValue..range.last)
                }

                val validValues = baseRange.filter { (it - baseRange.first) % step == 0 }
                if (validValues.isEmpty()) {
                    throw IllegalArgumentException("Step value '$step' produces no valid values in $fieldName field")
                }

                // For single values with step (e.g., "59/2"), reject if only the starting value is produced
                if (!base.contains("-") && base != "*" && validValues.size == 1) {
                    throw IllegalArgumentException("Step value '$step' produces no valid values in $fieldName field")
                }
                Tuple4(
                    { it in validValues },
                    validValues.minOrNull() ?: range.first,
                    validValues.maxOrNull() ?: range.last,
                    false
                )
            }
            expr.contains(",") -> {
                // List of values: "1,3,5"
                val parsedValues = mutableSetOf<Int>()
                expr.split(",").forEach { part ->
                    val (matcher, min, max, _) = parseCronField(part.trim(), range)
                    (min..max).forEach { if (matcher(it)) parsedValues.add(it) }
                }

                if (parsedValues.isEmpty()) {
                    throw IllegalArgumentException("List expression produces no valid values in $fieldName field")
                }

                Tuple4(
                    { it in parsedValues },
                    parsedValues.minOrNull() ?: range.first,
                    parsedValues.maxOrNull() ?: range.last,
                    false
                )
            }
            expr.contains("-") -> {
                // Range: "1-5"
                val parts = expr.split("-")
                require(parts.size == 2) { "Invalid range format: $expr in $fieldName field" }

                val start = parts[0].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid range start: ${parts[0]} in $fieldName field")
                val end = parts[1].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid range end: ${parts[1]} in $fieldName field")

                require(start in range && end in range) {
                    "Range $start-$end is not within valid range $range in $fieldName field"
                }
                require(start <= end) { "Range start ($start) must be <= end ($end) in $fieldName field" }

                Tuple4(
                    { it in (start..end) },
                    start,
                    end,
                    false
                )
            }
            else -> {
                // Single value
                val value = expr.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid value: $expr in $fieldName field")

                require(value in range) {
                    "Value $value is not within valid range $range in $fieldName field"
                }

                Tuple4({ it == value }, value, value, false)
            }
        }
    }
}

/**
 * A simple tuple class for returning 4 values from parseCronField.
 * This is a workaround for returning multiple values in a clean way.
 */
internal data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)