package ru.digitalpunk.lumi.ui.settings

import androidx.compose.runtime.saveable.Saver

/** The screens of Settings; [Root] is the category list. */
enum class SettingsPage { Root, Appearance, Search, SearchEngines, Tabs, Privacy, Sites, SiteData, Unprotected, LocalNetwork, Updates, About }

/** The stack with [page] on top; the page already on top is not added twice. */
fun List<SettingsPage>.pushed(page: SettingsPage): List<SettingsPage> = if (lastOrNull() == page) this else this + page

/** The stack without its top page; null when only the root is left, which closes Settings. */
fun List<SettingsPage>.popped(): List<SettingsPage>? = if (size <= 1) null else dropLast(1)

/** The known names up to the first unknown one; the root alone when the stack does not start at the root. */
fun restoreStack(names: List<String>): List<SettingsPage> {
    val pages = names.map { name -> SettingsPage.entries.firstOrNull { it.name == name } }.takeWhile { it != null }.filterNotNull()
    return if (pages.firstOrNull() == SettingsPage.Root) pages else listOf(SettingsPage.Root)
}

/** Keeps the stack as page names, so it survives rotation and process death. */
val SettingsStackSaver: Saver<List<SettingsPage>, Any> = Saver(
    save = { stack -> ArrayList(stack.map { it.name }) },
    restore = { saved -> restoreStack((saved as? List<*>).orEmpty().filterIsInstance<String>()) },
)
