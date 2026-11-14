package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.StorageController.ClearFlags

/**
 * What "Delete browsing data" passes to Gecko. Never `PERMISSIONS` or `SITE_SETTINGS`: the protection exceptions and
 * the local network answers are Gecko permissions and stay (ADR 005, ADR 015).
 */
internal fun clearFlags(cookies: Boolean, cache: Boolean): Long =
    (if (cookies) ClearFlags.COOKIES or ClearFlags.DOM_STORAGES or ClearFlags.AUTH_SESSIONS else 0L) or
        (if (cache) ClearFlags.ALL_CACHES else 0L)

/**
 * The hosts one site's data is cleared from: the site key ([siteKey] drops `www.`) and its `www.` host, because Gecko's
 * host cleaner may match exact hosts and a site browsed only as `www.example.com` keeps its data there.
 */
internal fun siteDataHosts(key: String): List<String> = listOf(key, "www.$key")
