package ru.digitalpunk.lumi.about

private val DEV = Regex("""-dev\.\d+$""")

/** The running app's version name and build number (the commit count), as the About screen shows them. */
data class AppVersion(val name: String, val code: Long) {
    val isDev: Boolean get() = DEV.containsMatchIn(name)
    val copyText: String get() = "$name ($code)"
}

/** From API 33 the system shows its own confirmation when an app copies to the clipboard. */
fun showCopiedSnackbar(sdkInt: Int): Boolean = sdkInt < 33
