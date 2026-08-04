package io.github.darkryh.katalyst.scheduler.cron

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Property-based (dependency-free generative) tests for [CronExpression.nextExecutionAfter].
 *
 * Rather than a few fixed expressions, these generate many random daily schedules and random base
 * times and assert the universal invariants hold for all of them:
 *  - the next fire is strictly after the input instant,
 *  - it matches every specified field (second/minute/hour),
 *  - a daily schedule's next fire is within ~24h,
 *  - no matching instant is ever skipped: the returned fire is the *earliest* match after the base.
 */
class CronExpressionPropertyTest {

    @Test
    fun `next fire is always after the input and matches every field`() {
        repeat(1_000) {
            val s = Random.nextInt(0, 60)
            val m = Random.nextInt(0, 60)
            val h = Random.nextInt(0, 24)
            // Daily fire at h:m:s (any day-of-month, any day-of-week).
            val cron = CronExpression("$s $m $h * * ?")
            val base = randomDateTime()

            val next = cron.nextExecutionAfter(base)

            assertTrue(next.isAfter(base), "next ($next) not after base ($base) for '$s $m $h * * ?'")
            assertEquals(s, next.second, "second mismatch for '$s $m $h * * ?' base=$base -> $next")
            assertEquals(m, next.minute, "minute mismatch for '$s $m $h * * ?' base=$base -> $next")
            assertEquals(h, next.hour, "hour mismatch for '$s $m $h * * ?' base=$base -> $next")
            assertTrue(
                !next.isAfter(base.plusDays(1).plusSeconds(1)),
                "daily fire $next is more than ~24h after base $base"
            )
        }
    }

    @Test
    fun `an every-second schedule always advances by exactly one second`() {
        val cron = CronExpression("* * * * * ?")
        repeat(1_000) {
            val base = randomDateTime()
            val next = cron.nextExecutionAfter(base)
            assertEquals(base.withNano(0).plusSeconds(1), next, "every-second next-fire wrong for base=$base")
        }
    }

    private fun randomDateTime(): LocalDateTime =
        LocalDateTime.of(
            Random.nextInt(2024, 2031),
            Random.nextInt(1, 13),
            Random.nextInt(1, 28), // keep day valid in every month
            Random.nextInt(0, 24),
            Random.nextInt(0, 60),
            Random.nextInt(0, 60)
        )

    // ==================== NO-SKIPPED-INSTANT PROPERTY ====================
    // The invariant the field-by-field assertions above cannot express: nextExecutionAfter(after)
    // must return the *earliest* matching instant strictly after `after`. A result that matches
    // every field can still be wrong if an earlier matching instant was jumped over — which is
    // exactly what happens when the day search rolls forward and carries the candidate's
    // time-of-day with it (Saturday 11:30 -> Monday 12:00 instead of Monday 08:00).

    /**
     * The strongest property in this file: sweep every second between the base instant and the
     * returned fire time and prove none of them matches the expression.
     *
     * Deliberately generates the *shape* that exhibits roll-forward bugs — multi-valued hour and/or
     * minute fields (ranges, steps, lists) combined with a day-of-week / day-of-month restriction,
     * and a base instant that lands on a **non-matching day** so the search is forced to roll to
     * another day.
     *
     * The verifier ([Schedule.matches]) is an independent field-by-field matcher built from value
     * sets generated alongside the expression text — it never calls back into the production parser
     * or into [CronExpression.nextExecutionAfter], so the code under test cannot vouch for itself.
     */
    @Test
    fun `no matching instant is skipped when the search rolls to another day`() {
        val random = Random(20250704) // fixed seed: failures are reproducible, never flaky

        repeat(GENERATED_CASES) { case ->
            val schedule = randomSchedule(random)
            val base = baseOnNonMatchingDay(random, schedule)

            val cron = CronExpression(schedule.text)
            val next = cron.nextExecutionAfter(base)

            assertTrue(
                next.isAfter(base),
                "case #$case: next ($next) is not strictly after base ($base) for '${schedule.text}'"
            )
            assertTrue(
                schedule.matches(next),
                "case #$case: returned fire $next does not match '${schedule.text}' (base=$base)"
            )

            val skipped = firstMatchBefore(schedule, base, next)
            if (skipped != null) {
                fail(
                    "case #$case: '${schedule.text}' skipped a matching instant.\n" +
                        "  base     = $base (${base.dayOfWeek})\n" +
                        "  returned = $next (${next.dayOfWeek})\n" +
                        "  earliest = $skipped (${skipped.dayOfWeek}) <- should have been returned"
                )
            }
        }
    }

