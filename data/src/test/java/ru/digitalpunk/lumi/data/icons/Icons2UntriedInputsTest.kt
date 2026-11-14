package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inputs nobody fed after Icons2: hosts and ports named by a page, hostile manifests, odd stored files, a hanging manifest. */
class Icons2UntriedInputsTest {
    private val base = "https://a.com/"
    private val target = IconTarget("a.com", "https://a.com/")

    // The page (or its redirect target) is untrusted: a link must pass the same host rule as the root (ADR 014, "host-name rule kept").
    private val unsafeHrefs = listOf(
        "https://192.168.1.1/i.png", "https://10.0.0.1/i.png", "https://127.0.0.1/i.png", "https://[::1]/i.png",
        "https://localhost/i.png", "https://nas.local/i.png", "https://router/i.png", "https://printer.internal/i.png",
        "https://a.com:8443/i.png", "https://u:p@a.com/i.png",
    )

    @Test fun aLinkToAnIpLiteralALocalNameAnotherPortOrUserInfoIsNotACandidate() {
        for (href in unsafeHrefs) {
            assertEquals(emptyList(), parseIconLinks("""<link rel="icon" href="$href">""", base), href)
            assertEquals(emptyList(), parseIconLinks("""<link rel="apple-touch-icon" href="$href">""", base), href)
        }
    }

    @Test fun aManifestLinkToALocalHostIsNotReturned() {
        for (href in unsafeHrefs) assertNull(manifestLink("""<link rel="manifest" href="$href">""", base), href)
    }

    private class Recording(val pages: Map<String, HttpResponse?>) : HttpGet {
        val asked = mutableListOf<String>()
        override suspend fun get(url: String, accept: String, maxBytes: Int, userAgent: String): HttpResponse? { asked += url; return pages[url] }
    }

    private val codec = IconCodec { String(it).takeIf { s -> s.startsWith("ok") }?.let { s -> EncodedIcon(s.toByteArray(), IconQuality(s.substringAfterLast('-').toIntOrNull() ?: 192, false)) } }

    @Test fun aPageThatNamesALanAddressForItsIconNeverMakesTheAppAskThatAddress() = runBlocking<Unit> {
        val html = """<link rel="apple-touch-icon" href="https://192.168.1.1/t.png"><link rel="manifest" href="https://10.0.0.1/m.json"><link rel="icon" href="https://nas.local/i.png">"""
        val http = Recording(mapOf("https://a.com/" to HttpResponse("https://a.com/", html.toByteArray())))
        IconFetcher(http, codec).fetch(target)
        assertEquals(listOf("https://a.com/", "https://a.com/apple-touch-icon.png", "https://a.com/apple-touch-icon-precomposed.png", "https://a.com/favicon.ico"), http.asked)
    }

    @Test fun aManifestThatIsGarbageStillLeavesTheFaviconToTry() = runBlocking<Unit> {
        val http = Recording(mapOf(
            "https://a.com/" to HttpResponse("https://a.com/", """<link rel="manifest" href="/m.json">""".toByteArray()),
            "https://a.com/m.json" to HttpResponse("https://a.com/m.json", ByteArray(5000) { 0xFF.toByte() }),
            "https://a.com/favicon.ico" to HttpResponse("https://a.com/favicon.ico", "ok-ico".toByteArray()),
        ))
        assertContentEquals("ok-ico".toByteArray(), IconFetcher(http, codec).fetch(target)?.png)
    }

