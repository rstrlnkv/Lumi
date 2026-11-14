package ru.digitalpunk.lumi.net

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import ru.digitalpunk.lumi.engine.ACCESS_LOCAL_NETWORK

/**
 * Gecko's ask for the Android 17 local network permission (ADR 015), for the process. [requester] is set by the activity;
 * it shows the system's Nearby devices dialog with a request code. With no activity the ask is refused.
 */
class LocalNetworkAsks(private val context: Context) {
    var requester: ((permission: String, code: Int) -> Unit)? = null
    private val waiting = mutableListOf<(Boolean) -> Unit>()

    fun ask(permissions: List<String>, answer: (Boolean) -> Unit) {
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) return answer(true)
        val request = requester ?: return answer(false)
        waiting += answer
        if (waiting.size == 1) request(ACCESS_LOCAL_NETWORK, REQUEST_LOCAL_NETWORK)
    }

    fun onPermissionResult(code: Int, granted: Boolean) {
        if (code != REQUEST_LOCAL_NETWORK) return
        val answers = waiting.toList()
        waiting.clear()
        answers.forEach { it(granted) }
    }

    companion object {
        /** After the download codes (2, 3) of [ru.digitalpunk.lumi.downloads.DownloadAsks]. */
        const val REQUEST_LOCAL_NETWORK = 4
    }
}
