package ru.digitalpunk.lumi.defaultbrowser

import android.content.Intent
import ru.digitalpunk.lumi.data.MAX_ROLE_REFUSALS

enum class DefaultStep { RoleRequest, SystemSettings }

/** The default-browser card shows from this launcher start on. */
const val DEFAULT_CARD_AFTER_LAUNCHES = 3

/** The system dialog from API 29 while the role is available, not held and refused fewer than [MAX_ROLE_REFUSALS] times; else the system screen. */
fun defaultBrowserStep(sdk: Int, roleAvailable: Boolean, roleHeld: Boolean, refusals: Int): DefaultStep =
    if (sdk >= 29 && roleAvailable && !roleHeld && refusals < MAX_ROLE_REFUSALS) DefaultStep.RoleRequest else DefaultStep.SystemSettings

/** The browser role when the system has one ([roleHeld] not null); else whether a plain http link resolves to [own]. */
fun isLumiDefault(roleHeld: Boolean?, resolvedPackage: String?, own: String): Boolean = roleHeld ?: (resolvedPackage == own)

/** A refusal this fast means the system showed no dialog at all. */
fun refusedWithoutDialog(elapsedMillis: Long): Boolean = elapsedMillis < 400

/** A fresh start from the launcher; links, notifications and recreation do not count. */
fun launcherStart(savedStateNull: Boolean, action: String?, categories: Set<String>?): Boolean =
    savedStateNull && action == Intent.ACTION_MAIN && Intent.CATEGORY_LAUNCHER in categories.orEmpty()

fun showDefaultCard(launches: Int, dismissed: Boolean, isDefault: Boolean, inPrivateTab: Boolean): Boolean =
    launches >= DEFAULT_CARD_AFTER_LAUNCHES && !dismissed && !isDefault && !inPrivateTab
