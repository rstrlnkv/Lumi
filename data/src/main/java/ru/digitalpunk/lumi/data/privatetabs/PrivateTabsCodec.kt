package ru.digitalpunk.lumi.data.privatetabs

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** What is stored of a private tab: nothing else. [sessionState] is Gecko's session JSON. */
data class PrivateStoredTab(val id: String, val url: String, val title: String, val sessionState: String?)

/** The plaintext: `int version | int count | per tab: str id, str url, str title, bool hasState, [str state]`; a str is an int length and UTF-8. */
internal object PrivateTabsCodec {
    private const val VERSION = 1
    private const val MAX_TABS = 10_000
    private const val MAX_FIELD_BYTES = 2 * 1024 * 1024

    /** Whether [decode] can read [tab]'s URL and title back. */
    fun fits(tab: PrivateStoredTab) = listOf(tab.url, tab.title).all { it.toByteArray(Charsets.UTF_8).size <= MAX_FIELD_BYTES }

    fun encode(tabs: List<PrivateStoredTab>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeInt(VERSION)
            writeInt(tabs.size)
            tabs.forEach {
                writeUtf8(it.id)
                writeUtf8(it.url)
                writeUtf8(it.title)
                writeBoolean(it.sessionState != null)
                it.sessionState?.let { s -> writeUtf8(s) }
            }
        }
        return bytes.toByteArray()
    }

    /** Null for an unknown version, a count or length out of range, a short input or trailing bytes. */
    fun decode(bytes: ByteArray): List<PrivateStoredTab>? = try {
        val input = ByteArrayInputStream(bytes)
        val data = DataInputStream(input)
        if (data.readInt() != VERSION) {
            null
        } else {
            val count = data.readInt()
            if (count !in 0..MAX_TABS) {
                null
            } else {
                val tabs = List(count) {
                    PrivateStoredTab(data.readText(input), data.readText(input), data.readText(input), if (data.readBoolean()) data.readText(input) else null)
                }
                tabs.takeIf { input.available() == 0 }
            }
        }
    } catch (_: IOException) {
        null
    }

    private fun DataOutputStream.writeUtf8(text: String) {
        val utf8 = text.toByteArray(Charsets.UTF_8)
        writeInt(utf8.size)
        write(utf8)
    }

    /** The length is checked against the limit and the bytes left before anything is allocated. */
    private fun DataInputStream.readText(input: ByteArrayInputStream): String {
        val size = readInt()
        if (size !in 0..MAX_FIELD_BYTES || size > input.available()) throw IOException()
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }
}
