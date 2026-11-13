package ru.digitalpunk.lumi.data.icons

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoundedReaderTest {
    @Test fun anImageOfExactlyOneMegabytePassesAndOneByteMoreFails() {
        assertEquals(1_048_576, assertNotNull(readBounded(ByteArrayInputStream(ByteArray(1_048_576)), 1_048_576, ReadMode.Image)).size)
        assertNull(readBounded(ByteArrayInputStream(ByteArray(1_048_577)), 1_048_576, ReadMode.Image))
    }

    @Test fun htmlOverTheCapIsTruncatedNotRefused() =
        assertEquals(1_048_576, assertNotNull(readBounded(ByteArrayInputStream(ByteArray(1_048_577) { 'a'.code.toByte() }), 1_048_576, ReadMode.Html)).size)

    @Test fun htmlReadingStopsAtTheEndOfTheHeadAndLeavesTheRestUnread() {
        val total = "<html><head><link rel=icon href=/a.png></HEAD>" + "x".repeat(200_000)
        val input = ByteArrayInputStream(total.toByteArray())
        val read = assertNotNull(readBounded(input, 1_048_576, ReadMode.Html))
        assertTrue(String(read).contains("</HEAD>"))
        assertTrue(input.available() > 150_000, "consumed ${total.length - input.available()} bytes")
    }

    @Test fun htmlReadingStopsAtABodyTagToo() {
        val input = ByteArrayInputStream(("<html><body>" + "x".repeat(200_000)).toByteArray())
        readBounded(input, 1_048_576, ReadMode.Html)
        assertTrue(input.available() > 150_000)
    }
}
