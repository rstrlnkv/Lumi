package ru.digitalpunk.lumi.data.library

/** A page the address bar can suggest: a bookmark or a history URL, with its visit count and newest visit. */
data class SuggestPage(val url: String, val title: String, val visits: Int, val lastVisit: Long, val bookmark: Boolean)

/** One history URL, grouped: the last-inserted visit's title, the number of visits and the newest visit time. */
data class HistoryHit(val url: String, val title: String, val visits: Int, val lastVisit: Long)
