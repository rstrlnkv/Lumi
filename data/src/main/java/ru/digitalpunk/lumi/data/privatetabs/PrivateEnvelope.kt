package ru.digitalpunk.lumi.data.privatetabs

/**
 * The file layout: `"LPT1" | version (1) | IV length (1) | IV | ciphertext + tag`. The header and a fixed label are
 * the AAD, so a changed header, IV, ciphertext or length fails authentication.
 */
internal object PrivateEnvelope {
    private val MAGIC = "LPT1".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    /** What [seal] adds to the plaintext: the header, the IV and the tag. */
    val OVERHEAD: Int get() = HEADER.size + IV_BYTES + TAG_BYTES
    private val LABEL = "lumi.private-tabs".toByteArray(Charsets.US_ASCII)
    private val HEADER = MAGIC + byteArrayOf(VERSION, IV_BYTES.toByte())

    fun seal(aead: Aead, plain: ByteArray): ByteArray {
        val sealed = aead.encrypt(plain, HEADER + LABEL)
        if (sealed.iv.size != IV_BYTES) throw AeadException(IllegalStateException())
        return HEADER + sealed.iv + sealed.ciphertext
    }

    /** Null for anything that is not a sealed file this key made; an [AeadException] that is not [AeadException.lost] is thrown on. */
    fun open(aead: Aead, bytes: ByteArray): ByteArray? {
        if (bytes.size < HEADER.size + IV_BYTES + TAG_BYTES) return null
        if (!bytes.copyOfRange(0, HEADER.size).contentEquals(HEADER)) return null
        val iv = bytes.copyOfRange(HEADER.size, HEADER.size + IV_BYTES)
        return try {
            aead.decrypt(iv, bytes.copyOfRange(HEADER.size + IV_BYTES, bytes.size), HEADER + LABEL)
        } catch (e: AeadException) {
            if (e.lost) null else throw e
        }
    }
}
