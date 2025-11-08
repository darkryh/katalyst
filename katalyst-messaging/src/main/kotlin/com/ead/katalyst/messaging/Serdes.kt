package com.ead.katalyst.messaging

interface Serializer<T> {
    fun serialize(input: T): ByteArray
}

interface Deserializer<T> {
    fun deserialize(bytes: ByteArray): T
}
