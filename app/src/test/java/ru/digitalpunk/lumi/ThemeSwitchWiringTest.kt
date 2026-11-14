package ru.digitalpunk.lumi

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A switch between light and dark recreates the activity but not the process, so the application forwards it to
 * Gecko; then `prefers-color-scheme` follows without a restart. Wiring only, so this reads the source.
 */
class ThemeSwitchWiringTest {
    private val app = File("src/main/java/ru/digitalpunk/lumi/LumiApplication.kt").readText()

    @Test fun theApplicationForwardsAConfigurationChangeToAStartedEngineOnly() {
        val body = app.substringAfter("override fun onConfigurationChanged(newConfig: Configuration) {", "")
        assertTrue("super.onConfigurationChanged(newConfig)" in body, "no override that calls super")
        assertTrue("if (engineHolder.isInitialized()) engine.configurationChanged(newConfig)" in body.substringBefore("\n    }"))
    }
}

/** Gecko's content processes create the Application too; the update schedule must start only from the activity. */
class UpdateScheduleWiringTest {
    private val app = File("src/main/java/ru/digitalpunk/lumi/LumiApplication.kt").readText()
    private val activity = File("src/main/java/ru/digitalpunk/lumi/MainActivity.kt").readText()

    @Test fun theApplicationDoesNoWorkAtProcessStart() =
        assertTrue("override fun onCreate()" !in app, "Application.onCreate runs in every Gecko child process")

    @Test fun theActivityStartsFollowingTheUpdateSwitch() =
        assertTrue("app.followUpdateSwitch()" in activity)
}
