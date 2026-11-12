package ru.digitalpunk.lumi.data.privatetabs

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Real AES-GCM with a software key, for JVM tests; [failWith] makes every call throw it. */
internal class SoftwareAead(var key: SecretKey = newKey()) : Aead {
    var deleted = 0
    var failWith: Exception? = null

    override fun encrypt(plain: ByteArray, aad: ByteArray): Sealed = guard {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        cipher.updateAAD(aad)
        Sealed(cipher.iv, cipher.doFinal(plain))
    }

    override fun decrypt(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray = guard {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        cipher.updateAAD(aad)
        cipher.doFinal(ciphertext)
    }

    override fun deleteKey() {
        deleted++
    }

    private fun <T> guard(block: () -> T): T {
        failWith?.let { throw it }
        return try {
            block()
        } catch (e: Exception) {
            throw AeadException(e)
        }
    }

    companion object {
        fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}
