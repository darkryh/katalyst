package io.github.darkryh.katalyst.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Path

class MemoryValidationRunnerTest {
    @Test
    fun `smoke mode has deterministic bounded settings`() {
        val config = parseArgs(
            arrayOf(
                "--mode=smoke",
                "--sample-dir=${Path.of("build/sample")}",
                "--heap-mb=192",
                "--seed=7",
            )
        )

        assertEquals(30, config.durationSeconds)
        assertEquals(2, config.concurrency)
        assertEquals(192, config.heapMb)
        assertEquals(7, config.seed)
    }
}
