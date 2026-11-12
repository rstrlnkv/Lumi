package ru.digitalpunk.lumi.data.privatetabs

import javax.crypto.AEADBadTagException

/** [iv] is the one the cipher chose; [ciphertext] ends with the GCM tag. */
class Sealed(val iv: ByteArray, val ciphertext: ByteArray)

/** Authenticated encryption with one stored key. Every failure is an [AeadException]. */
interface Aead {
    fun encrypt(plain: ByteArray, aad: ByteArray): Sealed

    fun decrypt(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray

    /** Forgets the key; the next [encrypt] makes a new one. Never throws. */
    fun deleteKey()
}

/**
 * Names only the failure's class: no message, so no content can reach a log. [lost] means the data can never be
 * opened (a failed tag check or a missing key); any other failure may pass.
 */
class AeadException(cause: Throwable, val lost: Boolean = cause is AEADBadTagException) : Exception(cause.javaClass.simpleName)
