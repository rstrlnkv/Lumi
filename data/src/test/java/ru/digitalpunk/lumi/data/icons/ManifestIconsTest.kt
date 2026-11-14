package ru.digitalpunk.lumi.data.icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManifestIconsTest {
    private val url = "https://a.com/app/manifest.json"
    private fun icons(vararg entries: String) = parseManifestIcons("""{"name":"A","icons":[${entries.joinToString(",")}]}""", url)

    @Test fun aRelativeSrcResolvesAgainstTheManifestUrlAndKeepsTheLargerEdge() {
        val got = icons("""{"src":"i/192.png","sizes":"192x192 96x96","type":"image/png"}""").single()
        assertEquals(IconCandidate("https://a.com/app/i/192.png", IconCandidate.Kind.Manifest, 192), got)
    }

    @Test fun monochromeAndRastersWithoutASizeAreLeftOutWhileMaskableAndSvgAreMarked() {
        val got = icons(
            """{"src":"/m.png","sizes":"512x512","purpose":"maskable"}""",
            """{"src":"/mono.png","sizes":"512x512","purpose":"monochrome"}""",
            """{"src":"/s.svg","sizes":"512x512"}""",
            """{"src":"/t.png","sizes":"512x512","type":"image/svg+xml"}""",
            """{"src":"/any.png","sizes":"any"}""",
            """{"src":"/none.png"}""",
            """{"src":"/both.png","sizes":"256x256","purpose":"maskable any"}""",
        )
        assertEquals(
            listOf(
                IconCandidate("https://a.com/m.png", IconCandidate.Kind.Manifest, 512, maskable = true),
                IconCandidate("https://a.com/s.svg", IconCandidate.Kind.Manifest, null, svg = true),
                IconCandidate("https://a.com/t.png", IconCandidate.Kind.Manifest, null, svg = true),
                IconCandidate("https://a.com/both.png", IconCandidate.Kind.Manifest, 256),
            ),
            got,
        )
    }

    @Test fun otherSchemesUserInfoLocalHostsAndNonStringsAreRefused() {
        val got = icons(
            """{"src":"data:image/png;base64,AA","sizes":"192x192"}""",
            """{"src":"javascript:alert(1)","sizes":"192x192"}""",
            """{"src":"ftp://a.com/i.png","sizes":"192x192"}""",
            """{"src":"https://u:p@a.com/i.png","sizes":"192x192"}""",
            """{"src":"https://nas.local/i.png","sizes":"192x192"}""",
            """{"src":"http://a.com/i.png","sizes":"192x192"}""",
            """{"src":5,"sizes":"192x192"}""",
            """{"src":"/ok.png","sizes":"192x192"}""",
        )
        assertEquals(listOf("https://a.com/ok.png"), got.map { it.url })
    }

    @Test fun atMostThirtyEntriesAreRead() =
        assertEquals(30, icons(*Array(40) { """{"src":"/$it.png","sizes":"192x192"}""" }).size)

    @Test fun invalidJsonANonObjectRootOrNoIconsArrayGiveNothing() {
        listOf("", "not json", "[]", "\"x\"", """{"icons":{}}""", """{"icons":[1,"x",null]}""").forEach {
            assertEquals(emptyList(), parseManifestIcons(it, url), it)
        }
    }

    @Test fun aHostileMegabyteOfBracketsFinishesQuickly() {
        listOf("[".repeat(1_048_576), "{\"a\":".repeat(200_000)).forEach { body ->
            val t0 = System.nanoTime()
            assertEquals(emptyList(), parseManifestIcons(body, url))
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 1_000)
        }
    }

    @Test fun theManifestLinkIsReadFromTheHeadAndResolved() {
        assertEquals("https://a.com/site.webmanifest", manifestLink("""<link rel="icon" href="/i.png"><link rel="manifest" href="/site.webmanifest">""", "https://a.com/x/"))
        assertEquals(null, manifestLink("""<link rel="icon" href="/i.png">""", "https://a.com/"))
        assertEquals(null, manifestLink("""<link rel="manifest" href="javascript:x">""", "https://a.com/"))
    }
}
