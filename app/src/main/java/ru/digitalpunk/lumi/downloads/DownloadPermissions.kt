package ru.digitalpunk.lumi.downloads

/** What to ask at a "Download" tap. Storage on API 26-28 only; notifications once, on API 33+. Never both on one device. */
data class PermissionPlan(val askStorage: Boolean, val askNotifications: Boolean)

fun permissionPlan(sdk: Int, storageGranted: Boolean, notificationsGranted: Boolean, notificationsAsked: Boolean) = PermissionPlan(
    askStorage = sdk <= 28 && !storageGranted,
    askNotifications = sdk >= 33 && !notificationsGranted && !notificationsAsked,
)

/** "Lumi can't show download progress outside the app" shows once per process, while notifications are off. */
fun showNoNotificationHint(enabled: Boolean, shownThisProcess: Boolean) = !enabled && !shownThisProcess
