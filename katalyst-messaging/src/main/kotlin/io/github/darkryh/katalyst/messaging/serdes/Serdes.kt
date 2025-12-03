package io.github.darkryh.katalyst.messaging.serdes

interface Serializer<T> {
    fun serialize(input: T): ByteArray
}

interface Deserializer<T> {
    fun deserialize(bytes: ByteArray): T
}
