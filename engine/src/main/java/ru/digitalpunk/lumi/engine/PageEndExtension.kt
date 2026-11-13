package ru.digitalpunk.lumi.engine

import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * The built-in extension that ends an http(s) page taller than the screen with a block as tall as the collapsed
 * strip (see docs: 012-page-end-spacer-extension). It gets only the strip height and the view width.
 */
internal class PageEndExtension(private val runtime: GeckoRuntime) {
    private val spacer = PageEndSpacer()
    private var port: WebExtension.Port? = null

    private val portDelegate = object : WebExtension.PortDelegate {
        override fun onDisconnect(port: WebExtension.Port) {
            if (this@PageEndExtension.port === port) this@PageEndExtension.port = null
        }
    }

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            port.setDelegate(portDelegate)
            this@PageEndExtension.port = port
            spacer.current?.let { post(port, it) }
        }
    }

    fun start() {
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(PageEndSpacer.URI, PageEndSpacer.ID)
            .then { ext ->
                ext!!.setMessageDelegate(messageDelegate, PageEndSpacer.NATIVE_APP)
                if (ext.metaData.allowedInPrivateBrowsing) GeckoResult.fromValue(ext)
                else controller.setAllowedInPrivateBrowsing(ext, true)
            }
            .accept(
                { ext ->
                    ext!!.setMessageDelegate(messageDelegate, PageEndSpacer.NATIVE_APP)
                    Log.i(TAG, "page-end extension ready, private tabs ${ext.metaData.allowedInPrivateBrowsing}")
                },
                { Log.w(TAG, "page-end extension not installed", it) },
            )
    }

    /** Sends a new strip height or view width (px) to the extension; it is kept for a port that connects later. */
    fun update(stripPx: Int, viewWidthPx: Int) {
        val pair = spacer.update(stripPx, viewWidthPx) ?: return
        port?.let { post(it, pair) }
    }

    private fun post(port: WebExtension.Port, pair: Pair<Int, Int>) =
        port.postMessage(JSONObject().put("stripPx", pair.first).put("viewWidthPx", pair.second))
}

private const val TAG = "Lumi"
