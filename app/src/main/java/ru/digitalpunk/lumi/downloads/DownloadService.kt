package ru.digitalpunk.lumi.downloads

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.LumiApplication

/**
 * Keeps the process alive while downloads run (foreground, `dataSync`); the copy itself runs in [DownloadManager]. Not
 * sticky: after a process death nothing can resume, Gecko's stream is gone.
 */
class DownloadService : Service() {
    private val scope = MainScope()
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as LumiApplication
        val downloads = app.downloads
        // Always first, within the system's time limit, even when everything already finished.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, DownloadNotifier.SUMMARY_ID, app.downloadNotifier.summary(downloads.activeCount.value.coerceAtLeast(1)), type)
        val id = intent?.getLongExtra(EXTRA_ID, 0) ?: 0
        when (intent?.action) {
            ACTION_CANCEL -> downloads.cancel(id)
            ACTION_RETRY -> downloads.retry(id)
        }
        if (!watching) {
            watching = true
            scope.launch {
                downloads.activeCount.collect { count ->
                    if (count > 0) {
                        app.downloadNotifier.updateSummary(count)
                    } else {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** API 35+: the daily `dataSync` time is used up. Running downloads end Failed, with Retry. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        (application as LumiApplication).downloads.interruptRunning()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "ru.digitalpunk.lumi.downloads.CANCEL"
        const val ACTION_RETRY = "ru.digitalpunk.lumi.downloads.RETRY"
        const val EXTRA_ID = "id"
    }
}
