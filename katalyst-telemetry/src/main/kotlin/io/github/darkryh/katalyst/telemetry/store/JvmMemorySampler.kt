package io.github.darkryh.katalyst.telemetry.store

import io.github.darkryh.katalyst.telemetry.model.JvmMemory
import java.lang.management.ManagementFactory

/**
 * Cheap, always-available JVM heap/GC sample powering the memory-pressure gauge on the top strip.
 * Reads live MXBeans; no state retained, so calling it costs nothing between calls.
 */
object JvmMemorySampler {
    fun sample(): JvmMemory {
        val memory = ManagementFactory.getMemoryMXBean()
        val heap = memory.heapMemoryUsage
        val nonHeap = memory.nonHeapMemoryUsage
        var gcCount = 0L
        var gcTimeMs = 0L
        for (gc in ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.collectionCount >= 0) gcCount += gc.collectionCount
            if (gc.collectionTime >= 0) gcTimeMs += gc.collectionTime
        }
        return JvmMemory(
            heapUsedBytes = heap.used,
            heapMaxBytes = heap.max, // -1 when undefined; the UI treats <=0 as "unknown ceiling"
            heapCommittedBytes = heap.committed,
            nonHeapUsedBytes = nonHeap.used,
            gcCount = gcCount,
            gcTimeMs = gcTimeMs,
        )
    }
}
