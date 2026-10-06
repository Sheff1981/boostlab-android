package com.boostlab.app.tunnel

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ClientIdentityStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadOrCreate(): ClientIdentity {
        val encrypted = preferences.getString(KEY_CIPHERTEXT, null)
        val iv = preferences.getString(KEY_IV, null)

        if (encrypted == null && iv == null) {
            return createAndStore()
        }
        check(encrypted != null && iv != null) {
            "Client identity storage is incomplete"
        }

        val privateKey = decrypt(encrypted, iv)
        val keyPair = try {
            KeyPair(Key.fromBase64(privateKey))
        } catch (error: Exception) {
            throw IllegalStateException("Stored client identity is invalid", error)
        }

        return ClientIdentity(
            privateKeyBase64 = keyPair.privateKey.toBase64(),
            publicKeyBase64 = keyPair.publicKey.toBase64(),
        )
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .apply()
    }

    private fun createAndStore(): ClientIdentity {
        val keyPair = KeyPair()
        val privateKey = keyPair.privateKey.toBase64()

        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())

        val ciphertext = cipher.doFinal(privateKey.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv

        preferences.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()

        return ClientIdentity(
            privateKeyBase64 = privateKey,
            publicKeyBase64 = keyPair.publicKey.toBase64(),
        )
    }

    private fun decrypt(ciphertextBase64: String, ivBase64: String): String {
        try {
            val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.DECRYPT_MODE,
                wrappingKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )

            return String(
                cipher.doFinal(ciphertext),
                StandardCharsets.UTF_8,
            )
        } catch (error: Exception) {
            throw IllegalStateException(
                "Client identity cannot be decrypted; do not silently rotate the WireGuard identity",
                error,
            )
        }
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "boostlab_secure_identity"
        private const val KEY_CIPHERTEXT = "wireguard_private_key_ciphertext"
        private const val KEY_IV = "wireguard_private_key_iv"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "boostlab_wireguard_identity_wrap"
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
