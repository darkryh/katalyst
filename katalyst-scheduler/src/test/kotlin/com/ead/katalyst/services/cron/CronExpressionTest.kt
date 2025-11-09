package com.ead.katalyst.services.cron

import com.ead.katalyst.scheduler.cron.CronExpression
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Comprehensive test suite for CronExpression.
 * Tests all supported cron patterns, edge cases, and error conditions.
 */
class CronExpressionTest {

    // ==================== Basic Wildcard Tests ====================

    @Test
    fun `wildcard expression matches any valid time`() {
        val cron = CronExpression("* * * * * *")
        val start = LocalDateTime.of(2025, 1, 1, 12, 30, 45)
        val next = cron.nextExecutionAfter(start)
        // Should execute at the next second
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 30, 46), next)
    }

    // ==================== Second Field Tests ====================

    @Test
    fun `every 2 seconds expression works correctly`() {
        val cron = CronExpression("*/2 * * * * *")
        val start = LocalDateTime.of(2025, 1, 1, 12, 30, 45)
        val next = cron.nextExecutionAfter(start)
        // 45 seconds is odd, next even second is 46
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 30, 46), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 30, 48), next2)

        val next3 = cron.nextExecutionAfter(next2)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 30, 50), next3)
    }

    @Test
    fun `every 5 seconds expression works correctly`() {
        val cron = CronExpression("*/5 * * * * *")
        val start = LocalDateTime.of(2025, 1, 1, 12, 30, 44)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 30, 45), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 30, 50), next2)

        val next3 = cron.nextExecutionAfter(next2)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 30, 55), next3)

        val next4 = cron.nextExecutionAfter(next3)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 31, 0), next4)
    }

    @Test
    fun `specific second expression works correctly`() {
        val cron = CronExpression("15 * * * * *")
        val start = LocalDateTime.of(2025, 1, 1, 12, 30, 10)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 30, 15), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 31, 15), next2)
    }

    // ==================== Minute Field Tests ====================

    @Test
    fun `every 2 minutes expression works correctly`() {
        val cron = CronExpression("0 */2 * * * *")
        val start = LocalDateTime.of(2025, 1, 1, 12, 31, 0)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 32, 0), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 34, 0), next2)
    }

    @Test
    fun `every 15 minutes expression works correctly`() {
        val cron = CronExpression("0 */15 * * * *")
        val start = LocalDateTime.of(2025, 1, 1, 12, 14, 0)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 15, 0), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 30, 0), next2)

        val next3 = cron.nextExecutionAfter(next2)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 45, 0), next3)

        val next4 = cron.nextExecutionAfter(next3)
        assertEquals(LocalDateTime.of(2025, 1, 1, 13, 0, 0), next4)
    }

    // ==================== Hour Field Tests ====================

    @Test
    fun `every 6 hours expression works correctly`() {
        val cron = CronExpression("0 0 */6 * * *")
        val start = LocalDateTime.of(2025, 1, 1, 5, 59, 59)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 1, 6, 0, 0), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 0, 0), next2)

        val next3 = cron.nextExecutionAfter(next2)
        assertEquals(LocalDateTime.of(2025, 1, 1, 18, 0, 0), next3)

        val next4 = cron.nextExecutionAfter(next3)
        assertEquals(LocalDateTime.of(2025, 1, 2, 0, 0, 0), next4)
    }

    // ==================== Range Tests ====================

    @Test
    fun `range expression works correctly`() {
        val cron = CronExpression("0 0 9-17 * * *")
        val start = LocalDateTime.of(2025, 1, 1, 8, 59, 59)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 1, 9, 0, 0), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 1, 1, 10, 0, 0), next2)

        // After 17:00, should move to next day at 9:00
        val start2 = LocalDateTime.of(2025, 1, 1, 17, 30, 0)
        val next3 = cron.nextExecutionAfter(start2)
        assertEquals(LocalDateTime.of(2025, 1, 2, 9, 0, 0), next3)
    }

    // ==================== List Tests ====================

    @Test
    fun `list expression works correctly`() {
        val cron = CronExpression("0 0 9,12,15,18 * * *")
        val start = LocalDateTime.of(2025, 1, 1, 8, 59, 59)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 1, 9, 0, 0), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 0, 0), next2)

        val next3 = cron.nextExecutionAfter(next2)
        assertEquals(LocalDateTime.of(2025, 1, 1, 15, 0, 0), next3)

        val next4 = cron.nextExecutionAfter(next3)
        assertEquals(LocalDateTime.of(2025, 1, 1, 18, 0, 0), next4)

        val next5 = cron.nextExecutionAfter(next4)
        assertEquals(LocalDateTime.of(2025, 1, 2, 9, 0, 0), next5)
    }

    // ==================== Month Field Tests ====================

    @Test
    fun `month range works correctly`() {
        val cron = CronExpression("0 0 0 1 3-5 *")
        val start = LocalDateTime.of(2025, 2, 28, 23, 59, 59)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 3, 1, 0, 0, 0), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 4, 1, 0, 0, 0), next2)

        val next3 = cron.nextExecutionAfter(next2)
        assertEquals(LocalDateTime.of(2025, 5, 1, 0, 0, 0), next3)

        val next4 = cron.nextExecutionAfter(next3)
        assertEquals(LocalDateTime.of(2026, 3, 1, 0, 0, 0), next4)
    }

    // ==================== Question Mark (?) Tests ====================

    @Test
    fun `question mark in day-of-month allows day-of-week to match`() {
        // Every Monday (day 1), month and day-of-month don't matter (?)
        val cron = CronExpression("0 0 0 ? * 1")
        val start = LocalDateTime.of(2025, 1, 4, 23, 59, 59) // Saturday
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 6, 0, 0, 0), next) // Monday

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 1, 13, 0, 0, 0), next2) // Next Monday
    }

    @Test
    fun `question mark in day-of-week allows day-of-month to match`() {
        // 15th of every month, day-of-week doesn't matter (?)
        val cron = CronExpression("0 0 0 15 * ?")
        val start = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 15, 0, 0, 0), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 2, 15, 0, 0, 0), next2)
    }

    // ==================== Combined Field Tests ====================

    @Test
    fun `complex expression with multiple fields`() {
        // Every 15 minutes during business hours (9-17) on weekdays
        val cron = CronExpression("0 */15 9-17 * * 1-5")
        val start = LocalDateTime.of(2025, 1, 6, 8, 59, 59) // Monday 8:59
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 6, 9, 0, 0), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 1, 6, 9, 15, 0), next2)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `leap year handling`() {
        val cron = CronExpression("0 0 0 29 2 *")
        val start = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
        val next = cron.nextExecutionAfter(start)
        // 2024 is a leap year
        assertEquals(LocalDateTime.of(2024, 2, 29, 0, 0, 0), next)

        // 2025 is not a leap year, so next execution is 2028
        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2028, 2, 29, 0, 0, 0), next2)
    }

    @Test
    fun `month boundary - 31st of month`() {
        val cron = CronExpression("0 0 0 31 * *")
        val start = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 31, 0, 0, 0), next)

        // February doesn't have 31 days, so skip to March
        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 3, 31, 0, 0, 0), next2)
    }

    @Test
    fun `year boundary`() {
        val cron = CronExpression("0 0 0 1 1 *")
        val start = LocalDateTime.of(2024, 12, 31, 23, 59, 59)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0, 0), next)
    }

    // ==================== Error Cases ====================

    @Test
    fun `invalid number of fields throws error`() {
        assertFailsWith<IllegalArgumentException> {
            CronExpression("* * * *")
        }

        assertFailsWith<IllegalArgumentException> {
            CronExpression("* * * * * * *")
        }
    }

    @Test
    fun `invalid second value throws error`() {
        assertFailsWith<IllegalArgumentException> {
            CronExpression("60 * * * * *")
        }
    }

    @Test
    fun `invalid minute value throws error`() {
        assertFailsWith<IllegalArgumentException> {
            CronExpression("* 60 * * * *")
        }
    }

    @Test
    fun `invalid hour value throws error`() {
        assertFailsWith<IllegalArgumentException> {
            CronExpression("* * 24 * * *")
        }
    }

    @Test
    fun `invalid day of month value throws error`() {
        assertFailsWith<IllegalArgumentException> {
            CronExpression("* * * 32 * *")
        }
    }

    @Test
    fun `invalid month value throws error`() {
        assertFailsWith<IllegalArgumentException> {
            CronExpression("* * * * 13 *")
        }
    }

    @Test
    fun `invalid day of week value throws error`() {
        assertFailsWith<IllegalArgumentException> {
            CronExpression("* * * * * 7")
        }
    }

    @Test
    fun `question mark not allowed in non-day fields throws error`() {
        assertFailsWith<IllegalArgumentException> {
            CronExpression("? * * * * *")
        }

        assertFailsWith<IllegalArgumentException> {
            CronExpression("* ? * * * *")
        }
    }

    @Test
    fun `both day fields as question mark throws error`() {
        assertFailsWith<IllegalArgumentException> {
            CronExpression("0 0 0 ? * ?")
        }
    }

    @Test
    fun `invalid step value throws error`() {
        assertFailsWith<IllegalArgumentException> {
            CronExpression("*/0 * * * * *")
        }

        assertFailsWith<IllegalArgumentException> {
            CronExpression("*/-1 * * * * *")
        }
    }

    @Test
    fun `invalid range throws error`() {
        assertFailsWith<IllegalArgumentException> {
            CronExpression("10-5 * * * * *")
        }
    }

    @Test
    fun `step value producing no valid values throws error`() {
        assertFailsWith<IllegalArgumentException> {
            CronExpression("59/2 * * * * *")
        }
    }

    // ==================== Real-world Examples ====================

    @Test
    fun `every minute at 00 seconds`() {
        val cron = CronExpression("0 * * * * *")
        val start = LocalDateTime.of(2025, 1, 1, 12, 30, 45)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 31, 0), next)
    }

    @Test
    fun `every hour at 15 minutes 30 seconds`() {
        val cron = CronExpression("30 15 * * * *")
        val start = LocalDateTime.of(2025, 1, 1, 12, 14, 0)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 15, 30), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 1, 1, 13, 15, 30), next2)
    }

    @Test
    fun `daily at midnight`() {
        val cron = CronExpression("0 0 0 * * *")
        val start = LocalDateTime.of(2025, 1, 1, 12, 30, 45)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 2, 0, 0, 0), next)
    }

    @Test
    fun `twice daily - morning and evening`() {
        val cron = CronExpression("0 0 8,20 * * *")
        val start = LocalDateTime.of(2025, 1, 1, 7, 59, 59)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 1, 1, 8, 0, 0), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 1, 1, 20, 0, 0), next2)

        val next3 = cron.nextExecutionAfter(next2)
        assertEquals(LocalDateTime.of(2025, 1, 2, 8, 0, 0), next3)
    }

    @Test
    fun `quarterly - first day of Jan, Apr, Jul, Oct`() {
        val cron = CronExpression("0 0 0 1 1,4,7,10 *")
        val start = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
        val next = cron.nextExecutionAfter(start)
        assertEquals(LocalDateTime.of(2025, 4, 1, 0, 0, 0), next)

        val next2 = cron.nextExecutionAfter(next)
        assertEquals(LocalDateTime.of(2025, 7, 1, 0, 0, 0), next2)

        val next3 = cron.nextExecutionAfter(next2)
        assertEquals(LocalDateTime.of(2025, 10, 1, 0, 0, 0), next3)

        val next4 = cron.nextExecutionAfter(next3)
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0, 0), next4)
    }

    @Test
    fun `zero step value like 0 slash 2 is valid`() {
        val cron = CronExpression("0/2 * * * * ?")
        val start = LocalDateTime.of(2025, 1, 1, 0, 0, 1)
        val next = cron.nextExecutionAfter(start)
        // Should be 0 seconds (every 2 seconds: 0, 2, 4, ...)
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0, 2), next)
    }
}
