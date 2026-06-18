package io.github.darkryh.katalyst.scheduler.cron

import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based (dependency-free generative) tests for [CronExpression.nextExecutionAfter].
 *
 * Rather than a few fixed expressions, these generate many random daily schedules and random base
 * times and assert the universal invariants hold for all of them:
 *  - the next fire is strictly after the input instant,
 *  - it matches every specified field (second/minute/hour),
 *  - a daily schedule's next fire is within ~24h.
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
}
