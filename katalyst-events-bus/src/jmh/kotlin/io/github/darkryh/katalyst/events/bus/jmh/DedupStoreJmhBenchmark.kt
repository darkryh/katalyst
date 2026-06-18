package io.github.darkryh.katalyst.events.bus.jmh

import io.github.darkryh.katalyst.events.bus.deduplication.InMemoryEventDeduplicationStore
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/**
 * Real JMH microbenchmarks for the dedup-store hot paths (Phase 4). Run with
 * `./gradlew :katalyst-events-bus:jmh`. Unlike the test-tagged `MicroBench` smoke benchmarks, JMH
 * provides forked JVMs, warmup/measurement separation and statistically rigorous output — use these
 * numbers for tracking regressions over time.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class DedupStoreJmhBenchmark {

    private lateinit var store: InMemoryEventDeduplicationStore
    private var counter = 0L

    @Setup(Level.Iteration)
    fun setup() {
        store = InMemoryEventDeduplicationStore(maxEntries = 2_000_000)
        counter = 0
        // Pre-populate for the lookup benchmark.
        runBlocking { repeat(10_000) { i -> store.markAsPublished("seed-$i") } }
    }

    @Benchmark
    fun markAsPublished(): Unit = runBlocking {
        store.markAsPublished("e-${counter++}")
    }

    @Benchmark
    fun isEventPublishedHit(): Boolean = runBlocking {
        store.isEventPublished("seed-${counter++ % 10_000}")
    }
}
