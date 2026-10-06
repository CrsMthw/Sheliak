package com.crsmthw.sheliak.data.provider

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM over strings, as Lyra's EncryptedPrefs does it: a fresh 12-byte IV per value (the cipher picks it),
 * stored in front of the ciphertext + 128-bit tag, the whole Base64-encoded. Plain javax.crypto + java.util.Base64,
 * so it is unit-tested on the JVM with a software key; [CredentialStore] gives it the AndroidKeyStore key.
 */
class CredentialCipher(private val key: () -> SecretKey) {

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        check(iv.size == IV_SIZE) { "Unexpected GCM IV size ${iv.size}" }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + sealed)
    }

    /** The plain text, or null when [encoded] is not something [encrypt] produced with this key (or was altered). */
    fun decrypt(encoded: String): String? = try {
        val combined = Base64.getDecoder().decode(encoded)
        if (combined.size <= IV_SIZE) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_BITS, combined, 0, IV_SIZE),
            )
            String(cipher.doFinal(combined, IV_SIZE, combined.size - IV_SIZE), Charsets.UTF_8)
        }
    } catch (_: java.security.GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
