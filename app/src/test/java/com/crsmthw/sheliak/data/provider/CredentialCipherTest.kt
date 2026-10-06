package com.crsmthw.sheliak.data.provider

import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CredentialCipherTest {

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val cipher = CredentialCipher { key }

    @Test
    fun `a value round-trips`() {
        listOf("", "token-123", "pässwörd ✓ 坂本", "x".repeat(4096)).forEach {
            assertEquals(it, cipher.decrypt(cipher.encrypt(it)))
        }
    }

    @Test
    fun `the same value encrypts differently every time`() {
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
    }

    @Test
    fun `the stored form is not the plain text`() {
        val sealed = cipher.encrypt("secret-token")
        assertEquals(false, String(Base64.getDecoder().decode(sealed), Charsets.ISO_8859_1).contains("secret-token"))
    }

    @Test
    fun `an altered value does not decrypt`() {
        val bytes = Base64.getDecoder().decode(cipher.encrypt("token"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(bytes)))
    }

    @Test
    fun `another key does not decrypt`() {
        assertNull(CredentialCipher { newKey() }.decrypt(cipher.encrypt("token")))
    }

    @Test
    fun `garbage does not decrypt`() {
        listOf("", "not base64 !", Base64.getEncoder().encodeToString(ByteArray(5))).forEach {
            assertNull(cipher.decrypt(it), it)
        }
    }
}
