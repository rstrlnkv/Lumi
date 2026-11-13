package ru.digitalpunk.lumi.data.icons

/** Deletes stored site icons that no bookmark needs any more. Blocking: call it off the main thread. */
interface IconRetention {
    fun forget(hosts: Set<String>)

    fun retainOnly(hosts: Set<String>)
}

/** The icon hosts of [urls]; folders (null), IP literals and non-web URLs have none. */
fun bookmarkIconHosts(urls: Collection<String?>): Set<String> = urls.mapNotNullTo(HashSet()) { url -> url?.let(::iconHost) }

/** Hosts that had a bookmark [before] a change and have none [after] it: their icons must go. */
fun orphanedIconHosts(before: Collection<String?>, after: Collection<String?>): Set<String> =
    bookmarkIconHosts(before) - bookmarkIconHosts(after)
