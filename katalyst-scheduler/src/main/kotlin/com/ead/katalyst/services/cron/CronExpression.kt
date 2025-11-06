package com.ead.katalyst.services.cron

import java.time.LocalDateTime

/**
 * Represents a cron expression for scheduling tasks.
 * Supports standard 6-field cron format: second minute hour dayOfMonth month dayOfWeek
 *
 * Optimized with O(1) algorithm for calculating next execution time.
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

        secondField = CronField(parts[0], 0..59)
        minuteField = CronField(parts[1], 0..59)
        hourField = CronField(parts[2], 0..23)
        dayOfMonthField = CronField(parts[3], 1..31)
        monthField = CronField(parts[4], 1..12)
        dayOfWeekField = CronField(parts[5], 0..6)
    }

    /**
     * Calculates the next execution time after the given date/time.
     *
     * Algorithm:
     * 1. Start from next second
     * 2. Find next valid second (may wrap to next minute)
     * 3. Find next valid minute (reset seconds, may wrap to next hour)
     * 4. Find next valid hour (reset lower fields, may wrap to next day)
     * 5. Find next valid month (with wraparound)
     * 6. Find next valid day (considering both day-of-month and day-of-week)
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

        // Find next valid month
        candidate = findNextMonth(candidate)

        // Find next valid day (considering day-of-month and day-of-week)
        candidate = findNextDay(candidate)

        return candidate
    }

    /**
     * Finds the next valid month. O(1) for most cases, O(12) maximum.
     */
    private fun findNextMonth(candidate: LocalDateTime): LocalDateTime {
        var current = candidate
        var attempts = 0
        val maxMonths = 12

        while (!monthField.matches(current.monthValue) && attempts < maxMonths) {
            current = current.plusMonths(1).withDayOfMonth(1)
                .withHour(hourField.firstValidValue())
                .withMinute(minuteField.firstValidValue())
                .withSecond(secondField.firstValidValue())
            attempts++
        }

        if (attempts >= maxMonths) {
            throw IllegalStateException("Could not find next execution time for cron expression: $expression within 1 year")
        }

        return current
    }

    /**
     * Finds the next valid day considering both day-of-month and day-of-week.
     * Implements standard cron behavior: either condition can match.
     */
    private fun findNextDay(candidate: LocalDateTime): LocalDateTime {
        var current = candidate
        var attempts = 0
        val maxDays = 366

        while (attempts < maxDays) {
            val dayOfMonthMatch = dayOfMonthField.matches(current.dayOfMonth)
            val dayOfWeekMatch = dayOfWeekField.matches(current.dayOfWeek.value % 7)

            val dayMatches = when {
                dayOfMonthField.isWildcard() && dayOfWeekField.isWildcard() -> true
                dayOfMonthField.isWildcard() -> dayOfWeekMatch
                dayOfWeekField.isWildcard() -> dayOfMonthMatch
                else -> dayOfMonthMatch && dayOfWeekMatch  // Both conditions must match
            }

            if (dayMatches) {
                return current
            }

            current = current.plusDays(1)
                .withHour(hourField.firstValidValue())
                .withMinute(minuteField.firstValidValue())
                .withSecond(secondField.firstValidValue())
            attempts++
        }

        throw IllegalStateException("Could not find next execution time for cron expression: $expression within 1 year")
    }

    override fun toString(): String = expression
}

/**
 * Represents a single field in a cron expression.
 * Uses lazy evaluation with range predicates instead of Set allocation for memory efficiency.
 */
internal class CronField(private val expression: String, internal val range: IntRange) {
    @Suppress("unused")
    internal val rangeSize: Int = range.last - range.first + 1
    private val matcher: (Int) -> Boolean
    private val minValue: Int
    private val maxValue: Int

    init {
        val (matcherFunc, min, max) = parseCronField(expression, range)
        matcher = matcherFunc
        minValue = min
        maxValue = max
    }

    fun matches(value: Int): Boolean {
        require(value in range) { "Value $value is not within valid range $range" }
        return matcher(value)
    }

    fun firstValidValue(): Int = minValue

    fun isWildcard(): Boolean = expression == "*"

    /**
     * Parses a cron field expression and returns:
     * - A matcher function: (Int) -> Boolean
     * - The minimum valid value
     * - The maximum valid value
     */
    private fun parseCronField(expr: String, range: IntRange): Triple<(Int) -> Boolean, Int, Int> {
        return when {
            expr == "*" -> {
                // Wildcard: match all values in range
                Triple({ it in range }, range.first, range.last)
            }
            expr.contains("/") -> {
                // Step values: "*/5" or "0-30/5"
                val (base, stepStr) = expr.split("/")
                val step = stepStr.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid step value: $stepStr")

                require(step > 0) { "Step value must be positive" }

                val baseRange = if (base == "*") range else {
                    val (baseMatcher, baseMin, baseMax) = parseCronField(base, range)
                    (baseMin..baseMax).filter { baseMatcher(it) }
                }

                val validValues = baseRange.filter { (it - range.first) % step == 0 }
                if (validValues.isEmpty()) {
                    throw IllegalArgumentException("Step value '$step' produces no valid values")
                }
                Triple(
                    { it in validValues },
                    validValues.minOrNull() ?: range.first,
                    validValues.maxOrNull() ?: range.last
                )
            }
            expr.contains(",") -> {
                // List of values: "1,3,5"
                val parsedValues = mutableSetOf<Int>()
                expr.split(",").forEach { part ->
                    val (matcher, min, max) = parseCronField(part.trim(), range)
                    (min..max).forEach { if (matcher(it)) parsedValues.add(it) }
                }

                if (parsedValues.isEmpty()) {
                    throw IllegalArgumentException("List expression produces no valid values")
                }

                Triple(
                    { it in parsedValues },
                    parsedValues.minOrNull() ?: range.first,
                    parsedValues.maxOrNull() ?: range.last
                )
            }
            expr.contains("-") -> {
                // Range: "1-5"
                val parts = expr.split("-")
                require(parts.size == 2) { "Invalid range format: $expr" }

                val start = parts[0].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid range start: ${parts[0]}")
                val end = parts[1].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid range end: ${parts[1]}")

                require(start in range && end in range) {
                    "Range $start-$end is not within valid range $range"
                }
                require(start <= end) { "Range start ($start) must be <= end ($end)" }

                Triple(
                    { it in (start..end) },
                    start,
                    end
                )
            }
            else -> {
                // Single value
                val value = expr.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid value: $expr")

                require(value in range) {
                    "Value $value is not within valid range $range"
                }

                Triple({ it == value }, value, value)
            }
        }
    }
}