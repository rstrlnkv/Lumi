package ru.digitalpunk.lumi.data.privatetabs

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PrivateEnvelopeTest {
    private val aead = SoftwareAead()
    private val plain = "https://secret.example/ title".toByteArray()
    private val headerSize = 6

    @Test fun sealThenOpenReturnsThePlaintext() {
        assertContentEquals(plain, PrivateEnvelope.open(aead, PrivateEnvelope.seal(aead, plain)))
    }

    @Test fun eachSealUsesAFreshIv() {
        val a = PrivateEnvelope.seal(aead, plain)
        val b = PrivateEnvelope.seal(aead, plain)
        assertFalse(a.copyOfRange(headerSize, headerSize + 12).contentEquals(b.copyOfRange(headerSize, headerSize + 12)))
    }

    @Test fun flippedCiphertextBitIsRejected() {
        val sealed = PrivateEnvelope.seal(aead, plain)
        for (i in headerSize + 12 until sealed.size) {
            val bad = sealed.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertNull(PrivateEnvelope.open(aead, bad), "byte $i")
        }
    }

    @Test fun flippedHeaderByteIsRejected() {
        val sealed = PrivateEnvelope.seal(aead, plain)
        for (i in 0 until headerSize) {
            val bad = sealed.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertNull(PrivateEnvelope.open(aead, bad), "byte $i")
        }
    }

    @Test fun swappedIvIsRejected() {
        val a = PrivateEnvelope.seal(aead, plain)
        val b = PrivateEnvelope.seal(aead, plain)
        val swapped = a.copyOf().also { b.copyInto(it, headerSize, headerSize, headerSize + 12) }
        assertNull(PrivateEnvelope.open(aead, swapped))
    }

    @Test fun truncatedFileIsRejected() {
        val sealed = PrivateEnvelope.seal(aead, plain)
        for (n in 0 until sealed.size) assertNull(PrivateEnvelope.open(aead, sealed.copyOf(n)), "length $n")
    }

    @Test fun emptyFileIsRejected() {
        assertNull(PrivateEnvelope.open(aead, ByteArray(0)))
    }

    @Test fun wrongKeyIsRejected() {
        val sealed = PrivateEnvelope.seal(aead, plain)
        assertNull(PrivateEnvelope.open(SoftwareAead(), sealed))
    }

    @Test fun unknownMagicIsRejected() {
        val sealed = PrivateEnvelope.seal(aead, plain).also { it[0] = 'X'.code.toByte() }
        assertNull(PrivateEnvelope.open(aead, sealed))
    }
}
