package com.ead.katalyst.example.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordHasherTest {
    private val hasher = PasswordHasher()

    @Test
    fun `hashing the same password is deterministic`() {
        val first = hasher.hash("S3cretPass!")
        val second = hasher.hash("S3cretPass!")
        assertEquals(first, second)
    }

    @Test
    fun `verify distinguishes matching and mismatching passwords`() {
        val hashed = hasher.hash("another-pass")
        assertTrue(hasher.verify("another-pass", hashed))
        assertFalse(hasher.verify("not-the-same", hashed))
    }
}
