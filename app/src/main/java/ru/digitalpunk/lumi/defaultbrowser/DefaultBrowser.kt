package ru.digitalpunk.lumi.defaultbrowser

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.data.MAX_ROLE_REFUSALS
import ru.digitalpunk.lumi.data.SettingsStore

/**
 * Whether Lumi is the default browser, and the one way to ask for it, shared by the Settings row and the start-page card:
 * the system's browser-role dialog (API 29+), else the system's default-apps screen.
 */
class DefaultBrowser(private val context: Context, private val settings: SettingsStore, private val scope: CoroutineScope) {
    private val mutableIsDefault = MutableStateFlow(check())
    val isDefault: StateFlow<Boolean> = mutableIsDefault.asStateFlow()

    /** Starts a system screen for a result; set by the activity, cleared when it goes. */
    var starter: ((Intent, Int) -> Unit)? = null

    private var requestedAt = 0L

    /** Reads the state again; on every resume, since the person can change it in the system settings. */
    fun refresh() {
        mutableIsDefault.value = check()
    }

    fun countLaunch() {
        scope.launch { settings.countLaunch() }
    }

    fun request() {
        scope.launch {
            val held = roleHeld()
            when (defaultBrowserStep(Build.VERSION.SDK_INT, held != null, held == true, settings.roleRefusals.first())) {
                DefaultStep.RoleRequest -> roleIntent()?.let {
                    requestedAt = SystemClock.elapsedRealtime()
                    starter?.invoke(it, REQUEST_ROLE)
                }
                DefaultStep.SystemSettings -> openSystemSettings()
            }
        }
    }

    /** The answer to the role dialog. A refusal with no dialog shown means the system stopped asking: go to its screen at once. */
    fun onRoleResult(resultCode: Int) {
        scope.launch {
            when {
                resultCode == Activity.RESULT_OK -> settings.setRoleRefusals(0)
                refusedWithoutDialog(SystemClock.elapsedRealtime() - requestedAt) -> {
                    settings.setRoleRefusals(MAX_ROLE_REFUSALS)
                    openSystemSettings()
                }
                else -> settings.setRoleRefusals(settings.roleRefusals.first() + 1)
            }
            refresh()
        }
    }

    private fun openSystemSettings() {
        val start = starter ?: return
        try {
            start(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), NO_RESULT)
        } catch (_: ActivityNotFoundException) {
            try {
                start(Intent(Settings.ACTION_SETTINGS), NO_RESULT)
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    private fun check(): Boolean {
        val held = roleHeld()
        // Only without the role: the app that a plain http link goes to ("android", the chooser, when there is no default).
        @Suppress("DEPRECATION")
        val resolved = if (held != null) null else context.packageManager
            .resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://")), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return isLumiDefault(held, resolved, context.packageName)
    }

    /** Null when the system has no browser role (before API 29, or not on this device). */
    private fun roleHeld(): Boolean? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)?.takeIf { it.isRoleAvailable(RoleManager.ROLE_BROWSER) }?.isRoleHeld(RoleManager.ROLE_BROWSER)
    } else {
        null
    }

    private fun roleIntent(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
    } else {
        null
    }

    companion object {
        /** The request code of the role dialog, next to the device-credential screen's 1. */
        const val REQUEST_ROLE = 2
        private const val NO_RESULT = -1
    }
}
