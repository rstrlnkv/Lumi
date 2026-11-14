package ru.digitalpunk.lumi

import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.digitalpunk.lumi.data.ThemeChoice
import ru.digitalpunk.lumi.ui.theme.LocalDarkTheme
import ru.digitalpunk.lumi.ui.theme.LumiTheme
import ru.digitalpunk.lumi.ui.LocalSiteIcons
import android.app.KeyguardManager
import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ru.digitalpunk.lumi.defaultbrowser.DefaultBrowser
import ru.digitalpunk.lumi.defaultbrowser.launcherStart
import ru.digitalpunk.lumi.links.incomingIntent
import ru.digitalpunk.lumi.privacy.UnlockStart
import ru.digitalpunk.lumi.privacy.authenticators
import ru.digitalpunk.lumi.privacy.unlockStart
import ru.digitalpunk.lumi.ui.BrowserScreen

/** A [FragmentActivity] because the system unlock prompt (`BiometricPrompt`) needs one. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LumiApplication
        val gate = app.privateGate
        val title = getString(R.string.private_prompt_title)
        // API 28-29: the device credential alone, through the system screen (androidx.biometric 1.1.0 allows it only
        // from API 30). Not the ActivityResult API: the Fragment 1.2.5 that androidx.biometric brings refuses its codes.
        val askCredential: () -> Unit = {
            @Suppress("DEPRECATION")
            val intent = getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent(title, null)
            @Suppress("DEPRECATION")
            if (intent == null) gate.onPromptEnded() else startActivityForResult(intent, CONFIRM_CREDENTIAL)
        }
        // Made on every create, so a prompt that is up during rotation reports to this activity.
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = gate.onPromptSucceeded()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) askCredential() else gate.onPromptEnded()
            },
        )
        // No negative button: it is not allowed together with the device credential.
        val onePrompt = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(authenticators(Build.VERSION.SDK_INT))
            .build()
        val strongPrompt = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText(getString(R.string.private_prompt_use_pin))
            .build()
        val prompter = {
            val strong = BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
            when (unlockStart(Build.VERSION.SDK_INT, strong)) {
                UnlockStart.OnePrompt -> prompt.authenticate(onePrompt)
                UnlockStart.StrongBiometric -> prompt.authenticate(strongPrompt)
                UnlockStart.DeviceCredential -> askCredential()
            }
        }
        gate.prompter = prompter
        app.historyRecorder.start()
        app.tabPersistence.start()
        app.privatePersistence.start()
        app.tabSnapshots.start()
        app.followUpdateSwitch()
        val asks = app.downloadAsks
        asks.start()
        val requester = { permission: String, code: Int -> ActivityCompat.requestPermissions(this, arrayOf(permission), code) }
        asks.requester = requester
        val localNetwork = app.localNetworkAsks
        localNetwork.requester = requester
        app.resetLnaDenialsOnce()
        if (savedInstanceState == null) readDownloadsRequest(intent)
        if (savedInstanceState == null) readLink(intent)
        val defaultBrowser = app.defaultBrowser
        if (launcherStart(savedInstanceState == null, intent.action, intent.categories)) defaultBrowser.countLaunch()
        @Suppress("DEPRECATION")
        val starter = { request: Intent, code: Int -> startActivityForResult(request, code) }
        defaultBrowser.starter = starter
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = app.privatePersistence.onStart()

            // The person may have changed the default browser in the system settings.
            override fun onResume(owner: LifecycleOwner) = defaultBrowser.refresh()

            override fun onStop(owner: LifecycleOwner) {
                app.tabSnapshots.captureNow()
                app.tabPersistence.flushNow()
                app.privatePersistence.flushNow()
                // Rotation is not leaving the app.
                if (!isChangingConfigurations) gate.onBackground()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                if (gate.prompter === prompter) gate.prompter = null
                if (asks.requester === requester) asks.requester = null
                if (localNetwork.requester === requester) localNetwork.requester = null
                if (defaultBrowser.starter === starter) defaultBrowser.starter = null
            }
        })
        setContent {
            // Nothing is drawn until both are read, so a forced theme never flashes the device's first.
            val storedTheme by app.settings.theme.collectAsStateWithLifecycle<ThemeChoice?>(null)
            val storedWallpaper by app.settings.wallpaperColors.collectAsStateWithLifecycle<Boolean?>(null)
            val theme = storedTheme ?: return@setContent
            val wallpaperColors = storedWallpaper ?: return@setContent
            LumiTheme(theme, wallpaperColors) {
                // The bar icons follow Lumi's theme, not the device's, when a theme is forced.
                val dark = LocalDarkTheme.current
                LaunchedEffect(dark) {
                    enableEdgeToEdge(
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark },
                    )
                }
                Surface(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalSiteIcons provides app.siteIcons) {
                        BrowserScreen(app.engine, app.settings, app.tabSnapshots, gate, app.library, app.suggestClient, app.downloads, asks, app.showDownloads, app.appLinks, defaultBrowser)
                    }
                }
            }
        }
    }

    @Deprecated("The platform result path; see askCredential in onCreate.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val app = application as LumiApplication
        when (requestCode) {
            CONFIRM_CREDENTIAL -> if (resultCode == RESULT_OK) app.privateGate.onPromptSucceeded() else app.privateGate.onPromptEnded()
            DefaultBrowser.REQUEST_ROLE -> app.defaultBrowser.onRoleResult(resultCode)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDownloadsRequest(intent)
        readLink(intent)
    }

    /**
     * A link or web search from another app goes to the inbox; the browser screen opens it once the tabs are restored.
     * Not from Recents: that intent is the old one. Capped in [incomingIntent].
     */
    private fun readLink(intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        // A hostile extras bundle can throw when it is unparcelled.
        val incoming = runCatching {
            incomingIntent(intent.action, intent.dataString, intent.getStringExtra(SearchManager.QUERY))
        }.getOrNull() ?: return
        (application as LumiApplication).appLinks.offer(incoming)
    }

    private fun readDownloadsRequest(intent: Intent?) {
        if (intent?.action == ACTION_SHOW_DOWNLOADS) (application as LumiApplication).showDownloads.value = true
    }

    @Deprecated("The platform result path; the ActivityResult API is out, see askCredential in onCreate.")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        (application as LumiApplication).downloadAsks.onPermissionResult(requestCode, granted)
        (application as LumiApplication).localNetworkAsks.onPermissionResult(requestCode, granted)
    }

    companion object {
        private const val CONFIRM_CREDENTIAL = 1

        /** The navigation-bar scrims of `enableEdgeToEdge()`'s defaults, for three-button navigation. */
        private const val LIGHT_NAV_SCRIM = 0xE6FFFFFF.toInt()
        private const val DARK_NAV_SCRIM = 0x801B1B1B.toInt()

        /** A download notification: open the Library on the Downloads tab. */
        const val ACTION_SHOW_DOWNLOADS = "ru.digitalpunk.lumi.SHOW_DOWNLOADS"
    }
}
