package io.github.darkryh.katalyst.services.cron

import io.github.darkryh.katalyst.scheduler.cron.CronValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Comprehensive test suite for CronValidator.
 * Tests validation of all supported cron patterns and error cases.
 */
class CronValidatorTest {

    // ==================== Valid Expressions ====================

    @Test
    fun `wildcard expression is valid`() {
        assertTrue(CronValidator.isValid("* * * * * *"))
        assertEquals(emptyList(), CronValidator.validate("* * * * * *"))
    }

    @Test
    fun `valid single value expression`() {
        assertTrue(CronValidator.isValid("15 30 12 1 6 3"))
        assertEquals(emptyList(), CronValidator.validate("15 30 12 1 6 3"))
    }

    @Test
    fun `valid range expression`() {
        assertTrue(CronValidator.isValid("0-30 * * * * *"))
        assertEquals(emptyList(), CronValidator.validate("0-30 * * * * *"))
    }

    @Test
    fun `valid list expression`() {
        assertTrue(CronValidator.isValid("1,15,30,45 * * * * *"))
        assertEquals(emptyList(), CronValidator.validate("1,15,30,45 * * * * *"))
    }

    @Test
    fun `valid step value expression - every 2 seconds`() {
        assertTrue(CronValidator.isValid("*/2 * * * * *"))
        assertEquals(emptyList(), CronValidator.validate("*/2 * * * * *"))
    }

    @Test
    fun `valid step value expression - every 5 minutes`() {
        assertTrue(CronValidator.isValid("0 */5 * * * *"))
        assertEquals(emptyList(), CronValidator.validate("0 */5 * * * *"))
    }

    @Test
    fun `valid step value with range - 0-30 every 5 seconds`() {
        assertTrue(CronValidator.isValid("0-30/5 * * * * *"))
        assertEquals(emptyList(), CronValidator.validate("0-30/5 * * * * *"))
    }

    @Test
    fun `valid question mark in day-of-month`() {
        assertTrue(CronValidator.isValid("0 0 0 ? * 1"))
        assertEquals(emptyList(), CronValidator.validate("0 0 0 ? * 1"))
    }

    @Test
    fun `valid question mark in day-of-week`() {
        assertTrue(CronValidator.isValid("0 0 0 15 * ?"))
        assertEquals(emptyList(), CronValidator.validate("0 0 0 15 * ?"))
    }

    @Test
    fun `valid complex expression`() {
        assertTrue(CronValidator.isValid("0 */15 9-17 * * 1-5"))
        assertEquals(emptyList(), CronValidator.validate("0 */15 9-17 * * 1-5"))
    }

    @Test
    fun `valid real-world examples`() {
        assertTrue(CronValidator.isValid("0 * * * * *"))  // Every minute
        assertTrue(CronValidator.isValid("30 15 * * * *")) // Every hour at 15:30
        assertTrue(CronValidator.isValid("0 0 0 * * *"))   // Daily at midnight
        assertTrue(CronValidator.isValid("0 0 8,20 * * *")) // Twice daily
        assertTrue(CronValidator.isValid("0 9 0 * * 1-5")) // Every weekday at 9:00
    }

    // ==================== Invalid Field Count ====================

