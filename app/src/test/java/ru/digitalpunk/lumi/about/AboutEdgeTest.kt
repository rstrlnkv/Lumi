package ru.digitalpunk.lumi.about

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Inputs the first tests did not feed: odd version names, changelog shapes, and library entries. */
class AboutEdgeTest {
    @Test fun `a version without the dash before dev is not a dev build`() {
        assertFalse(AppVersion("0.1.0dev.1", 1).isDev)
        assertFalse(AppVersion("dev.1", 1).isDev)
    }

    @Test fun `a build number past Int range is copied whole`() =
        assertEquals("1.0.0 (3000000000)", AppVersion("1.0.0", 3_000_000_000L).copyText)

    @Test fun `Unreleased is skipped whatever its letter case`() =
        assertEquals(listOf("1.0.0"), parseChangelog("## [UNRELEASED]\n- x\n## [1.0.0] - 2026-01-01\n- a\n").map { it.version })

    @Test fun `an indented line right under a release heading does not crash and is not an item`() {
        val release = parseChangelog("## [1.0.0] - 2026-01-01\n  stray indent\n- a\n").single()
        assertEquals(listOf("a"), release.items)
    }

    @Test fun `an indented line under Unreleased before any release does not crash`() =
        assertEquals(emptyList(), parseChangelog("## [Unreleased]\n  stray\n"))

    @Test fun `trailing spaces on a release heading are accepted`() =
        assertEquals(LocalDate.of(2026, 1, 1), parseChangelog("## [1.0.0] - 2026-01-01   \n- a\n").single().date)

    @Test fun `a yanked release keeps its place in the list`() {
        // Keep a Changelog writes yanked releases as "## [0.0.1] - 2014-05-31 [YANKED]".
        val text = "## [0.2.0] - 2026-02-01\n- b\n## [0.1.0] - 2026-01-01 [YANKED]\n- a\n"
        assertEquals(listOf("0.2.0", "0.1.0"), parseChangelog(text).map { it.version })
    }

    @Test fun `link reference lines at the end of the file are not items`() {
        val text = "## [1.0.0] - 2026-01-01\n- a\n\n[Unreleased]: https://example.org/compare\n[1.0.0]: https://example.org/tag\n"
        assertEquals(listOf("a"), parseChangelog(text).single().items)
    }

    @Test fun `a very large changelog parses`() {
        val text = (1..20_000).joinToString("\n") { "## [1.0.$it] - 2026-01-01\n- item $it" }
        assertEquals(20_000, parseChangelog(text).size)
    }

    @Test fun `a license id missing from the licenses map is shown by its id with no text`() {
        val json = """{"libraries":[{"uniqueId":"a:b","name":"B","licenses":["Weird-1.0"]}],"licenses":{}}"""
        val lic = parseLicenses(json).single().licenses.single()
        assertEquals("Weird-1.0", lic.name)
        assertEquals("", lic.text)
    }

    @Test fun `a library with no licenses array or no name still lists`() {
        val json = """{"libraries":[{"uniqueId":"a:b"}]}"""
        val lib = parseLicenses(json).single()
        assertEquals("a:b", lib.name)
        assertEquals(emptyList(), lib.licenses)
        assertEquals(null, lib.version)
    }
}
