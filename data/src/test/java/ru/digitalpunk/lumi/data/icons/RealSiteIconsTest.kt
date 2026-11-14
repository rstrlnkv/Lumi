package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.runBlocking
import ru.digitalpunk.lumi.data.icons.IconCandidate.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The icon markup real sites served in November 2026 to an anonymous GET (build 51 showed letter tiles for Google and
 * YouTube, and a small Instagram icon). Google and YouTube list their 180 px touch icons only to an iPhone user agent.
 */
class RealSiteIconsTest {
    /** Serves [ios] to a user agent that names an iPhone, else [android]; images by URL. */
    private class Site(val root: String, val finalUrl: String, val android: String, val ios: String, val images: Map<String, String>) : HttpGet {
        val agents = mutableListOf<String>()
        override suspend fun get(url: String, accept: String, maxBytes: Int, userAgent: String): HttpResponse? {
            agents += userAgent
            if (url == root) return HttpResponse(finalUrl, (if ("iPhone" in userAgent) ios else android).toByteArray())
            return images[url]?.let { HttpResponse(url, it.toByteArray()) }
        }
    }

    /** "ok-<px>" encodes to a raster of that size. */
    private val codec = IconCodec { b -> String(b).takeIf { it.startsWith("ok-") }?.let { EncodedIcon("png:$it".toByteArray(), IconQuality(it.removePrefix("ok-").toInt(), false)) } }

    private val google = Site(
        "https://google.com/", "https://www.google.com/",
        android = """<html><head><meta charset="UTF-8"><title>Google</title></head><body>""",
        ios = """<html><head><link href="//www.gstatic.com/images/branding/searchlogo/ico/favicon.ico" rel="icon">""" +
            """<link href="/images/branding/product_ios/3x/googleg_60dp.png" rel="apple-touch-icon" sizes="180x180">""" +
            """<link href="/images/branding/product_ios/2x/googleg_60dp.png" rel="apple-touch-icon" sizes="120x120">""" +
            """<link href="/images/branding/product_ios/1x/googleg_57dp.png" rel="apple-touch-icon"></head><body>""",
        images = mapOf(
            "https://www.google.com/images/branding/product_ios/3x/googleg_60dp.png" to "ok-180",
            "https://www.google.com/images/branding/product_ios/2x/googleg_60dp.png" to "ok-120",
            "https://google.com/favicon.ico" to "ok-32",
        ),
    )

    private val youtube = Site(
        "https://youtube.com/", "https://m.youtube.com/",
        android = """<head><link rel="icon" href="https://m.youtube.com/static/favicon.ico" type="image/x-icon"></head>""",
        ios = """<head><link rel="icon" href="https://m.youtube.com/static/favicon.ico" type="image/x-icon">""" +
            """<link rel="apple-touch-icon-precomposed" href="https://m.youtube.com/static/apple-touch-icon-180x180-precomposed.png" sizes="180x180">""" +
            """<link rel="apple-touch-icon-precomposed" href="https://m.youtube.com/static/apple-touch-icon-120x120-precomposed.png" sizes="120x120"></head>""",
        images = mapOf(
            "https://m.youtube.com/static/apple-touch-icon-180x180-precomposed.png" to "ok-180",
            "https://m.youtube.com/static/favicon.ico" to "ok-32",
            "https://youtube.com/favicon.ico" to "ok-16",
        ),
    )

    @Test fun googleGivesItsSharpTouchIconBecauseTheRootPageIsAskedForAsSafari() = runBlocking<Unit> {
        val got = IconFetcher(google, codec).fetch(IconTarget("google.com", "https://google.com/"))
        assertEquals("png:ok-180", String(got!!.png))
        assertTrue(google.agents.all { "iPhone" in it }, google.agents.toString())
    }

    @Test fun youtubeGivesItsSharpTouchIconBecauseTheRootPageIsAskedForAsSafari() = runBlocking<Unit> {
        val got = IconFetcher(youtube, codec).fetch(IconTarget("youtube.com", "https://youtube.com/"))
        assertEquals("png:ok-180", String(got!!.png))
    }

    @Test fun anOpenedPageThatOffersOnlyAFaviconStillGetsTheSharpTouchIconOfTheRootPage() = runBlocking<Unit> {
        // What the content script sends from m.youtube.com in a Gecko tab: the favicon link only.
        val offer = PageIconOffer("https://m.youtube.com", listOf(IconCandidate("https://m.youtube.com/static/favicon.ico", Kind.Icon, null)), null, 0xFFFF0000.toInt())
        val got = IconFetcher(youtube, codec).fetch(IconTarget("youtube.com", "https://youtube.com/"), offer)
        assertEquals("png:ok-180", String(got!!.png))
        assertEquals(0xFFFF0000.toInt(), got.meta.themeColor)
    }

    @Test fun anOpenedPageThatOffersASharpIconDoesNotFetchTheRootPage() = runBlocking<Unit> {
        val offer = PageIconOffer("https://m.youtube.com", listOf(IconCandidate("https://m.youtube.com/static/apple-touch-icon-180x180-precomposed.png", Kind.Touch, 180)), null, null)
        IconFetcher(youtube, codec).fetch(IconTarget("youtube.com", "https://youtube.com/"), offer)
        assertEquals(1, youtube.agents.size)
    }

    /**
     * Instagram's 180 px touch icon: a full-bleed square whose corners are cut transparent with a radius of about 40 px,
     * and lossy WebP leaves its bottom row at alpha 254.
     */
    private fun roundedSquare(size: Int, radius: Int): IntArray = IntArray(size * size) { i ->
        val x = i % size; val y = i / size
        val cx = x.coerceIn(radius, size - 1 - radius); val cy = y.coerceIn(radius, size - 1 - radius)
        val outside = (x - cx) * (x - cx) + (y - cy) * (y - cy) > radius * radius
        when {
            outside -> 0x00000000
            y == size - 1 -> 0xFEC13584.toInt()
            else -> 0xFFC13584.toInt()
        }
    }

    @Test fun aFullBleedIconWithRoundedTransparentCornersFillsTheTile() {
        assertTrue(fillsTile(roundedSquare(180, 40), 180))
        assertTrue(fillsTile(roundedSquare(192, 44), 192))
    }

    @Test fun aRoundLogoDoesNotFillTheTile() {
        assertFalse(fillsTile(roundedSquare(180, 89), 180))
    }

    @Test fun aTransparentPixelAwayFromTheCornersMeansTheIconDoesNotFillTheTile() {
        assertFalse(fillsTile(IntArray(180 * 180) { 0xFFC13584.toInt() }.also { it[90 * 180 + 90] = 0x80C13584.toInt() }, 180))
        assertFalse(fillsTile(IntArray(180 * 180) { 0xFFC13584.toInt() }.also { it[90] = 0 }, 180))
    }
}
