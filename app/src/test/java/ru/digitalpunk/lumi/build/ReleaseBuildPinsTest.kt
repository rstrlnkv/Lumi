package ru.digitalpunk.lumi.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The release build's rules live in Gradle scripts that no unit test runs, so these read them. The packaging rule was also
 * measured on the built APK: `unzip -v app-release-unsigned.apk` lists every native library under lib/ as Stored.
 */
class ReleaseBuildPinsTest {
    private val app = File("build.gradle.kts").readText()
    private val root = File("../build.gradle.kts").readText()
    private val numberScript = File("../gradle/build-number.sh").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test fun nativeLibrariesAreNotForcedIntoTheCompressedExtractedPackaging() {
        assertFalse(Regex("useLegacyPackaging\\s*=\\s*true").containsMatchIn(app))
        assertFalse(Regex("extractNativeLibs\\s*=\\s*\"?true").containsMatchIn(manifest))
    }

    @Test fun theUnsignedWarningRunsBeforeEveryReleasePackagingTaskAndOnlyWithoutKeys() {
        val task = app.substringAfter("val warnUnsignedRelease by tasks.registering {").substringBefore("\n}\n")
        assertTrue("onlyIf { unsigned }" in task, task)
        assertTrue("val unsigned = !canSign" in task, task)
        assertTrue("UNSIGNED release APK" in task && "doLast" in task, task)
        val wired = app.substringAfter("tasks.matching {").substringBefore(".configureEach")
        assertTrue("\"packageRelease\"" in wired && "\"packageReleaseBundle\"" in wired, wired)
        assertEquals(1, Regex("dependsOn\\(warnUnsignedRelease\\)").findAll(app).count())
    }

    @Test fun gitRunsWithNoSystemOrUserConfigAndRefusesInjectedConfig() {
        val git = root.substringAfter("fun git(").substringBefore("val code = try")
        assertTrue("environment(\"GIT_CONFIG_NOSYSTEM\", \"1\")" in git, git)
        assertTrue("environment(\"GIT_CONFIG_GLOBAL\", \"/dev/null\")" in git, git)
        // The build number script reads the history with the same clean config and refuses injected config.
        assertTrue("GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null git" in numberScript, numberScript)
        val refused = numberScript.substringAfter("for name in ").substringBefore("; do")
        for (name in listOf("GIT_DIR", "GIT_WORK_TREE", "GIT_GRAFT_FILE", "GIT_REPLACE_REF_BASE", "GIT_CONFIG_COUNT", "GIT_CONFIG_PARAMETERS", "GIT_NO_REPLACE_OBJECTS")) {
            assertTrue(name in refused.split(" "), "$name is not refused")
        }
    }
}
