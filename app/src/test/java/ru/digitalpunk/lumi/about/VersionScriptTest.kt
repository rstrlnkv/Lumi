package ru.digitalpunk.lumi.about

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The root build script makes the version, build number and channel (versioning standard); it runs only in Gradle, so this reads it. */
class VersionScriptTest {
    private val script = File("../build.gradle.kts").readText()

    @Test fun `the build number is minutes since 2020 from the committer time`() {
        assertTrue("\"--format=%ct\"" in script, "no committer time")
        assertTrue("- 1577836800) / 60" in script, "not minutes since 2020-01-01 UTC")
        assertFalse("\"rev-list\", \"--count\"" in script, "still the commit count")
    }

    @Test fun `the channel comes from the branch`() {
        assertTrue("\"main\" -> \"STABLE\"" in script)
        assertTrue("\"beta\" -> \"BETA\"" in script)
        assertTrue("else -> \"DEV\"" in script)
    }

    @Test fun `the version name is plain X Y Z`() {
        assertTrue("""Regex(""${'"'}\d+\.\d+\.\d+""${'"'})""" in script, "the version check must accept X.Y.Z only")
        assertFalse("-dev" in script)
    }
}
