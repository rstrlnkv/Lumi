package ru.digitalpunk.lumi.engine

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Moved from `:data` (BoundedReaderTest, IconEdgeTest) with the read itself: Gecko's body stream is read here now. */
class ReadCappedTest {
    private val mb = 1_048_576

    @Test fun aBodyOfExactlyTheCapPassesAndOneByteMoreFails() {
        assertEquals(mb, assertNotNull(readCapped(ByteArrayInputStream(ByteArray(mb)), mb, htmlHead = false)).size)
        assertNull(readCapped(ByteArrayInputStream(ByteArray(mb + 1)), mb, htmlHead = false))
    }

    @Test fun htmlOverTheCapIsTruncatedNotRefused() =
        assertEquals(mb, assertNotNull(readCapped(ByteArrayInputStream(ByteArray(mb + 1) { 'a'.code.toByte() }), mb, htmlHead = true)).size)

    @Test fun htmlReadingStopsAtTheEndOfTheHeadAndLeavesTheRestUnread() {
        val total = "<html><head><link rel=icon href=/a.png></HEAD>" + "x".repeat(200_000)
        val input = ByteArrayInputStream(total.toByteArray())
        val read = assertNotNull(readCapped(input, mb, htmlHead = true))
        assertTrue(String(read).contains("</HEAD>"))
        assertTrue(input.available() > 150_000, "consumed ${total.length - input.available()} bytes")
    }

    @Test fun htmlReadingStopsAtABodyTagToo() {
        val input = ByteArrayInputStream(("<html><body>" + "x".repeat(200_000)).toByteArray())
        readCapped(input, mb, htmlHead = true)
        assertTrue(input.available() > 150_000)
    }

    /** Hands out at most [n] bytes per read, as a slow socket does. */
    private class Trickle(data: ByteArray, private val n: Int) : InputStream() {
        private val inner = ByteArrayInputStream(data)
        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, minOf(len, n))
    }

    @Test fun aHeadEndTagSplitAcrossReadsStillStopsTheReader() {
        val html = "<html><head><link rel=icon href=/i.png></head>" + "X".repeat(5000)
        for (n in 1..7) {
            val got = readCapped(Trickle(html.toByteArray(), n), mb, htmlHead = true)!!
            assertTrue(got.size < html.length - 4000, "n=$n read ${got.size} of ${html.length}")
        }
    }

    @Test fun anEmptyStreamGivesAnEmptyArrayInBothModes() {
        assertEquals(0, readCapped(ByteArrayInputStream(ByteArray(0)), mb, htmlHead = false)!!.size)
        assertEquals(0, readCapped(ByteArrayInputStream(ByteArray(0)), mb, htmlHead = true)!!.size)
    }
}
