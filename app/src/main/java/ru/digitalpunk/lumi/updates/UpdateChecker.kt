package ru.digitalpunk.lumi.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.digitalpunk.lumi.BuildConfig
import ru.digitalpunk.lumi.about.Channel
import ru.digitalpunk.lumi.about.installedChannel
import ru.digitalpunk.lumi.data.SettingsStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** GitHub's release list of the project; public, so no token. A fixed host, fetched outside Gecko (ADR 017). */
private const val FEED = "https://api.github.com/repos/rstrlnkv/lumi/releases?per_page=30"
private const val MAX_BODY = 1_048_576
private const val TIMEOUT_MS = 10_000

sealed interface CheckResult {
    data class Available(val release: Release) : CheckResult
    data object UpToDate : CheckResult
    data object Failed : CheckResult
}

/** The bytes of [input], or null once they pass [max]. */
fun readCapped(input: InputStream, max: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) return out.toByteArray()
        if (out.size() + n > max) return null
        out.write(buffer, 0, n)
    }
}

/** [block]'s result, or [CheckResult.Failed] for any exception but a cancellation: a check never crashes the app or the job. */
suspend fun guarded(block: suspend () -> CheckResult): CheckResult = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    CheckResult.Failed
}

/** The release to announce: a newer one not announced before. */
fun shouldNotify(result: CheckResult, notifiedTag: String?): Release? =
    (result as? CheckResult.Available)?.release?.takeIf { it.name != notifiedTag }

/** Asks GitHub for a build newer than this one in the chosen channel; the daily job and "Check now" share it. */
class UpdateChecker(private val settings: SettingsStore, private val notifier: UpdateNotifier) {
    suspend fun check(): CheckResult = guarded { withContext(Dispatchers.IO) { checkOnce() } }

    private suspend fun checkOnce(): CheckResult {
        val installed = installedTag(BuildConfig.VERSION_NAME, installedChannel, BuildConfig.VERSION_CODE.toLong())
            ?: return CheckResult.Failed
        val releases = fetch()?.let(::parseReleases) ?: return CheckResult.Failed
        settings.setLastUpdateCheck(System.currentTimeMillis())
        val channel = settings.updateChannel.first()?.let(Channel::of) ?: installedChannel
        return newestUpdate(releases, installed, channel)?.let { CheckResult.Available(it) } ?: CheckResult.UpToDate
    }

    /** The daily job: announce a new release once. False when the check failed and the job should retry. */
    suspend fun checkAndNotify(): Boolean = guarded {
        val result = check()
        shouldNotify(result, settings.notifiedUpdateTag.first())?.let { if (notifier.post(it)) settings.setNotifiedUpdateTag(it.name) }
        result
    } != CheckResult.Failed

    private fun fetch(): String? = try {
        val connection = URL(FEED).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Lumi")
            if (connection.responseCode != 200) null
            else connection.inputStream.use { readCapped(it, MAX_BODY) }?.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    } catch (_: IOException) {
        null
    }
}
