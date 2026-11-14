package ru.digitalpunk.lumi.ui.settings

import android.util.Log
import ru.digitalpunk.lumi.data.ClearItem
import ru.digitalpunk.lumi.data.library.ClearRange
import ru.digitalpunk.lumi.data.library.LibraryRepository
import ru.digitalpunk.lumi.engine.BrowserEngine
import java.time.ZoneId

/** Delete is enabled while at least one box is checked. */
fun canDelete(choice: Set<ClearItem>): Boolean = choice.isNotEmpty()

/**
 * Runs "Delete browsing data": the normal tabs close first (their sessions go before Gecko clears), then Gecko's cookies
 * and caches, then the history. Bookmarks, protection exceptions, local network answers, downloads and settings stay.
 * False when Gecko failed; the history is still cleared then.
 */
suspend fun deleteBrowsingData(choice: Set<ClearItem>, engine: BrowserEngine, library: LibraryRepository, zone: ZoneId): Boolean {
    if (ClearItem.Tabs in choice) engine.closeNormalTabs()
    val cookies = ClearItem.Cookies in choice
    val cache = ClearItem.Cache in choice
    val engineDone = !(cookies || cache) || runCatching { engine.clearBrowsingData(cookies, cache) }
        .onFailure { Log.e(TAG, "Clearing Gecko data failed", it) }.isSuccess
    if (ClearItem.History in choice) library.clearHistory(ClearRange.AllTime, zone)
    return engineDone
}

private const val TAG = "DeleteBrowsingData"
