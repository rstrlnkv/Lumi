package ru.digitalpunk.lumi

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = app.privatePersistence.onStart()

            override fun onStop(owner: LifecycleOwner) {
                app.tabSnapshots.captureNow()
                app.tabPersistence.flushNow()
                app.privatePersistence.flushNow()
                // Rotation is not leaving the app.
                if (!isChangingConfigurations) gate.onBackground()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                if (gate.prompter === prompter) gate.prompter = null
            }
        })
        setContent {
            LumiTheme {
                Surface(Modifier.fillMaxSize()) {
                    BrowserScreen(app.engine, app.settings, app.tabSnapshots, gate, app.library)
                }
            }
        }
    }

    @Deprecated("The platform result path; see askCredential in onCreate.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CONFIRM_CREDENTIAL) return
        val gate = (application as LumiApplication).privateGate
        if (resultCode == RESULT_OK) gate.onPromptSucceeded() else gate.onPromptEnded()
    }

    private companion object {
        const val CONFIRM_CREDENTIAL = 1
    }
}

/** Follows the system light/dark setting; Dynamic Color on Android 12+. */
@Composable
fun LumiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
