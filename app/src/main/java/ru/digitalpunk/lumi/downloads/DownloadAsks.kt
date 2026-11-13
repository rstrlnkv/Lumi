package ru.digitalpunk.lumi.downloads

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.DownloadOffer

/** The most files waiting for the ask sheet; each holds an open response stream. */
const val MAX_ASKS = 5

/** Files waiting for the ask sheet, in arrival order; the head is shown. Every way out of the queue closes or hands on the stream. */
class AskQueue {
    private val mutable = MutableStateFlow<List<DownloadOffer>>(emptyList())
    val offers: StateFlow<List<DownloadOffer>> = mutable

    /** A page that sends file after file cannot pile up sheets: past [MAX_ASKS] a new offer is discarded. */
    fun add(offer: DownloadOffer) {
        if (mutable.value.size >= MAX_ASKS) offer.discard() else mutable.update { it + offer }
    }

    /** Takes [offer] out without closing it: the caller owns it now. */
    fun remove(offer: DownloadOffer) = mutable.update { list -> list.filter { it !== offer } }

    fun cancel(offer: DownloadOffer) {
        remove(offer)
        offer.discard()
    }

    /** The last private tab closed: its files are not asked about any more. */
    fun dropPrivate() = mutable.value.filter { it.inPrivateTab }.forEach(::cancel)
}

/**
 * The ask sheet's queue and the permissions a "Download" tap needs (design section 5), for the process: a permission
 * dialog survives rotation. [requester] is set by the activity; it asks the system with a request code.
 */
class DownloadAsks(
    private val context: Context,
    private val engine: BrowserEngine,
    private val downloads: DownloadManager,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val queue = AskQueue()
    val offers: StateFlow<List<DownloadOffer>> = queue.offers
    var requester: ((permission: String, code: Int) -> Unit)? = null
    private var held: DownloadOffer? = null
    private var notificationsAsked = false
    private var asking = false
    private var hintShown = false
    private var started = false
    private val mutableHints = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Time to show "Lumi can't show download progress outside the app · Settings". */
    val hints: SharedFlow<Unit> = mutableHints

    fun start() {
        if (started) return
        started = true
        scope.launch { engine.downloads.collect(queue::add) }
        scope.launch { settings.downloadNotificationsAsked.collect { notificationsAsked = it } }
        scope.launch { engine.tabs.map { it.privateCount }.distinctUntilChanged().collect { if (it == 0) queue.dropPrivate() } }
    }

    fun cancel(offer: DownloadOffer) = queue.cancel(offer)

    fun confirm(offer: DownloadOffer) {
        queue.remove(offer)
        val request = requester
        val plan = permissionPlan(Build.VERSION.SDK_INT, granted(Manifest.permission.WRITE_EXTERNAL_STORAGE), granted(NOTIFICATIONS), notificationsAsked)
        if (plan.askStorage && request != null) {
            held?.let(downloads::refuse)
            held = offer
            request(Manifest.permission.WRITE_EXTERNAL_STORAGE, REQUEST_STORAGE)
            return
        }
        if (plan.askNotifications && request != null) {
            notificationsAsked = true
            asking = true
            scope.launch { settings.setDownloadNotificationsAsked() }
            request(NOTIFICATIONS, REQUEST_NOTIFICATIONS)
        }
        // The download never waits for the notification answer.
        downloads.start(offer)
        if (!asking) hintIfMuted()
    }

    fun onPermissionResult(code: Int, granted: Boolean) {
        when (code) {
            REQUEST_STORAGE -> held?.let { offer ->
                held = null
                if (granted) {
                    downloads.start(offer)
                    hintIfMuted()
                } else {
                    downloads.refuse(offer)
                }
            }
            REQUEST_NOTIFICATIONS -> {
                asking = false
                hintIfMuted()
            }
        }
    }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun hintIfMuted() {
        if (!showNoNotificationHint(NotificationManagerCompat.from(context).areNotificationsEnabled(), hintShown)) return
        hintShown = true
        mutableHints.tryEmit(Unit)
    }

    companion object {
        const val REQUEST_STORAGE = 2
        const val REQUEST_NOTIFICATIONS = 3
        private const val NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    }
}
