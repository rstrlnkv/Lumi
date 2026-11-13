package ru.digitalpunk.lumi.downloads

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import ru.digitalpunk.lumi.data.downloads.DownloadItem
import java.io.File

/** Opens and shares downloaded files through the system chooser. No `file:` URI leaves the app: API 26-28 files go through FileProvider. */
object FileOpener {
    enum class Result { Started, NoApp, Gone }

    fun contentUri(context: Context, item: DownloadItem): Uri? {
        val stored = item.uri ?: return null
        if (stored.startsWith("content:")) return Uri.parse(stored)
        return runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", File(stored)) }.getOrNull()
    }

    /** The chooser for "Open", or null when the file has no URI. The grant rides on the chooser's clip, so it reaches the chosen app. */
    fun openIntent(context: Context, item: DownloadItem): Intent? {
        val uri = contentUri(context, item) ?: return null
        return chooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mimeType), uri)
    }

    fun open(context: Context, item: DownloadItem): Result {
        val uri = contentUri(context, item) ?: return Result.Gone
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mimeType)
        if (context.packageManager.queryIntentActivities(view, 0).isEmpty()) return Result.NoApp
        return start(context, chooser(view, uri))
    }

    fun share(context: Context, item: DownloadItem): Result {
        val uri = contentUri(context, item) ?: return Result.Gone
        return start(context, chooser(Intent(Intent.ACTION_SEND).setType(item.mimeType).putExtra(Intent.EXTRA_STREAM, uri), uri))
    }

    private fun chooser(target: Intent, uri: Uri): Intent {
        target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(target, null).apply {
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun start(context: Context, intent: Intent): Result = try {
        context.startActivity(intent)
        Result.Started
    } catch (_: ActivityNotFoundException) {
        Result.NoApp
    } catch (_: SecurityException) {
        Result.Gone
    }
}