    // ==================== REGRESSION: verified roll-forward failures ====================

    @Test
    fun `business hours on weekdays fires at the start of the next matching day`() {
        val cron = CronExpression("0 0 8-17 * * 1-5")
        val saturday = LocalDateTime.of(2025, 1, 4, 11, 30, 0)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek, "fixture date is not a Saturday")

        val next = cron.nextExecutionAfter(saturday)

        // Monday 08:00 — NOT Monday 12:00 (the Saturday candidate's hour carried forward).
        assertEquals(LocalDateTime.of(2025, 1, 6, 8, 0, 0), next)
    }

    @Test
    fun `quarter-hourly on weekdays fires at midnight of the next matching day`() {
        val cron = CronExpression("0 0/15 * * * 1-5")
        val saturday = LocalDateTime.of(2025, 1, 4, 22, 47, 0)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek, "fixture date is not a Saturday")

        val next = cron.nextExecutionAfter(saturday)

        // Monday 00:00 — NOT Monday 23:00 (the Saturday candidate's hour carried forward).
        assertEquals(LocalDateTime.of(2025, 1, 6, 0, 0, 0), next)
    }

    // ==================== generator + independent oracle ====================

    /**
     * A generated cron field: the text handed to [CronExpression] plus the set of values it stands
     * for, expanded here rather than by the production parser so the oracle stays independent.
     */
    private data class GenField(val text: String, val values: Set<Int>) {
        val unrestricted: Boolean get() = text == "*" || text == "?"
    }

    /**
     * A generated schedule and its own matcher. Day semantics mirror the documented cron contract:
     * day-of-month and day-of-week are OR'd when both are restricted, and an unrestricted field
     * defers entirely to the other one.
     */
    private class Schedule(
        val second: GenField,
        val minute: GenField,
        val hour: GenField,
        val dayOfMonth: GenField,
        val month: GenField,
        val dayOfWeek: GenField
    ) {
        val text: String =
            "${second.text} ${minute.text} ${hour.text} ${dayOfMonth.text} ${month.text} ${dayOfWeek.text}"

        fun dateMatches(date: LocalDate): Boolean {
            if (date.monthValue !in month.values) return false
            val domMatch = date.dayOfMonth in dayOfMonth.values
            val dowMatch = (date.dayOfWeek.value % 7) in dayOfWeek.values // Sunday == 0
            return when {
                dayOfMonth.unrestricted && dayOfWeek.unrestricted -> true
                dayOfMonth.unrestricted -> dowMatch
                dayOfWeek.unrestricted -> domMatch
                else -> domMatch || dowMatch
            }
        }

        fun matches(instant: LocalDateTime): Boolean =
            instant.second in second.values &&
                instant.minute in minute.values &&
                instant.hour in hour.values &&
                dateMatches(instant.toLocalDate())
    }

    /**
     * Walks second-by-second from `base + 1s` and returns the first instant that matches the
     * schedule, or null if none is found before `next` (i.e. `next` really was the earliest).
     *
     * Whole non-matching days are stepped over in one jump — no instant on such a day can match, so
     * the sweep stays exhaustive while remaining fast enough for a unit-test suite. The sweep is
     * additionally capped at [MAX_SWEEP_DAYS]; past that the check degrades to "nothing matches
     * inside the window", which is still sound, just partial.
     */
    private fun firstMatchBefore(
        schedule: Schedule,
        base: LocalDateTime,
        next: LocalDateTime
    ): LocalDateTime? {
        val limit = minOf(next, base.plusDays(MAX_SWEEP_DAYS.toLong()))
        var probe = base.plusSeconds(1).withNano(0)
        while (probe.isBefore(limit)) {
            if (!schedule.dateMatches(probe.toLocalDate())) {
                probe = probe.toLocalDate().plusDays(1).atStartOfDay()
                continue
            }
            if (schedule.matches(probe)) return probe
            probe = probe.plusSeconds(1)
        }
        return null
    }

    private fun randomSchedule(random: Random): Schedule {
        // Always restrict at least one day field, so non-matching days exist to roll forward from.
        val restrictDayOfWeek = random.nextBoolean()
        val dayOfWeek = if (restrictDayOfWeek) RESTRICTED_DAYS_OF_WEEK.random(random) else DAYS_OF_WEEK.random(random)
        val dayOfMonth = if (restrictDayOfWeek) DAYS_OF_MONTH.random(random) else RESTRICTED_DAYS_OF_MONTH.random(random)
        return Schedule(
            second = SECONDS.random(random),
            minute = MINUTES.random(random),
            hour = HOURS.random(random),
            dayOfMonth = dayOfMonth,
            month = ALL_MONTHS,
            dayOfWeek = dayOfWeek
        )
    }

    /**
     * Picks a base instant whose *date* does not match the schedule, so `nextExecutionAfter` has to
     * roll forward to another day (the precondition for the roll-forward bug class). Falls back to
     * the drawn date if the schedule happens to match every day in the search window.
     */
    private fun baseOnNonMatchingDay(random: Random, schedule: Schedule): LocalDateTime {
        var date = LocalDate.of(random.nextInt(2024, 2031), random.nextInt(1, 13), random.nextInt(1, 29))
        var attempts = 0
        while (schedule.dateMatches(date) && attempts < 40) {
            date = date.plusDays(1)
            attempts++
        }
        // Bias towards a late time-of-day: the bug only shows when the carried-over time-of-day is
        // later than the first valid one on the day the search lands on.
        return date.atTime(random.nextInt(6, 24), random.nextInt(0, 60), random.nextInt(0, 60))
    }

    private companion object {
        const val GENERATED_CASES = 200

        /** Long enough for any day-of-week schedule (max gap 7 days) to be verified end to end. */
        const val MAX_SWEEP_DAYS = 8

        val SECONDS = listOf(
            GenField("0", setOf(0)),
            GenField("30", setOf(30)),
            GenField("0/30", setOf(0, 30))
        )

        val MINUTES = listOf(
            GenField("0", setOf(0)),
            GenField("*", (0..59).toSet()),
            GenField("0/15", setOf(0, 15, 30, 45)),
            GenField("*/30", setOf(0, 30)),
            GenField("0,30", setOf(0, 30)),
            GenField("15-45/10", setOf(15, 25, 35, 45)),
            GenField("5,25,45", setOf(5, 25, 45))
        )

        val HOURS = listOf(
            GenField("*", (0..23).toSet()),
            GenField("8-17", (8..17).toSet()),
            GenField("9-17", (9..17).toSet()),
            GenField("0/6", setOf(0, 6, 12, 18)),
            GenField("*/4", setOf(0, 4, 8, 12, 16, 20)),
            GenField("1,5,9,13", setOf(1, 5, 9, 13)),
            GenField("6-22/4", setOf(6, 10, 14, 18, 22)),
            GenField("2,14", setOf(2, 14))
        )

        val RESTRICTED_DAYS_OF_WEEK = listOf(
            GenField("1-5", (1..5).toSet()),
            GenField("0,6", setOf(0, 6)),
            GenField("1", setOf(1)),
            GenField("2,4", setOf(2, 4)),
            GenField("6", setOf(6)),
            GenField("0", setOf(0))
        )

        val DAYS_OF_WEEK = RESTRICTED_DAYS_OF_WEEK + listOf(
            GenField("*", (0..6).toSet()),
            GenField("?", (0..6).toSet())
        )

        val RESTRICTED_DAYS_OF_MONTH = listOf(
            GenField("1,15", setOf(1, 15)),
            GenField("10-20", (10..20).toSet()),
            GenField("15", setOf(15)),
            GenField("1-10/3", setOf(1, 4, 7, 10))
        )

        val DAYS_OF_MONTH = RESTRICTED_DAYS_OF_MONTH + listOf(
            GenField("*", (1..31).toSet()),
            GenField("?", (1..31).toSet())
        )

        val ALL_MONTHS = GenField("*", (1..12).toSet())
    }
}
