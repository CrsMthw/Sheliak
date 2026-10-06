package com.crsmthw.sheliak.data.provider

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The providers' secrets (tokens, passwords), encrypted at rest — Lyra's EncryptedPrefs mechanism kept as is:
 * an AES-256-GCM key that never leaves the AndroidKeyStore, and a private SharedPreferences file holding only
 * ciphertext ([CredentialCipher]). Nothing is ever written as plain text, and the file is excluded from backup
 * and device transfer (data_extraction_rules.xml).
 *
 * Keys are the provider's own choice, namespaced by it: `"<provider id>:<what>"` for a per-instance secret
 * (`plex:<machineIdentifier>:token`), `"<type>:<what>"` for one shared by a type's instances (`plex:account:token`).
 * Reads and writes are synchronous and cheap (SharedPreferences keeps the file in memory); the keystore key is
 * created on first use, off the startup path.
 */
class CredentialStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private val secretKey: SecretKey by lazy { loadOrCreateKey() }

    private val cipher = CredentialCipher { secretKey }

    fun putToken(key: String, value: String) {
        prefs.edit { putString(key, cipher.encrypt(value)) }
    }

    /** The stored value, or null when there is none or it no longer decrypts (a reset keystore). */
    fun token(key: String): String? = prefs.getString(key, null)?.let(cipher::decrypt)

    fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    /** Removes every key starting with [prefix] — a provider's whole namespace when it is removed. */
    fun removeAll(prefix: String) {
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        if (keys.isNotEmpty()) prefs.edit { keys.forEach(::remove) }
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFS_FILE        = "sheliak_credentials"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS         = "sheliak_credentials_key"
        const val KEY_BITS          = 256
    }
}
