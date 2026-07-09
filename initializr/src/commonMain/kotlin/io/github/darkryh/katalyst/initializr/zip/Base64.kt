package io.github.darkryh.katalyst.initializr.zip

/**
 * Standard Base64 encoder (pure Kotlin). The browser layer turns the ZIP [ByteArray] into a
 * `data:` URL to trigger a download, which needs the bytes as Base64 — doing it here keeps all the
 * binary handling in testable common code and the browser shim to a single anchor click.
 */
object Base64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i + 3 <= bytes.size) {
            val n = (bytes[i].toInt() and 0xFF shl 16) or
                (bytes[i + 1].toInt() and 0xFF shl 8) or
                (bytes[i + 2].toInt() and 0xFF)
            sb.append(ALPHABET[n ushr 18 and 0x3F])
            sb.append(ALPHABET[n ushr 12 and 0x3F])
            sb.append(ALPHABET[n ushr 6 and 0x3F])
            sb.append(ALPHABET[n and 0x3F])
            i += 3
        }
        when (bytes.size - i) {
            1 -> {
                val n = bytes[i].toInt() and 0xFF shl 16
                sb.append(ALPHABET[n ushr 18 and 0x3F])
                sb.append(ALPHABET[n ushr 12 and 0x3F])
                sb.append("==")
            }
            2 -> {
                val n = (bytes[i].toInt() and 0xFF shl 16) or (bytes[i + 1].toInt() and 0xFF shl 8)
                sb.append(ALPHABET[n ushr 18 and 0x3F])
                sb.append(ALPHABET[n ushr 12 and 0x3F])
                sb.append(ALPHABET[n ushr 6 and 0x3F])
                sb.append("=")
            }
        }
        return sb.toString()
    }
}
