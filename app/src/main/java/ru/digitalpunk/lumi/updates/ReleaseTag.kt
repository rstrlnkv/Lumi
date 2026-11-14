package ru.digitalpunk.lumi.updates

import ru.digitalpunk.lumi.about.Channel

private val TAG = Regex("""v(\d+)\.(\d+)\.(\d+)(?:\.(DEV|BETA)\.(\d+))?""")
private val VERSION = Regex("""(\d+)\.(\d+)\.(\d+)""")

/** A build as the versioning standard tags it; a STABLE tag carries no build number. */
data class ReleaseTag(val major: Int, val minor: Int, val patch: Int, val channel: Channel, val build: Long?) : Comparable<ReleaseTag> {
    val display: String get() = "$major.$minor.$patch" + if (channel == Channel.STABLE) "" else " ${channel.name} (Build $build)"

    /** Version first; a STABLE release is above every DEV and BETA build of its version; then the build. */
    override fun compareTo(other: ReleaseTag): Int = compareValuesBy(
        this, other, { it.major }, { it.minor }, { it.patch }, { it.channel == Channel.STABLE }, { it.build ?: 0 },
    )
}

/** `vX.Y.Z`, `vX.Y.Z.DEV.N` or `vX.Y.Z.BETA.N`; anything else, or a number out of range, is not a release tag. */
fun parseTag(tag: String): ReleaseTag? {
    val m = TAG.matchEntire(tag) ?: return null
    val (major, minor, patch, channel, build) = m.destructured
    return ReleaseTag(
        major.toIntOrNull() ?: return null,
        minor.toIntOrNull() ?: return null,
        patch.toIntOrNull() ?: return null,
        if (channel.isEmpty()) Channel.STABLE else Channel.valueOf(channel),
        if (build.isEmpty()) null else build.toLongOrNull() ?: return null,
    )
}

/** The running build as a tag, or null when its version name is not plain `X.Y.Z`. */
fun installedTag(versionName: String, channel: Channel, versionCode: Long): ReleaseTag? {
    val (major, minor, patch) = VERSION.matchEntire(versionName)?.destructured ?: return null
    return ReleaseTag(major.toIntOrNull() ?: return null, minor.toIntOrNull() ?: return null, patch.toIntOrNull() ?: return null, channel, versionCode)
}

/** DEV gets every build, BETA gets BETA and STABLE, STABLE only STABLE. */
fun Channel.sees(other: Channel): Boolean = other.ordinal >= ordinal