    @Test
    fun `empty expression is invalid`() {
        assertFalse(CronValidator.isValid(""))
        val errors = CronValidator.validate("")
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("empty"))
    }

    @Test
    fun `blank expression is invalid`() {
        assertFalse(CronValidator.isValid("   "))
        val errors = CronValidator.validate("   ")
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("empty"))
    }

    @Test
    fun `too few fields is invalid`() {
        assertFalse(CronValidator.isValid("* * * * *"))
        val errors = CronValidator.validate("* * * * *")
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("exactly 6 fields"))
    }

    @Test
    fun `too many fields is invalid`() {
        assertFalse(CronValidator.isValid("* * * * * * *"))
        val errors = CronValidator.validate("* * * * * * *")
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("exactly 6 fields"))
    }

    // ==================== Invalid Second Field ====================

    @Test
    fun `second value exceeds max is invalid`() {
        assertFalse(CronValidator.isValid("60 * * * * *"))
        val errors = CronValidator.validate("60 * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    @Test
    fun `second value below min is invalid`() {
        assertFalse(CronValidator.isValid("-1 * * * * *"))
        val errors = CronValidator.validate("-1 * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    @Test
    fun `second range exceeding max is invalid`() {
        assertFalse(CronValidator.isValid("30-65 * * * * *"))
        val errors = CronValidator.validate("30-65 * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    // ==================== Invalid Minute Field ====================

    @Test
    fun `minute value exceeds max is invalid`() {
        assertFalse(CronValidator.isValid("* 60 * * * *"))
        val errors = CronValidator.validate("* 60 * * * *")
        assertTrue(errors.any { it.contains("minute") })
    }

    @Test
    fun `minute range exceeding max is invalid`() {
        assertFalse(CronValidator.isValid("* 30-65 * * * *"))
        val errors = CronValidator.validate("* 30-65 * * * *")
        assertTrue(errors.any { it.contains("minute") })
    }

    // ==================== Invalid Hour Field ====================

    @Test
    fun `hour value exceeds max is invalid`() {
        assertFalse(CronValidator.isValid("* * 24 * * *"))
        val errors = CronValidator.validate("* * 24 * * *")
        assertTrue(errors.any { it.contains("hour") })
    }

    @Test
    fun `hour range exceeding max is invalid`() {
        assertFalse(CronValidator.isValid("* * 10-25 * * *"))
        val errors = CronValidator.validate("* * 10-25 * * *")
        assertTrue(errors.any { it.contains("hour") })
    }

    // ==================== Invalid Day of Month Field ====================

    @Test
    fun `day of month value exceeds max is invalid`() {
        assertFalse(CronValidator.isValid("* * * 32 * *"))
        val errors = CronValidator.validate("* * * 32 * *")
        assertTrue(errors.any { it.contains("day of month") })
    }

    @Test
    fun `day of month value below min is invalid`() {
        assertFalse(CronValidator.isValid("* * * 0 * *"))
        val errors = CronValidator.validate("* * * 0 * *")
        assertTrue(errors.any { it.contains("day of month") })
    }

    @Test
    fun `day of month range exceeding max is invalid`() {
        assertFalse(CronValidator.isValid("* * * 15-32 * *"))
        val errors = CronValidator.validate("* * * 15-32 * *")
        assertTrue(errors.any { it.contains("day of month") })
    }

    // ==================== Invalid Month Field ====================

    @Test
    fun `month value exceeds max is invalid`() {
        assertFalse(CronValidator.isValid("* * * * 13 *"))
        val errors = CronValidator.validate("* * * * 13 *")
        assertTrue(errors.any { it.contains("month") })
    }

    @Test
    fun `month value below min is invalid`() {
        assertFalse(CronValidator.isValid("* * * * 0 *"))
        val errors = CronValidator.validate("* * * * 0 *")
        assertTrue(errors.any { it.contains("month") })
    }

    @Test
    fun `month range exceeding max is invalid`() {
        assertFalse(CronValidator.isValid("* * * * 6-13 *"))
        val errors = CronValidator.validate("* * * * 6-13 *")
        assertTrue(errors.any { it.contains("month") })
    }

    // ==================== Invalid Day of Week Field ====================

    @Test
    fun `day of week value exceeds max is invalid`() {
        assertFalse(CronValidator.isValid("* * * * * 7"))
        val errors = CronValidator.validate("* * * * * 7")
        assertTrue(errors.any { it.contains("day of week") })
    }

    @Test
    fun `day of week range exceeding max is invalid`() {
        assertFalse(CronValidator.isValid("* * * * * 3-7"))
        val errors = CronValidator.validate("* * * * * 3-7")
        assertTrue(errors.any { it.contains("day of week") })
    }

    // ==================== Invalid Question Mark Usage ====================

    @Test
    fun `question mark in second field is invalid`() {
        assertFalse(CronValidator.isValid("? * * * * *"))
        val errors = CronValidator.validate("? * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    @Test
    fun `question mark in minute field is invalid`() {
        assertFalse(CronValidator.isValid("* ? * * * *"))
        val errors = CronValidator.validate("* ? * * * *")
        assertTrue(errors.any { it.contains("minute") })
    }

    @Test
    fun `question mark in hour field is invalid`() {
        assertFalse(CronValidator.isValid("* * ? * * *"))
        val errors = CronValidator.validate("* * ? * * *")
        assertTrue(errors.any { it.contains("hour") })
    }

    @Test
    fun `question mark in month field is invalid`() {
        assertFalse(CronValidator.isValid("* * * * ? *"))
        val errors = CronValidator.validate("* * * * ? *")
        assertTrue(errors.any { it.contains("month") })
    }

    @Test
    fun `both day-of-month and day-of-week as question mark is invalid`() {
        assertFalse(CronValidator.isValid("0 0 0 ? * ?"))
        val errors = CronValidator.validate("0 0 0 ? * ?")
        assertTrue(errors.any { it.contains("at least one") || it.contains("restricted") })
    }

    // ==================== Invalid Step Values ====================

    @Test
    fun `step value of 0 is invalid`() {
        assertFalse(CronValidator.isValid("*/0 * * * * *"))
        val errors = CronValidator.validate("*/0 * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    @Test
    fun `step value of -1 is invalid`() {
        assertFalse(CronValidator.isValid("*/-1 * * * * *"))
        val errors = CronValidator.validate("*/-1 * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    @Test
    fun `non-numeric step value is invalid`() {
        assertFalse(CronValidator.isValid("*/abc * * * * *"))
        val errors = CronValidator.validate("*/abc * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    @Test
    fun `step value producing no valid values is invalid`() {
        assertFalse(CronValidator.isValid("59/2 * * * * *"))
        val errors = CronValidator.validate("59/2 * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    // ==================== Invalid Range ====================

    @Test
    fun `range with start exceeding end is invalid`() {
        assertFalse(CronValidator.isValid("30-10 * * * * *"))
        val errors = CronValidator.validate("30-10 * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    @Test
    fun `range with non-numeric values is invalid`() {
        assertFalse(CronValidator.isValid("10-abc * * * * *"))
        val errors = CronValidator.validate("10-abc * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    @Test
    fun `range with too many parts is invalid`() {
        assertFalse(CronValidator.isValid("10-20-30 * * * * *"))
        val errors = CronValidator.validate("10-20-30 * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    // ==================== Invalid Lists ====================

    @Test
    fun `list with non-numeric values is invalid`() {
        assertFalse(CronValidator.isValid("1,abc,5 * * * * *"))
        val errors = CronValidator.validate("1,abc,5 * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    @Test
    fun `list with out-of-range values is invalid`() {
        assertFalse(CronValidator.isValid("1,30,65 * * * * *"))
        val errors = CronValidator.validate("1,30,65 * * * * *")
        assertTrue(errors.any { it.contains("second") })
    }

    @Test
    fun `empty list is invalid`() {
        assertFalse(CronValidator.isValid("* * * * * ,,,"))
        val errors = CronValidator.validate("* * * * * ,,,")
        assertTrue(errors.any { it.contains("day of week") })
    }

    // ==================== Complex Invalid Expressions ====================

    @Test
    fun `multiple invalid fields reports all errors`() {
        val errors = CronValidator.validate("60 60 24 32 13 7")
        assertEquals(6, errors.size)
        assertTrue(errors.any { it.contains("second") })
        assertTrue(errors.any { it.contains("minute") })
        assertTrue(errors.any { it.contains("hour") })
        assertTrue(errors.any { it.contains("day of month") })
        assertTrue(errors.any { it.contains("month") })
        assertTrue(errors.any { it.contains("day of week") })
    }
}