    @Test fun aManifestThatNeverAnswersEndsInNullWithinTheTotalDeadlineAndTheCandidatesAreSkipped() = runBlocking<Unit> {
        val http = object : HttpGet {
            override suspend fun get(url: String, accept: String, maxBytes: Int, userAgent: String): HttpResponse? =
                if (url == "https://a.com/") HttpResponse(url, """<link rel="manifest" href="/m.json">""".toByteArray()) else kotlinx.coroutines.awaitCancellation()
        }
        val t0 = System.nanoTime()
        assertNull(IconFetcher(http, codec, totalTimeoutMs = 100).fetch(target))
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 5_000)
    }

    @Test fun aManifestWithoutAnyUsableEntryFallsBackToTheSmallLinks() = runBlocking<Unit> {
        val http = Recording(mapOf(
            "https://a.com/" to HttpResponse("https://a.com/", """<link rel="icon" sizes="32x32" href="/i.png"><link rel="manifest" href="/m.json">""".toByteArray()),
            "https://a.com/m.json" to HttpResponse("https://a.com/m.json", """{"icons":[{"src":"/x.svg","sizes":"any"}]}""".toByteArray()),
            "https://a.com/i.png" to HttpResponse("https://a.com/i.png", "ok-32".toByteArray()),
        ))
        assertContentEquals("ok-32".toByteArray(), IconFetcher(http, codec).fetch(target)?.png)
        assertEquals(listOf("https://a.com/", "https://a.com/m.json", "https://a.com/apple-touch-icon.png", "https://a.com/apple-touch-icon-precomposed.png", "https://a.com/i.png", "https://a.com/x.svg", "https://a.com/favicon.ico"), http.asked)
    }

    private val url = "https://a.com/manifest.json"
    private fun nested(depth: Int) = "[".repeat(depth) + "]".repeat(depth)

    @Test fun theManifestNestingLimitIs32AndTheIconsAtDepth32AreStillRead() {
        val entry = """{"src":"/a.png","sizes":"192x192"}"""
        // root object (1) > icons array (2) > entry object (3) > padding of nested arrays up to total depth 32
        fun withDepth(total: Int) = """{"icons":[$entry,{"pad":${nested(total - 3)}}]}"""
        assertEquals(1, parseManifestIcons(withDepth(32), url).size)
        assertEquals(emptyList(), parseManifestIcons(withDepth(33), url))
    }

    @Test fun bracketsInsideStringsAndEscapedQuotesDoNotCountAsNesting() {
        val noise = "[".repeat(100) + "\\\"" + "{".repeat(100)
        assertEquals(1, parseManifestIcons("""{"name":"$noise","icons":[{"src":"/a.png","sizes":"192x192"}]}""", url).size)
    }

    @Test fun absurdSizesDoNotBreakTheParserAndAZeroSizeNeverMeetsTheTarget() {
        val got = parseManifestIcons("""{"icons":[{"src":"/z.png","sizes":"0x0"},{"src":"/h.png","sizes":"99999x99999"},{"src":"/g.png","sizes":"999999999999x1"}]}""", url)
        assertEquals(0, got.first { it.url.endsWith("z.png") }.size)
        assertEquals(99999, got.first { it.url.endsWith("h.png") }.size)
    }

    @Test fun purposeAndTypeAreMatchedWhateverTheirCaseAndSpacing() {
        val got = parseManifestIcons("""{"icons":[{"src":"/a.png","sizes":"192x192","purpose":"  Maskable   ANY "},{"src":"/b.png","sizes":"192x192","type":"IMAGE/SVG+XML"},{"src":"/c.PNG?x=.svg","sizes":"192x192"},{"src":"/d.SVG","sizes":"192x192"}]}""", url)
        assertEquals(listOf("https://a.com/a.png", "https://a.com/c.PNG?x=.svg"), got.filter { !it.svg }.map { it.url })
        assertEquals(listOf("https://a.com/b.png", "https://a.com/d.SVG"), got.filter { it.svg }.map { it.url })
    }

    // Per the manifest spec a purpose that is not a string is ignored, so the icon keeps the default `any`.
    @Test fun aNonStringPurposeIsTakenAsAnyAndNonStringSizesOrTypeAreNotACrash() {
        val got = parseManifestIcons("""{"icons":[{"src":"/a.png","sizes":192,"purpose":"any"},{"src":"/b.png","sizes":"192x192","purpose":["any"]},{"src":"/c.png","sizes":"192x192","type":7}]}""", url)
        assertEquals(listOf("https://a.com/b.png", "https://a.com/c.png"), got.map { it.url })
    }

    @Test fun aSrcOver2048CharactersIsRefused() =
        assertEquals(emptyList(), parseManifestIcons("""{"icons":[{"src":"/${"a".repeat(2048)}.png","sizes":"192x192"}]}""", url))

    // An empty src resolves to the manifest's own URL, which would be fetched as an image.
    @Test fun anEmptyOrBlankSrcIsNotAnIcon() =
        assertEquals(emptyList(), parseManifestIcons("""{"icons":[{"src":"   ","sizes":"192x192"},{"src":"","sizes":"192x192"}]}""", url))

    // The stored file check.
    private fun stored(bytes: ByteArray): Boolean {
        val dir = kotlin.io.path.createTempDirectory("icons2").toFile()
        try {
            val store = SiteIconStore(dir)
            store.write("a.com", bytes)
            return store.meta("a.com") != null
        } finally { dir.deleteRecursively() }
    }

    @Test fun aPngWithoutIcons3MetaIsNotCurrentWhateverItsSize() {
        assertTrue(stored(IconMeta.insert(fakePng(), IconMeta(16, false, null))!!))
        assertFalse(stored(pngHeader(192)))
        assertFalse(stored(fakePng()))
        assertFalse(stored(ByteArray(0)))
    }

    @Test fun aDirectoryOrAMissingFileIsNotCurrent() {
        val dir = kotlin.io.path.createTempDirectory("icons2").toFile()
        try {
            val store = SiteIconStore(dir)
            assertNull(store.meta("none.com"))
            store.write("a.com", IconMeta.insert(fakePng(), IconMeta(192, false, null))!!)
            File(dir, dir.listFiles()!!.first { it.name.endsWith(".png") }.name).delete()
            assertNull(store.meta("a.com"))
        } finally { dir.deleteRecursively() }
    }

    private fun c(name: String, kind: IconCandidate.Kind, size: Int?) = IconCandidate("https://a.com/$name", kind, size)
    private fun order(vararg cs: IconCandidate) = rankIcons(cs.toList(), base).filter { it.kind != IconCandidate.Kind.RootTouch }.map { it.url.removePrefix(base) }

    @Test fun anIconOf179DoesNotMeetTheTargetButOneOf180Does() {
        assertEquals(listOf("i192.png", "i179.png", "favicon.ico"), order(c("i179.png", IconCandidate.Kind.Icon, 179), c("i192.png", IconCandidate.Kind.Icon, 192)))
        assertEquals(listOf("i180.png", "i179.png", "favicon.ico"), order(c("i179.png", IconCandidate.Kind.Icon, 179), c("i180.png", IconCandidate.Kind.Icon, 180)))
        assertTrue(needsManifest(listOf(c("i179.png", IconCandidate.Kind.Icon, 179))))
        assertFalse(needsManifest(listOf(c("i180.png", IconCandidate.Kind.Icon, 180))))
    }

    @Test fun atTheSameSizeATouchIconBeatsAManifestIconAndThatBeatsAnIconWhateverTheListOrder() {
        val k = IconCandidate.Kind.values()
        val all = listOf(c("icon.png", k[2], 192), c("manifest.png", k[1], 192), c("touch.png", k[0], 192))
        assertEquals(listOf("touch.png", "manifest.png", "icon.png", "favicon.ico"), order(*all.toTypedArray()))
        assertEquals(listOf("touch.png", "manifest.png", "icon.png", "favicon.ico"), order(*all.reversed().toTypedArray()))
        val small = listOf(c("icon.png", k[2], 64), c("manifest.png", k[1], 64), c("touch.png", k[0], 64))
        assertEquals(listOf("touch.png", "manifest.png", "icon.png", "favicon.ico"), order(*small.reversed().toTypedArray()))
    }

    @Test fun tabsAndNewlinesSeparatePurposeTokensAndASrcWithSpacesAroundItIsTrimmed() {
        val got = parseManifestIcons("""{"icons":[{"src":" /a.png ","sizes":"192x192","purpose":"maskable\tany"},{"src":"/b.png","sizes":"192x192","purpose":"maskable\nany"}]}""", url)
        assertEquals(listOf("https://a.com/a.png", "https://a.com/b.png"), got.map { it.url })
    }

    @Test fun aBackslashInAStringDoesNotHideLaterNestingFromTheDepthCheck() =
        assertEquals(emptyList(), parseManifestIcons("""{"a":"x\\y","icons":[{"src":"/a.png","sizes":"192x192"}],"deep":${nested(40)}}""", url))

    @Test fun anExplicitPort443IsAcceptedInAManifestAndAnotherPortIsNot() {
        val got = parseManifestIcons("""{"icons":[{"src":"https://a.com:443/a.png","sizes":"192x192"},{"src":"https://a.com:444/b.png","sizes":"192x192"}]}""", url)
        assertEquals(listOf("https://a.com:443/a.png"), got.map { it.url })
    }

    @Test fun aLinkTypeOfSvgInAnyCaseAndSizesInUpperCaseAreRead() {
        assertEquals(listOf(true), parseIconLinks("""<link rel="icon" type="IMAGE/SVG+XML" href="/v">""", base).map { it.svg })
        assertEquals(listOf(192), parseIconLinks("""<link rel="icon" sizes="192X192" href="/a.png">""", base).map { it.size })
    }

    @Test fun aRelativeHrefThatMakesTheResolvedUrlTooLongIsDropped() {
        val longBase = "https://a.com/" + "d".repeat(2000) + "/"
        assertEquals(emptyList(), parseIconLinks("""<link rel="icon" href="${"x".repeat(100)}.png">""", longBase))
    }

    @Test fun theFirstManifestLinkWinsWhenAPageHasTwo() =
        assertEquals("https://a.com/one.json", manifestLink("""<link rel="manifest" href="/one.json"><link rel="manifest" href="/two.json">""", base))

    @Test fun theManifestRelIsMatchedInAnyCaseAmongOtherTokens() =
        assertEquals("https://a.com/m.json", manifestLink("""<link rel="Alternate MANIFEST" href="/m.json">""", base))

    @Test fun eachRequestAsksForItsOwnKindOfBody() = runBlocking<Unit> {
        val accepts = mutableMapOf<String, String>()
        val http = object : HttpGet {
            override suspend fun get(url: String, accept: String, maxBytes: Int, userAgent: String): HttpResponse? {
                accepts[url] = accept
                return when (url) {
                    "https://a.com/" -> HttpResponse(url, """<link rel="manifest" href="/m.json">""".toByteArray())
                    "https://a.com/m.json" -> HttpResponse(url, """{"icons":[{"src":"/m.png","sizes":"192x192"}]}""".toByteArray())
                    else -> HttpResponse(url, "ok".toByteArray())
                }
            }
        }
        IconFetcher(http, codec).fetch(target)
        assertEquals("text/html", accepts["https://a.com/"])
        assertTrue(accepts["https://a.com/m.json"]!!.startsWith("application/manifest+json"))
        assertEquals("image/*", accepts["https://a.com/m.png"])
    }
}
