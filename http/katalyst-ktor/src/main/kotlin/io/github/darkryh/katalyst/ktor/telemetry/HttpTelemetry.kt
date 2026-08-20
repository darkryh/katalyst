package io.github.darkryh.katalyst.ktor.telemetry

import io.github.darkryh.katalyst.core.annotation.KatalystInternalApi
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.ceil

/**
 * Process-global, bounded HTTP request metrics. Written by an always-on pipeline interceptor
 * ([installKatalystHttpTelemetry]) and read by the telemetry capturer. Framework-internal
 * (`@KatalystInternalApi`), a pure side-channel that never alters request handling.
 *
 * Memory is bounded by construction: a handful of atomic counters, a fixed ~6-entry status-class map,
 * and a fixed-bucket latency histogram (never a per-request list).
 */
@KatalystInternalApi
object HttpTelemetry {

    val inFlight = AtomicInteger(0)
    val total = AtomicLong(0)
    val abortedByMiddleware = AtomicLong(0)
    val exceptionsSeen = AtomicLong(0)

    private val statusClasses = ConcurrentHashMap<String, AtomicLong>()
    private val latency = Buckets()

    fun onStart() {
        inFlight.incrementAndGet()
        total.incrementAndGet()
    }

    fun onComplete(statusCode: Int?, durationMs: Long) {
        inFlight.updateAndGet { if (it > 0) it - 1 else 0 }
        val cls = when (statusCode) {
            null -> "other"
            in 200..299 -> "2xx"
            in 300..399 -> "3xx"
            in 400..499 -> "4xx"
            in 500..599 -> "5xx"
            else -> "other"
        }
        statusClasses.computeIfAbsent(cls) { AtomicLong(0) }.incrementAndGet()
        latency.record(durationMs)
    }

    fun recordException() {
        exceptionsSeen.incrementAndGet()
    }

    /** Called by the middleware chain when a request is short-circuited by a MiddlewareResult.Abort. */
    fun recordAbort() {
        abortedByMiddleware.incrementAndGet()
    }

    fun statusClassCounts(): Map<String, Long> = statusClasses.mapValues { it.value.get() }

    /** (p50, p95, max) request latency in ms over a fixed-bucket histogram. */
    fun latencyStats(): Triple<Double, Double, Double> = latency.stats()

    /** Fixed-bucket, allocation-free latency histogram — bounded memory regardless of request volume. */
    private class Buckets(
        private val n: Int = 24,
        private val minMs: Double = 1.0,
        private val maxMs: Double = 300_000.0,
    ) {
        private val counts = AtomicLongArray(n)
        private val totalCount = AtomicLong(0)
        private val maxBits = AtomicLong(java.lang.Double.doubleToLongBits(0.0))
        private val boundaries = DoubleArray(n) { i -> minMs * Math.pow(maxMs / minMs, i.toDouble() / (n - 1)) }

        fun record(ms: Long) {
            val v = ms.coerceAtLeast(0L).toDouble()
            var idx = n - 1
            for (i in 0 until n) if (v <= boundaries[i]) { idx = i; break }
            counts.incrementAndGet(idx)
            totalCount.incrementAndGet()
            while (true) {
                val b = maxBits.get()
                if (v <= java.lang.Double.longBitsToDouble(b)) break
                if (maxBits.compareAndSet(b, java.lang.Double.doubleToLongBits(v))) break
            }
        }

        fun stats(): Triple<Double, Double, Double> {
            val tc = totalCount.get()
            if (tc == 0L) return Triple(0.0, 0.0, 0.0)
            fun pct(p: Double): Double {
                val rank = ceil(p * tc).toLong().coerceAtLeast(1)
                var cum = 0L
                for (i in 0 until n) {
                    cum += counts.get(i)
                    if (cum >= rank) return boundaries[i]
                }
                return java.lang.Double.longBitsToDouble(maxBits.get())
            }
            return Triple(pct(0.50), pct(0.95), java.lang.Double.longBitsToDouble(maxBits.get()))
        }
    }
}

/**
 * Install the always-on Katalyst HTTP telemetry interceptor on the [Application] pipeline. Wraps the
 * whole request in the Monitoring phase to record in-flight count, throughput, status-class mix and
 * latency. It only ever reads/records — it re-throws any downstream failure unchanged.
 */
@KatalystInternalApi
fun installKatalystHttpTelemetry(application: Application) {
    application.intercept(ApplicationCallPipeline.Monitoring) {
        HttpTelemetry.onStart()
        val startNanos = System.nanoTime()
        var status: Int? = null
        try {
            proceed()
            status = call.response.status()?.value
        } catch (cause: Throwable) {
            HttpTelemetry.recordException()
            throw cause
        } finally {
            HttpTelemetry.onComplete(status, (System.nanoTime() - startNanos) / 1_000_000)
        }
    }
}
