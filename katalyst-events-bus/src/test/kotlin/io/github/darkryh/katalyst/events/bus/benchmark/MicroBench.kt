package io.github.darkryh.katalyst.events.bus.benchmark

import kotlin.system.measureNanoTime

/**
 * Minimal, dependency-free microbenchmark harness applying real measurement discipline:
 * a warmup phase (so JIT compilation/class-loading is excluded) followed by several measured
 * iterations, reporting the **median** (robust to GC/outlier spikes) rather than a single shot.
 *
 * This exists so `@Tag("benchmark")` suites measure something meaningful offline. It is **not** a
 * replacement for JMH — Phase 4 of TESTING_STRATEGY.md migrates these to a real JMH/kotlinx-benchmark
 * source set (forks, blackholes, statistically rigorous output). Treat results as a regression
 * signal, never a hard pass/fail gate.
 */
object MicroBench {

    data class Result(val name: String, val medianMs: Double, val minMs: Double, val maxMs: Double) {
        override fun toString(): String =
            "%s: median=%.3fms min=%.3fms max=%.3fms".format(name, medianMs, minMs, maxMs)
    }

    /**
     * Runs [block] [warmup] times (discarded), then [iterations] measured times, returning timing
     * statistics in milliseconds.
     */
    inline fun measure(
        name: String,
        warmup: Int = 5,
        iterations: Int = 20,
        block: () -> Unit
    ): Result {
        require(iterations > 0) { "iterations must be > 0" }
        repeat(warmup) { block() }
        val samplesMs = DoubleArray(iterations) { measureNanoTime { block() } / 1_000_000.0 }
        samplesMs.sort()
        val median = samplesMs[iterations / 2]
        val result = Result(name, median, samplesMs.first(), samplesMs.last())
        println(result) // surfaced in test output / CI logs as a tracking signal
        return result
    }
}
