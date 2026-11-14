package ru.digitalpunk.lumi.about

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The root build script makes the version, build number and channel (versioning standard); it runs only in Gradle, so this reads it. */
class VersionScriptTest {
    private val script = File("../build.gradle.kts").readText()

    @Test fun `the build number comes from the commit count script, not from the committer time`() {
        assertTrue("gradle/build-number.sh" in script, "the root build does not run the build-number script")
        assertFalse("%ct" in script, "still the committer time")
        assertFalse("1577836800" in script, "still minutes since 2020")
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
