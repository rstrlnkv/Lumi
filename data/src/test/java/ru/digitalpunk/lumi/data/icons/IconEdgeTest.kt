package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inputs the first tests did not feed: limits on both sides, odd attributes, split reads, clock and timing. */
class IconEdgeTest {
    private val base = "https://a.com/"

    @Test fun portsOutsideOneToSixtyFiveThousandFiveHundredThirtyFiveAreRefused() {
        assertNull(iconTarget("https://a.com:65536/"))
        assertNull(iconTarget("https://a.com:0/"))
        assertNull(iconTarget("https://a.com:-1/"))
        assertNull(iconTarget("https://a.com:99999999999/"))
        assertEquals("https://a.com/", iconTarget("https://a.com:65535/")?.rootUrl)
        assertEquals("https://a.com/", iconTarget("https://a.com:1/")?.rootUrl)
    }

    @Test fun anUrlOf2048CharactersIsAllowedAndOneMoreIsNot() {
        val head = "https://a.com/"
        assertTrue(isAllowedIconUrl(head + "x".repeat(2048 - head.length)))
        assertFalse(isAllowedIconUrl(head + "x".repeat(2049 - head.length)))
    }

    @Test fun sizesTakeTheLargerEdgeOfANonSquareIconAndTheLargestOfAList() {
        val one = parseIconLinks("""<link rel="icon" sizes="48x96" href="/a.png">""", base).single()
        assertEquals(96, one.size)
        val many = parseIconLinks("""<link rel="icon" sizes="16x16 64x32 32x32" href="/a.png">""", base).single()
        assertEquals(64, many.size)
        assertNull(parseIconLinks("""<link rel="icon" sizes="any" href="/a.png">""", base).single().size)
    }

    @Test fun precomposedTouchIconAndShortcutIconAreRecognised() {
        val c = parseIconLinks(
            """<link rel="apple-touch-icon-precomposed" href="/p.png"><link rel="shortcut icon" href="/s.ico"><link rel="SHORTCUT ICON" href="/u.ico">""", base)
        assertEquals(listOf(IconCandidate.Kind.Touch, IconCandidate.Kind.Icon, IconCandidate.Kind.Icon), c.map { it.kind })
    }

    @Test fun aRelOfShortcutAloneIsNotAnIcon() =
        assertEquals(0, parseIconLinks("""<link rel="shortcut" href="/s.ico">""", base).size)

    @Test fun theSameUrlTwiceIsAskedOnce() {
        val links = parseIconLinks("""<link rel="icon" href="/a.png"><link rel="icon" href="/a.png"><link rel="icon" href="/b.png">""", base)
        assertEquals(listOf("https://a.com/a.png", "https://a.com/b.png", "https://a.com/favicon.ico"), rankIcons(links, base).filter { it.kind != IconCandidate.Kind.RootTouch }.map { it.url })
    }

    private fun gray(g: Int) = IntArray(16) { (0xFF shl 24) or (g shl 16) or (g shl 8) or g }

    @Test fun theLightEdgeLineIsAtMeanLuminance0_92() {
        assertFalse(hasLightEdge(gray(234), 4))
        assertTrue(hasLightEdge(gray(235), 4))
    }

    @Test fun luminanceWeightsGreenHigherThanRed() {
        // R=255 G=200 B=255 -> 0.845 with the right weights, 0.954 with red and green swapped
        assertFalse(hasLightEdge(IntArray(16) { 0xFFFFC8FF.toInt() }, 4))
    }

    @Test fun onlyTheOuterRingCountsForTheLightEdge() {
        // 8x8: white top row and left column (corners included), black right column and bottom row
        val white = 0xFFFFFFFF.toInt(); val black = 0xFF000000.toInt()
        val px = IntArray(64) { i -> val x = i % 8; val y = i / 8; if (x == 0 || y == 0) white else if (x == 7 || y == 7) black else white }
        assertFalse(hasLightEdge(px, 8))
        assertFalse(hasLightEdge(IntArray(256) { i -> if (i / 16 == 15) black else white }, 16), "bottom row only")
        assertFalse(hasLightEdge(IntArray(256) { i -> if (i % 16 == 15) black else white }, 16), "right column only")
        // a dark interior does not matter
        assertTrue(hasLightEdge(IntArray(64) { i -> val x = i % 8; val y = i / 8; if (x in 2..5 && y in 2..5) black else white }, 8))
    }

    @Test fun theTotalDeadlineIsReallyAboutTheGivenTimeNotJustAnyNull() = runBlocking<Unit> {
        val http = object : HttpGet { override suspend fun get(url: String, accept: String, maxBytes: Int): HttpResponse? = kotlinx.coroutines.awaitCancellation() }
        val codec = IconCodec { null }
        val t0 = System.nanoTime()
        assertNull(IconFetcher(http, codec, totalTimeoutMs = 100).fetch(IconTarget("a.com", base)))
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms in 90..3_000, "took $ms ms")
    }

    @Test fun theDefaultTotalDeadlineIsThirtySeconds() = assertEquals(30_000L, TOTAL_TIMEOUT_MS)

    @Test fun aWriteIntoADeletedIconsFolderReturnsFalseAndDoesNotThrow() {
        val dir = File(createTempDirectory("icons").toFile(), "icons")
        val store = SiteIconStore(dir)
        dir.deleteRecursively()
        assertFalse(store.write("a.com", byteArrayOf(1)))
        assertNull(store.read("a.com"))
        assertNull(store.lastModified("a.com"))
    }

    @Test fun aBackslashBeforeAnAtSignDoesNotMakeThePartAfterItTheHost() {
        // Gecko reads `\` as `/`, so the page lives on evil.com; its tile must not borrow good.com's icon.
        assertNull(iconTarget("https://evil.com\\@good.com/"))
    }

    @Test fun aTabOrNewlineInsideTheHostIsRefused() {
        assertNull(iconTarget("https://a.co\tm/"))
        assertNull(iconTarget("https://a.com\n.evil.com/"))
    }
}
