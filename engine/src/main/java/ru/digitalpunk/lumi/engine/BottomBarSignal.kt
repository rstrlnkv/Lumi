package ru.digitalpunk.lumi.engine

import org.json.JSONObject
import org.mozilla.geckoview.WebExtension

/**
 * The page-end content script's report that the page has a bar stuck to the screen bottom (see docs:
 * 012-page-end-spacer-extension): `{bottomBar: Boolean}` from our extension's content script in the top frame.
 * Null for any other message or sender.
 */
internal fun bottomBarReport(message: Any?, topLevel: Boolean, envType: Int, extensionId: String?): Boolean? {
    if (!topLevel || envType != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT || extensionId != PageEndSpacer.ID) return null
    val json = message as? JSONObject ?: return null
    return if (json.length() == 1) json.opt("bottomBar") as? Boolean else null
}
