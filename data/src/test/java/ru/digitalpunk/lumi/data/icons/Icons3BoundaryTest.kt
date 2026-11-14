package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.runBlocking
import ru.digitalpunk.lumi.data.icons.IconCandidate.Kind
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edges of the Icons3 rules that the first test list left to chance (each pinned with a literal, never with the production constant). */
class Icons3BoundaryTest {
    private fun argb(r: Int, g: Int, b: Int, a: Int = 255) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private val root = "https://a.com/"
    private fun c(name: String, kind: Kind, size: Int?, svg: Boolean = false, maskable: Boolean = false) =
        IconCandidate(root + name, kind, size, svg = svg, maskable = maskable)
    private fun order(vararg cs: IconCandidate) = rankIcons(cs.toList(), root).map { it.url.removePrefix(root) }

    // ---- quality and ordering ----

    @Test fun aRasterWithNoPixelsIsNoIconAndAVectorWithNoPixelsIsStillAVector() {
        assertEquals(IconClass.None, iconClass(IconQuality(0, false)))
        assertEquals(IconClass.Small, iconClass(IconQuality(1, false)))
        assertEquals(IconClass.Vector, iconClass(IconQuality(0, true)))
    }

    @Test fun aTouchIconThatIsAnSvgDoesNotCountAsMeetingTheTargetSoTheManifestIsStillRead() {
        assertTrue(needsManifest(listOf(c("t.svg", Kind.Touch, null, svg = true))))
        assertFalse(needsManifest(listOf(c("t.png", Kind.Touch, null))))
    }

    @Test fun theIconThatMeetsTheTargetSurvivesTheThreeRasterCapNextToThreeSmallerOnes() =
        assertEquals(
            "m512.png",
            order(c("i100.png", Kind.Icon, 100), c("i90.png", Kind.Icon, 90), c("i80.png", Kind.Icon, 80), c("m512.png", Kind.Manifest, 512)).first(),
        )

    @Test fun anAnyIconOf512BeatsAMaskableIconOf192BothMeetingTheTarget() =
        assertEquals(listOf("any512.png", "mask192.png"), order(c("mask192.png", Kind.Manifest, 192, maskable = true), c("any512.png", Kind.Manifest, 512)).take(2))

    @Test fun anAnySvgIsTriedBeforeAMaskableSvg() =
        assertEquals("a.svg", order(c("m.svg", Kind.Manifest, null, svg = true, maskable = true), c("a.svg", Kind.Manifest, null, svg = true)).first { it.endsWith(".svg") })

    @Test fun aRepeatedUrlTakesOnlyOneOfTheThreeRasterSlots() {
        val got = order(c("a.png", Kind.Icon, 100), c("a.png", Kind.Icon, 100), c("b.png", Kind.Icon, 90), c("c.png", Kind.Icon, 80), c("d.png", Kind.Icon, 70))
        assertEquals(listOf("a.png", "b.png", "c.png"), got.filter { it.endsWith(".png") && !it.startsWith("apple") })
    }

    // ---- fetcher ----

    private class Http(val pages: Map<String, ByteArray>) : HttpGet {
        val asked = mutableListOf<String>()
        override suspend fun get(url: String, accept: String, maxBytes: Int, userAgent: String): HttpResponse? { asked += url; return pages[url]?.let { HttpResponse(url, it) } }
    }
    /** "ok-<px>" is a raster of that size, "ok-svg" a vector. */
    private val codec = IconCodec { b ->
        String(b).takeIf { it.startsWith("ok") }?.let { t ->
            val tail = t.substringAfterLast('-')
            EncodedIcon("png:$t".toByteArray(), if (tail == "svg") IconQuality(192, true) else IconQuality(tail.toInt(), false))
        }
    }
    private val target = IconTarget("a.com", root)
    private fun html(vararg links: String) = HttpResponse(root, links.joinToString("") { "<link $it>" }.toByteArray())

