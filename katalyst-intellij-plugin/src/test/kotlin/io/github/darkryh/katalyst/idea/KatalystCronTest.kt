package io.github.darkryh.katalyst.idea

import io.github.darkryh.katalyst.idea.scheduler.KatalystCron
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden-table parity + readability tests for the vendored cron mirror. The accept/reject column
 * pins parity with the canonical `CronValidator`/`CronField` grammar (6 fields, Quartz `?`, `*`,
 * `/`, `,`, `-`, day-of-week `0=Sun`); the description column pins the human-readable rendering.
 */
class KatalystCronTest {

    // --- validity (parity with CronValidator) ---

    private val valid = listOf(
        "0 0 2 * * ?",        // 2am daily
        "0 0/15 * * * ?",     // every 15 minutes
        "*/30 * * * * ?",     // every 30 seconds
        "0 0 * * * ?",        // hourly
        "0 0 9 ? * 1",        // Mondays 9am
        "0 0 0 1 * ?",        // first of the month
        "0 0 12 1-5 * ?",     // days 1..5 at noon
        "0 0,30 * * * ?",     // on the hour and half-hour
        "0 0 8-17/2 * * ?",   // every 2 hours within 8..17
    )

    private val invalid = listOf(
        "",                   // empty
        "0 0 2 * *",          // 5 fields
        "0 0 25 * * ?",       // hour out of range
        "60 0 2 * * ?",       // second out of range
        "0 0 2 ? * ?",        // both day fields unrestricted
        "0 0 2 * 13 ?",       // month out of range
        "0 0 2 * * 7",        // day-of-week out of range (0..6)
        "0 0 2 * * abc",      // non-numeric
        "0 0 2/0 * * ?",      // zero step
    )

    @Test
    fun acceptsValidExpressions() {
        valid.forEach { assertTrue("expected valid: '$it' (errors=${KatalystCron.validate(it)})", KatalystCron.isValid(it)) }
    }

    @Test
    fun rejectsInvalidExpressions() {
        invalid.forEach { assertFalse("expected invalid: '$it'", KatalystCron.isValid(it)) }
    }

    @Test
    fun reportsFieldPreciseError() {
        val errors = KatalystCron.validate("0 0 25 * * ?")
        assertTrue("error should name the hour field: $errors", errors.any { it.contains("hour") })
    }

    // --- description ---

    @Test
    fun describesSpecificTimesWith12HourClock() {
        assertEquals("Every day at 2:00 AM", KatalystCron.describe("0 0 2 * * ?"))
        assertEquals("Every day at 2:00 PM", KatalystCron.describe("0 0 14 * * ?"))
        assertEquals("Every day at 12:00 AM", KatalystCron.describe("0 0 0 * * ?"))
        assertEquals("Every day at 12:00 PM", KatalystCron.describe("0 0 12 * * ?"))
        assertEquals("Every day at 6:30 PM", KatalystCron.describe("0 30 18 * * ?"))
        assertEquals("Every day at 9:00:30 AM", KatalystCron.describe("30 0 9 * * ?"))
    }

    @Test
    fun describesRecurringFrequencies() {
        assertEquals("Every 15 minutes", KatalystCron.describe("0 0/15 * * * ?"))
        assertEquals("Every 30 seconds", KatalystCron.describe("*/30 * * * * ?"))
        assertEquals("Every minute", KatalystCron.describe("0 * * * * ?"))
        assertEquals("Every hour, on the hour", KatalystCron.describe("0 0 * * * ?"))
        assertEquals("At 5 minutes past every hour", KatalystCron.describe("0 5 * * * ?"))
        assertEquals(
            "Every 15 minutes, between 9:00 AM and 5:00 PM",
            KatalystCron.describe("0 0/15 9-17 * * ?"),
        )
    }

    @Test
    fun describesDayOfWeekRuns() {
        assertEquals("Every Monday at 9:00 AM", KatalystCron.describe("0 0 9 ? * 1"))
        assertEquals("On weekdays at 9:30 AM", KatalystCron.describe("0 30 9 ? * 1-5"))
        assertEquals("On weekends at 9:00 AM", KatalystCron.describe("0 0 9 ? * 0,6"))
        assertEquals(
            "Every Monday, Wednesday and Friday at 9:00 AM",
            KatalystCron.describe("0 0 9 ? * 1,3,5"),
        )
    }

    @Test
    fun describesMonthlyAndYearlyDates() {
        assertEquals("On the 1st of every month at 12:00 AM", KatalystCron.describe("0 0 0 1 * ?"))
        assertEquals("On the 15th of every month at 8:00 AM", KatalystCron.describe("0 0 8 15 * ?"))
        assertEquals("On the 1st of every month at 12:00 PM", KatalystCron.describe("0 0 12 1 * ?"))
        assertEquals("On January 1st at 12:00 AM", KatalystCron.describe("0 0 0 1 1 ?"))
    }

    @Test
    fun describeReturnsNullForInvalid() {
        assertNull(KatalystCron.describe("nonsense"))
        assertNull(KatalystCron.describe("0 0 25 * * ?"))
    }
}
