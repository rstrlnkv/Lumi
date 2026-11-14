package ru.digitalpunk.lumi.about

import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangelogParserTest {
    private val sample = """
        # Changelog

        Intro line with [a link](https://keepachangelog.com/).

        ## [Unreleased]

        ### Added
        - Not out yet.

        ## [0.2.0] - 2026-12-01

        ### Added
        - Reader mode.
        ### Fixed
        - A long item that wraps
          onto a second line.

        ## [0.1.0-dev.1] - 2026-11-14

        ### Added
        - The first build.
    """.trimIndent()

    @Test fun `releases come newest first, without Unreleased and without group headings`() {
        val releases = parseChangelog(sample)
        assertEquals(listOf("0.2.0", "0.1.0-dev.1"), releases.map { it.version })
        assertEquals(LocalDate.of(2026, 12, 1), releases[0].date)
        assertEquals(listOf("Reader mode.", "A long item that wraps onto a second line."), releases[0].items)
        assertEquals(listOf("The first build."), releases[1].items)
    }

    @Test fun `a heading without a date or with a bad date has no date`() {
        val releases = parseChangelog("## [1.0.0]\n- a\n## [0.9.0] - 2026-13-45\n- b\n")
        assertEquals(listOf("1.0.0", "0.9.0"), releases.map { it.version })
        assertNull(releases[0].date)
        assertNull(releases[1].date)
    }

    @Test fun `a heading with no items gives an empty list`() =
        assertEquals(emptyList(), parseChangelog("## [1.0.0] - 2026-01-01\n").single().items)

    @Test fun `CRLF line ends parse like LF`() =
        assertEquals(parseChangelog(sample), parseChangelog(sample.replace("\n", "\r\n")))

    @Test fun `empty or garbage input gives no releases`() {
        assertEquals(emptyList(), parseChangelog(""))
        assertEquals(emptyList(), parseChangelog("hello\n- loose item\n## not a release\n"))
    }

    @Test fun `the repository CHANGELOG parses with 0_1_0-dev_1 as its newest release`() {
        val text = File("../CHANGELOG.md").readText()
        val first = parseChangelog(text).first()
        assertEquals("0.1.0-dev.1", first.version)
        assertTrue(first.items.isNotEmpty())
    }
}