    @Test fun aGoodIconThatIsNotSharpDoesNotStopTheSearchAndALaterSharpOneWins() = runBlocking<Unit> {
        val http = Http(mapOf(
            root to html("""rel="apple-touch-icon" sizes="200x200" href="/a.png"""", """rel="icon" sizes="256x256" href="/b.png"""").body,
            root + "a.png" to "ok-120".toByteArray(),
            root + "b.png" to "ok-256".toByteArray(),
        ))
        val got = IconFetcher(http, codec).fetch(target)
        assertEquals("png:ok-256", String(got!!.png))
        assertEquals(256, got.meta.srcPx)
        assertTrue(root + "b.png" in http.asked)
    }

    @Test fun anSvgThatDrawsEndsTheSearchSoFaviconIcoIsNotRequested() = runBlocking<Unit> {
        val http = Http(mapOf(
            root to html("""rel="icon" type="image/svg+xml" href="/i.svg"""").body,
            root + "i.svg" to "ok-svg".toByteArray(),
            root + "favicon.ico" to "ok-32".toByteArray(),
        ))
        val got = IconFetcher(http, codec).fetch(target)
        assertTrue(got!!.meta.vector)
        assertFalse(root + "favicon.ico" in http.asked, http.asked.toString())
    }

    @Test fun theThemeColourOfThePageOfferIsStoredInTheMetaAndWithoutAnOfferThereIsNone() = runBlocking<Unit> {
        val pages = mapOf(root + "t.png" to "ok-180".toByteArray())
        val offer = PageIconOffer("https://a.com", listOf(IconCandidate(root + "t.png", Kind.Touch, 180)), null, 0xFF112233.toInt())
        assertEquals(0xFF112233.toInt(), IconFetcher(Http(pages), codec).fetch(target, offer)!!.meta.themeColor)
        assertNull(IconFetcher(Http(pages + (root to html("""rel="apple-touch-icon" href="/t.png"""").body)), codec).fetch(target)!!.meta.themeColor)
    }

    private class HangingHttp(val pages: Map<String, ByteArray>, val hang: Set<String>) : HttpGet {
        override suspend fun get(url: String, accept: String, maxBytes: Int, userAgent: String): HttpResponse? {
            if (url in hang) kotlinx.coroutines.awaitCancellation()
            return pages[url]?.let { HttpResponse(url, it) }
        }
    }

    @Test fun whenTheTotalTimeoutHitsTheBestIconFoundSoFarIsReturnedNotNothing() = runBlocking<Unit> {
        val http = HangingHttp(
            mapOf(
                root to html("""rel="apple-touch-icon" sizes="200x200" href="/a.png"""", """rel="icon" sizes="256x256" href="/b.png"""").body,
                root + "a.png" to "ok-120".toByteArray(),
            ),
            hang = setOf(root + "b.png"),
        )
        val t0 = System.nanoTime()
        val got = IconFetcher(http, codec, totalTimeoutMs = 300).fetch(target)
        assertEquals("png:ok-120", String(got!!.png))
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 5_000)
    }

    @Test fun aHostWithTheSmallestHashCodeOrNoNameStillGetsAnOpaqueColour() {
        assertEquals(Int.MIN_VALUE, "polygenelubricants".hashCode())
        for (host in listOf("polygenelubricants", "", "xn--80ak6aa92e.com", "a".repeat(2000))) assertEquals(0xFF, tileColour(null, null, host) ushr 24, host)
    }

    // ---- manifest ----

    @Test fun aManifestIconSrcOf2048And2049CharactersIsTakenAndRefusedRespectively() {
        fun json(len: Int) = """{"icons":[{"src":"https://a.com/${"x".repeat(len - 14)}","sizes":"192x192"}]}"""
        assertEquals(1, parseManifestIcons(json(2048), root + "m.json").size)
        assertEquals(0, parseManifestIcons(json(2049), root + "m.json").size)
    }

    // ---- tile colour ----

    @Test fun aColourIsColourfulFromSaturationQuarterAndValueOneFifthAndNoLess() {
        assertNotNull(dominantColour(IntArray(50) { argb(200, 150, 150) }), "s = 0.25")
        assertNull(dominantColour(IntArray(50) { argb(200, 151, 151) }), "s = 0.245")
        assertNotNull(dominantColour(IntArray(50) { argb(51, 0, 0) }), "v = 0.2")
        assertNull(dominantColour(IntArray(50) { argb(40, 0, 0) }), "v = 0.157")
        assertNotNull(dominantColour(IntArray(50) { argb(200, 144, 144) }), "s = 0.28 is colourful")
    }

    @Test fun aPixelHalfOpaqueCountsAndOneJustBelowDoesNot() {
        assertNotNull(dominantColour(IntArray(50) { argb(200, 30, 30, 128) }))
        assertNull(dominantColour(IntArray(50) { argb(200, 30, 30, 127) }))
    }

    @Test fun aFewVividPixelsOutweighManyPaleOnesButNotManyMoreVividOnes() {
        val pale = argb(200, 140, 140); val vivid = argb(30, 60, 200)
        val got = dominantColour(IntArray(18) { if (it < 10) pale else if (it < 18) vivid else 0 })!!
        assertTrue((got and 0xFF) > (got shr 16 and 0xFF), "vivid blue wins by weight: ${Integer.toHexString(got)}")
    }

    @Test fun twoColoursOfNeighbouringBinsAreNotMergedIntoOne() {
        val a = argb(0x18, 0x00, 0xF0); val b = argb(0x00, 0x80, 0xF0); val green = argb(0, 200, 0)
        val got = dominantColour(IntArray(21) { if (it < 6) a else if (it < 12) b else green })!!
        assertEquals(argb(0, 200, 0), got)
    }

    @Test fun aThemeColourWithNoAlphaComesOutOpaque() =
        assertEquals(argb(200, 30, 30), tileColour(null, argb(200, 30, 30, 0), "a.com"))

    @Test fun theHostColourHasTheHueOfTheHostHashSaturationHalfWayAndValueSixtyPercent() {
        var negative = 0
        for (i in 0 until 300) {
            val host = "site$i.example.org"
            if (host.hashCode() < 0) negative++
            val c = tileColour(null, null, host)
            val r = (c shr 16 and 0xFF) / 255.0; val g = (c shr 8 and 0xFF) / 255.0; val b = (c and 0xFF) / 255.0
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            assertEquals(0.60, mx, 0.01, host); assertEquals(0.45, (mx - mn) / mx, 0.02, host)
            val h = when (mx) { r -> 60 * (((g - b) / (mx - mn)) % 6); g -> 60 * ((b - r) / (mx - mn) + 2); else -> 60 * ((r - g) / (mx - mn) + 4) }.let { if (it < 0) it + 360 else it }
            val want = Math.floorMod(host.hashCode(), 360).toDouble()
            assertTrue(min(abs(h - want), 360 - abs(h - want)) < 3, "$host hue $h want $want")
        }
        assertTrue(negative > 50, "the loop must cover negative hash codes")
    }

    @Test fun everyTileModeOfASmallIconAndOfAnIconStoredBeforeIcons3() {
        assertEquals(TileMode.Letter, tileMode(IconClass.Small, bigTile = true, fillsTile = true))
        assertEquals(TileMode.SmallIcon, tileMode(IconClass.Small, bigTile = false, fillsTile = true))
        assertEquals(TileMode.Image, tileMode(IconClass.None, bigTile = true, fillsTile = true))
        assertEquals(TileMode.Letter, tileMode(IconClass.None, bigTile = true, fillsTile = false))
        assertEquals(TileMode.SmallIcon, tileMode(IconClass.None, bigTile = false, fillsTile = false))
        assertEquals(TileMode.SmallIcon, tileMode(IconClass.Sharp, bigTile = true, fillsTile = false))
        assertEquals(TileMode.Image, tileMode(IconClass.Good, bigTile = true, fillsTile = true))
        assertEquals(TileMode.SmallIcon, tileMode(IconClass.Vector, bigTile = true, fillsTile = true))
    }

    // ---- meta chunk ----

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val crc = CRC32().apply { update(type.toByteArray()); update(data) }.value.toInt()
        return ByteBuffer.allocate(12 + data.size).putInt(data.size).put(type.toByteArray()).put(data).putInt(crc).array()
    }
    private val sig = fakePng().copyOf(8)
    private val ihdr = fakePng().copyOfRange(8, 33)
    private fun text(t: String) = chunk("tEXt", t.toByteArray(Charsets.ISO_8859_1))
    private val good = "lumi\u0000v=4;px=57;vec=0;tc=336699"

    @Test fun aFivedigitSizeRoundTripsAndASixDigitOrFiveHexColourIsRefused() {
        assertEquals(IconMeta(12345, false, null), IconMeta.parse(IconMeta.insert(fakePng(), IconMeta(12345, false, null))!!))
        assertNull(IconMeta.parse(sig + ihdr + text("lumi\u0000v=4;px=123456;vec=0;tc=")))
        assertNull(IconMeta.parse(sig + ihdr + text("lumi\u0000v=4;px=57;vec=0;tc=33669")))
        assertEquals(IconMeta(57, false, 0xFF336699.toInt()), IconMeta.parse(sig + ihdr + text(good)))
    }

    @Test fun aMetaOfVersion3IsNotCurrentSoTheIconIsFetchedAgainOnce() {
        assertNull(IconMeta.parse(sig + ihdr + text("lumi\u0000v=3;px=57;vec=0;tc=336699")))
    }

    @Test fun aChunkMissingTheLastByteOfItsCrcIsRefusedWithoutAnException() {
        val whole = sig + ihdr + text(good)
        assertNull(IconMeta.parse(whole.copyOf(whole.size - 1)))
        assertNull(IconMeta.parse(whole.copyOf(whole.size - 2)))
    }

    @Test fun aMetaChunkAfterTheFirstIdatOrWithABadCrcOrAnotherKeywordIsIgnored() {
        assertNull(IconMeta.parse(sig + ihdr + chunk("IDAT", byteArrayOf(1)) + text(good)))
        assertNull(IconMeta.parse((sig + ihdr + text(good)).also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }))
        assertNull(IconMeta.parse(sig + ihdr + text("lumix" + good.substring(5))))
        assertNotNull(IconMeta.parse(sig + ihdr + text(good)))
    }

    @Test fun theMetaIsFoundInThe32ndChunkAndNotInThe33rd() {
        val filler = chunk("tIME", ByteArray(0))
        assertNotNull(IconMeta.parse(sig + ihdr + filler * 30 + text(good)))
        assertNull(IconMeta.parse(sig + ihdr + filler * 31 + text(good)))
    }
    private operator fun ByteArray.times(n: Int): ByteArray = (1..n).fold(ByteArray(0)) { acc, _ -> acc + this }

    @Test fun insertRefusesAHeaderOnlyFileAndAnIhdrOfTheWrongLengthWithoutAnException() {
        assertNull(IconMeta.insert(pngHeader(10), IconMeta(10, false, null)))
        assertNull(IconMeta.insert(fakePng().also { ByteBuffer.wrap(it, 8, 4).putInt(14) }, IconMeta(10, false, null)))
        assertNull(IconMeta.insert(ByteArray(10), IconMeta(10, false, null)))
    }

    // ---- svg safety ----

    private val ok = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"><path d="M0 0h16v16z"/></svg>"""

    @Test fun anSvgWithExactly4000TagsAndDepth64IsKeptAndOneMoreOfEitherIsRefused() {
        assertTrue(svgSafe("<svg>" + "<rect/>".repeat(3999) + "</svg>"), "4000 tags")
        assertFalse(svgSafe("<svg>" + "<rect/>".repeat(4000) + "</svg>"), "4001 tags")
        assertTrue(svgSafe("<svg>" + "<g>".repeat(63) + "</g>".repeat(63) + "</svg>"), "depth 64")
        assertFalse(svgSafe("<svg>" + "<g>".repeat(64) + "</g>".repeat(64) + "</svg>"), "depth 65")
    }

    @Test fun selfClosedTagsCommentsAndDeclarationsDoNotAddDepth() {
        assertTrue(svgSafe("<svg>" + "<g/>".repeat(100) + "</svg>"))
        assertTrue(svgSafe("<svg>" + "<!--x-->".repeat(100) + "</svg>"))
        assertTrue(svgSafe("<?xml version=\"1.0\"?><svg>" + "<?pi x?>".repeat(100) + "</svg>"))
    }

    @Test fun onlyOneRuleCatchesEachOfAnEmptyDoctypeSubsetASlashBeforeAnEventAttributeAndATagInsideATag() {
        assertFalse(svgSafe("<!DOCTYPE svg [ ]>$ok"), "empty internal subset")
        assertFalse(svgSafe(ok.replace("<path", "<rect/onload=\"x()\"/><path")), "slash before on*")
        assertFalse(svgSafe("<svg <g/></svg>"), "tag inside tag")
    }

    @Test fun anSvgIsSniffedAfter600BytesOfWhitespaceOr150CharactersOfXmlDeclarationButNotAfter1100() {
        assertTrue(looksLikeSvg((" ".repeat(600) + "<svg/>").toByteArray()))
        assertTrue(looksLikeSvg(("<?xml version=\"1.0\" " + "a=\"b\" ".repeat(20) + "?><svg/>").toByteArray()))
        assertFalse(looksLikeSvg((" ".repeat(1100) + "<svg/>").toByteArray()))
    }

    @Test fun theInkThresholdIsExactlyTwoPercentOfTheStoredSquare() {
        val n = 192 * 192
        assertFalse(hasInk(IntArray(n) { if (it < 737) -1 else 0 }))
        assertTrue(hasInk(IntArray(n) { if (it < 738) -1 else 0 }))
    }
}
