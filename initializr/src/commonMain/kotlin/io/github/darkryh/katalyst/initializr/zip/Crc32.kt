package io.github.darkryh.katalyst.initializr.zip

/**
 * Standard CRC-32 (IEEE 802.3, polynomial 0xEDB88820) — required by every entry in a ZIP archive.
 * Implemented in pure Kotlin so the initializer needs no JS library to build archives.
 */
internal object Crc32 {
    private val TABLE =
        IntArray(256) { n ->
            var c = n
            repeat(8) {
                c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1
            }
            c
        }

    /** Compute the CRC-32 of [bytes] as an unsigned value held in a Long. */
    fun compute(bytes: ByteArray): Long {
        var crc = 0.inv()
        for (b in bytes) {
            crc = TABLE[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
        }
        return (crc.inv()).toLong() and 0xFFFFFFFFL
    }
}
