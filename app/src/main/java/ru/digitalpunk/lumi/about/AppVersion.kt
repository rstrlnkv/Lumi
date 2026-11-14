package ru.digitalpunk.lumi.about

/** The build's release channel (versioning standard). */
enum class Channel {
    DEV, BETA, STABLE;

    companion object {
        /** From `BuildConfig.CHANNEL`; any other name is DEV, the least promised channel. */
        fun of(name: String): Channel = entries.firstOrNull { it.name == name } ?: DEV
    }
}

/** The running app's version name, build number and channel, as the About screen shows them. */
data class AppVersion(val name: String, val code: Long, val channel: Channel) {
    val copyText: String get() = "$name ${channel.name} (Build $code)"

    /** A STABLE build shows the plain version until the person taps it; DEV and BETA always show the build. */
    fun shown(revealed: Boolean): String = if (channel == Channel.STABLE && !revealed) name else copyText
}

/** From API 33 the system shows its own confirmation when an app copies to the clipboard. */
fun showCopiedSnackbar(sdkInt: Int): Boolean = sdkInt < 33
