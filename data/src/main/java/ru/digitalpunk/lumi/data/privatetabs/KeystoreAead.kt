package ru.digitalpunk.lumi.data.privatetabs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a non-exportable Android Keystore key, made on the first [encrypt]. The key needs no user
 * authentication: saving runs in the background, and the lock screen is checked by the app instead (ADR 006).
 * Keystore picks a fresh random IV for every encryption.
 */
class KeystoreAead(private val alias: String = ALIAS) : Aead {
    private fun keyStore() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun storedKey(): SecretKey? = keyStore().getKey(alias, null) as SecretKey?

    private fun newKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
    }.generateKey()

    override fun encrypt(plain: ByteArray, aad: ByteArray): Sealed = wrap {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, storedKey() ?: newKey()) }
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plain)
        Sealed(cipher.iv, ciphertext)
    }

    override fun decrypt(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray = wrap {
        val key = storedKey() ?: throw AeadException(IllegalStateException(), lost = true)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        cipher.updateAAD(aad)
        cipher.doFinal(ciphertext)
    }

    override fun deleteKey() {
        try {
            keyStore().deleteEntry(alias)
        } catch (_: Exception) {
        }
    }

    private fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw e as? AeadException ?: AeadException(e)
    }

    private companion object {
        const val ALIAS = "lumi.private-tabs.v1"
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
