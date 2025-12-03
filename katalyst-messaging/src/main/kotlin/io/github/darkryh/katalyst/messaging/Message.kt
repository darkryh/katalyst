package io.github.darkryh.katalyst.messaging

data class Message(
    val key: String? = null,
    val payload: ByteArray,
    val headers: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message

        if (key != other.key) return false
        if (!payload.contentEquals(other.payload)) return false
        if (headers != other.headers) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key?.hashCode() ?: 0
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + headers.hashCode()
        return result
    }
}
