package ru.digitalpunk.lumi.data.privatetabs

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrivateTabsCodecTest {
    private val tabs = listOf(
        PrivateStoredTab("p1", "https://a.example/", "A", "{\"s\":1}"),
        PrivateStoredTab("p2", "https://b.example/", "B", null),
    )

    @Test fun roundTripKeepsOrderUrlTitleAndState() {
        assertEquals(tabs, PrivateTabsCodec.decode(PrivateTabsCodec.encode(tabs)))
    }

    @Test fun nullStateRoundTrips() {
        assertNull(PrivateTabsCodec.decode(PrivateTabsCodec.encode(tabs))!![1].sessionState)
    }

    @Test fun unicodeTitleSurvives() {
        val t = listOf(PrivateStoredTab("p", "https://x/", "Привет 👩‍👩‍👧 ‏עברית", null))
        assertEquals(t, PrivateTabsCodec.decode(PrivateTabsCodec.encode(t)))
    }

    @Test fun truncatedInputIsNull() {
        val bytes = PrivateTabsCodec.encode(tabs)
        for (n in 0 until bytes.size) assertNull(PrivateTabsCodec.decode(bytes.copyOf(n)), "length $n")
    }

    @Test fun hugeLengthPrefixIsNullWithoutAllocating() {
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(1); writeInt(1); writeInt(Int.MAX_VALUE) } }.toByteArray()
        assertNull(PrivateTabsCodec.decode(bytes))
        val count = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(1); writeInt(Int.MAX_VALUE) } }.toByteArray()
        assertNull(PrivateTabsCodec.decode(count))
    }

    @Test fun unknownVersionIsNull() {
        val bytes = PrivateTabsCodec.encode(tabs).also { it[3] = 2 }
        assertNull(PrivateTabsCodec.decode(bytes))
    }

    @Test fun trailingBytesAreRejected() {
        assertNull(PrivateTabsCodec.decode(PrivateTabsCodec.encode(tabs) + byteArrayOf(0)))
    }
}
