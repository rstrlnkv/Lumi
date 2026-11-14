package ru.digitalpunk.lumi.updates

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.digitalpunk.lumi.MainActivity
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.links.ACTION_OPEN_UPDATE

private const val REQUEST_UPDATE_NOTIFICATIONS = 40

private fun notificationsAllowed(context: Context) =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Android 13+: asks for the notification permission when it is missing. The answer needs no handling: the next check reads it. */
fun askUpdateNotifications(activity: Activity) {
    if (!notificationsAllowed(activity)) {
        // The platform path, not the ActivityResult API: see askCredential in MainActivity.
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_UPDATE_NOTIFICATIONS)
    }
}

/** The "Updates" channel: one notification for a newer build; a tap opens its release page in a new tab. */
class UpdateNotifier(context: Context) {
    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    /** False when nothing was shown: no permission, or the system refused. */
    fun post(release: Release): Boolean {
        if (!notificationsAllowed(context)) return false
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_updates)).build(),
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_UPDATE).setData(release.url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lumi)
            .setContentTitle(context.getString(R.string.notification_update_title, release.tag.display))
            .setContentText(context.getString(R.string.notification_update_text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        return try {
            manager.notify(TAG, 0, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val CHANNEL = "updates"
        // A tag of its own, so the id never meets a download notification's.
        const val TAG = "update"
    }
}
