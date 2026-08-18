package io.github.darkryh.katalyst.migrations.runner

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ordering is the one thing a migration runner must never get wrong.
 *
 * Applying `V10` before `V2` does not fail loudly — it runs a later schema change against an
 * earlier schema, and whether that corrupts the database or merely errors depends entirely on what
 * the migrations happen to contain. There was no test on this function at all, which is how the
 * defect survived: `KatalystMigration.version` parses a *leading* numeric prefix, so every
 * Flyway-style id (`V2__add_users`) yields `Long.MAX_VALUE`, all of them tie, and the tie-break
 * compared `"V10"` against `"V2"` character by character — putting V10 first.
 *
 * The framework recommends exactly that naming: `SchemaDiffService` documents
 * `V2__add_primary_key.sql` as the convention.
 */
class MigrationOrderingPropertyTest {

    private fun sorted(vararg ids: String): List<String> =
        ids.sortedWith { a, b -> MigrationRunner.compareMigrationKeys(a, b) }

    /* ── The regression ──────────────────────────────────────────────────────────────────── */

    @Test
    fun `Flyway-style ids apply in numeric order, not lexicographic`() {
        assertEquals(
            listOf("V1__init", "V2__add_users", "V9__x", "V10__add_index", "V20__y"),
            sorted("V10__add_index", "V2__add_users", "V20__y", "V1__init", "V9__x"),
        )
    }

    @Test
    fun `the double-digit boundary is ordered correctly for any prefix letter`() {
        listOf("V", "v", "R", "M", "").forEach { prefix ->
            assertEquals(
                listOf("${prefix}2__a", "${prefix}10__a"),
                sorted("${prefix}10__a", "${prefix}2__a"),
                "prefix '$prefix' ordered wrongly",
            )
        }
    }

    @Test
    fun `numeric-prefix ids still order numerically`() {
        assertEquals(
            listOf("001_init", "002_users", "010_index"),
            sorted("010_index", "002_users", "001_init"),
        )
    }

    @Test
    fun `leading zeros do not change magnitude`() {
        assertEquals(0, MigrationRunner.compareMigrationKeys("V007__a", "V7__a"))
        assertEquals(0, MigrationRunner.compareMigrationKeys("0001", "1"))
    }

    @Test
    fun `very large numeric runs beyond Long range still order by magnitude`() {
        // A timestamp-style id can exceed Long. Digit-run comparison must not silently overflow.
        val huge = "V99999999999999999999999__a"
        val small = "V2__a"
        assertTrue(
            MigrationRunner.compareMigrationKeys(small, huge) < 0,
            "V2 must precede a 23-digit version",
        )
    }

    /* ── Universal properties ────────────────────────────────────────────────────────────── */

    private fun randomId(random: Random): String {
        val prefix = listOf("V", "v", "R", "").random(random)
        val number = random.nextInt(0, 200)
        val name = listOf("init", "users", "index", "orders", "add_column").random(random)
        val separator = listOf("__", "_", "-", ".").random(random)
        return "$prefix$number$separator$name"
    }

    @Test
    fun `the comparator is a total order`() {
        val random = Random(20260818)
        repeat(1_000) {
            val a = randomId(random)
            val b = randomId(random)
            val c = randomId(random)

            val ab = MigrationRunner.compareMigrationKeys(a, b)
            val ba = MigrationRunner.compareMigrationKeys(b, a)

            // Antisymmetry: comparing in reverse must invert the sign.
            assertEquals(
                ab.coerceIn(-1, 1),
                -ba.coerceIn(-1, 1),
                "antisymmetry broken for '$a' vs '$b'",
            )

            // Transitivity: a <= b <= c implies a <= c.
            val bc = MigrationRunner.compareMigrationKeys(b, c)
            if (ab <= 0 && bc <= 0) {
                assertTrue(
                    MigrationRunner.compareMigrationKeys(a, c) <= 0,
                    "transitivity broken: '$a' <= '$b' <= '$c' but not '$a' <= '$c'",
                )
            }
        }
    }

    @Test
    fun `a migration id is equal to itself`() {
        val random = Random(4242)
        repeat(1_000) {
            val id = randomId(random)
            assertEquals(0, MigrationRunner.compareMigrationKeys(id, id), "'$id' not equal to itself")
        }
    }

    @Test
    fun `sorting is stable and independent of input order`() {
        // The property the runner actually depends on: whatever order discovery hands migrations
        // over in — and discovery order is hash-based, so it genuinely varies — the applied order
        // must be identical.
        val random = Random(777)
        repeat(200) {
            val ids = (1..12).map { randomId(random) }.distinct()
            val once = ids.sortedWith { a, b -> MigrationRunner.compareMigrationKeys(a, b) }
            val again = ids.shuffled(random).sortedWith { a, b -> MigrationRunner.compareMigrationKeys(a, b) }

            assertEquals(once, again, "sort order depended on input order for $ids")
        }
    }

    @Test
    fun `numeric ordering holds across every adjacent pair in a generated sequence`() {
        // Directly asserts the invariant that matters: for versions 1..150 under a Flyway-style
        // prefix, the sorted sequence is ascending by number. The old implementation failed this
        // as soon as the sequence crossed 9 -> 10.
        val ids = (1..150).map { "V${it}__migration" }
        val shuffled = ids.shuffled(Random(11))

        val result = shuffled.sortedWith { a, b -> MigrationRunner.compareMigrationKeys(a, b) }

        assertEquals(ids, result, "generated sequence did not sort ascending")
    }

    @Test
    fun `mixed separators do not change relative order`() {
        assertEquals(
            listOf("V2.a", "V10.a"),
            sorted("V10.a", "V2.a"),
        )
        assertEquals(
            listOf("V2-a", "V10-a"),
            sorted("V10-a", "V2-a"),
        )
    }
}
