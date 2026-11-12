package ru.digitalpunk.lumi.privacy

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.TabsState

enum class Entry { Allow, NeedsUnlock, Refuse }

/** How a way into the private tabs goes: no screen lock refuses, locked tabs need an unlock, else at once. */
fun decideEntry(availability: LockAvailability, state: TabsState): Entry = when {
    availability != LockAvailability.Available -> Entry.Refuse
    state.privateLocked -> Entry.NeedsUnlock
    else -> Entry.Allow
}

/**
 * The prompt bookkeeping of [PrivateGate], without Android. Our own credential screen stops the activity; that stop is
 * not leaving the app, so it neither locks nor arms the auto-prompt (else a cancel would bring the prompt back in a loop).
 */
class UnlockPromptState {
    /** Set on each real leave; the screen prompts once on return and clears it. */
    var autoPromptPending by mutableStateOf(false)
    private var inFlight = false

    /** True when this stop is a real leave: lock, and prompt once on return. */
    fun onBackground(): Boolean {
        if (inFlight) return false
        autoPromptPending = true
        return true
    }

    /** True when a prompt may start now; one at a time. */
    fun start(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }

    fun end() {
        inFlight = false
    }
}

/**
 * The lock of the private tabs, for the whole process: it locks when Lumi goes to the background and unlocks after
 * the system prompt (a strong biometric or the device credential). The activity sets [prompter]; one prompt at a time.
 */
class PrivateGate(private val engine: BrowserEngine, private val context: Context) {
    private val prompts = UnlockPromptState()
    var autoPromptPending by prompts::autoPromptPending

    /** Private tabs were dropped (never a Retry); the screen tells the person once and clears it. Memory only. */
    var dropNoticePending by mutableStateOf(false)
    private var pending: (() -> Unit)? = null

    /** Shows the system prompt; set by the activity on every create. */
    var prompter: (() -> Unit)? = null

    fun availability(): LockAvailability =
        lockAvailability(BiometricManager.from(context).canAuthenticate(authenticators(Build.VERSION.SDK_INT)))

    fun onBackground() {
        if (prompts.onBackground()) engine.lockPrivate()
    }

    /** Runs [action] at once, after an unlock, or calls [refuse] when the device has no screen lock. */
    fun enter(action: () -> Unit, refuse: () -> Unit) = when (decideEntry(availability(), engine.tabs.value)) {
        Entry.Allow -> action()
        Entry.NeedsUnlock -> requestUnlock(action)
        Entry.Refuse -> refuse()
    }

    fun requestUnlock(then: () -> Unit = {}) {
        val show = prompter ?: return
        if (!prompts.start()) return
        pending = then
        show()
    }

    fun onPromptSucceeded() {
        prompts.end()
        engine.unlockPrivate()
        pending?.invoke()
        pending = null
    }

    /** Cancelled or failed: the tabs stay locked; the Unlock button asks again. */
    fun onPromptEnded() {
        prompts.end()
        pending = null
    }
}
