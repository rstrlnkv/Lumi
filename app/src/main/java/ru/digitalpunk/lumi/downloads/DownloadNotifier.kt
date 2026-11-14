package ru.digitalpunk.lumi.downloads

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.digitalpunk.lumi.MainActivity
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.data.downloads.DownloadItem

/** The "Downloads" channel: one notification per download plus the service's summary. A private one never carries the file name. */
class DownloadNotifier(context: Context) {
    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)
    private val privateTitle get() = context.getString(R.string.notification_private_download)

    /** The foreground notification of [DownloadService]; its text never names a file. */
    fun summary(count: Int): Notification = builder()
        .setContentTitle(context.resources.getQuantityString(R.plurals.downloads_active, count, count))
        .setGroupSummary(true)
        .setOngoing(true)
        .setContentIntent(showDownloads())
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    fun updateSummary(count: Int) = post(SUMMARY_ID, summary(count))

    fun progress(item: DownloadItem) {
        val pct = percent(item.doneBytes, item.totalBytes)
        post(item, builder()
            .setContentTitle(notificationTitle(item, privateTitle))
            .setContentText(progressLine(item.doneBytes, item.totalBytes, ::size) { a, b -> context.getString(R.string.download_progress, a, b) })
            .setProgress(100, pct ?: 0, pct == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(showDownloads())
            .addAction(0, context.getString(R.string.download_cancel_action), service(DownloadService.ACTION_CANCEL, item.id)))
    }

    fun done(item: DownloadItem) {
        val open = FileOpener.openIntent(context, item)?.let { PendingIntent.getActivity(context, idFor(item.id), it, FLAGS) } ?: showDownloads()
        post(item, builder()
            .setContentTitle(notificationTitle(item, privateTitle))
            .setContentText(context.getString(R.string.notification_download_complete))
            .setAutoCancel(true)
            .setContentIntent(open))
    }

    fun failed(item: DownloadItem) {
        val builder = builder()
            .setContentTitle(notificationTitle(item, privateTitle))
            .setContentText(context.getString(R.string.notification_download_failed))
            .setAutoCancel(true)
            .setContentIntent(showDownloads())
        if (notificationCanRetry(item)) builder.addAction(0, context.getString(R.string.action_retry), service(DownloadService.ACTION_RETRY, item.id))
        post(item, builder)
    }

    fun cancel(item: DownloadItem) = manager.cancel(idFor(item.id))

    private fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    private fun builder(): NotificationCompat.Builder {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW).setName(context.getString(R.string.notification_channel_downloads)).build(),
        )
        return NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_lumi).setGroup(GROUP)
    }

    private fun post(item: DownloadItem, builder: NotificationCompat.Builder) {
        if (item.isPrivate) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(builder().setContentTitle(privateTitle).build())
        }
        post(idFor(item.id), builder.build())
    }

    private fun post(id: Int, notification: Notification) {
        // Refused or switched off: silent. The download goes on either way.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun showDownloads(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        FLAGS,
    )

    private fun service(action: String, id: Long): PendingIntent = PendingIntent.getForegroundService(
        context, idFor(id), Intent(context, DownloadService::class.java).setAction(action).putExtra(DownloadService.EXTRA_ID, id), FLAGS,
    )

    companion object {
        const val SUMMARY_ID = 1
        private const val CHANNEL = "downloads"
        private const val GROUP = "downloads"
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        /** Room ids (positive) map to 1000 and up, private ids (negative) to -1000 and down: the two never meet, nor [SUMMARY_ID]. */
        fun idFor(id: Long) = if (id < 0) -1000 - (-id % 1_000_000_000).toInt() else 1000 + (id % 1_000_000_000).toInt()
    }
}
