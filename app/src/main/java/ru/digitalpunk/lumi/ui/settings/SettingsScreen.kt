package ru.digitalpunk.lumi.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.about.Channel
import ru.digitalpunk.lumi.about.installedChannel
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.data.library.LibraryRepository
import ru.digitalpunk.lumi.data.search.SearchEngine
import ru.digitalpunk.lumi.defaultbrowser.DefaultBrowser
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.ui.about.AboutHost
import ru.digitalpunk.lumi.ui.about.rememberAppVersion

/**
 * Settings over the browser: the category list ([SettingsPage.Root]) and one back stack of pages over it. Back pops one
 * page; on the root it closes Settings through [onBack]. Only the top page is composed; the pages under it keep their
 * saved state. Links from About open through [onOpenUrl].
 */
@Composable
fun SettingsScreen(
    settings: SettingsStore,
    library: LibraryRepository,
    @Suppress("UNUSED_PARAMETER") engine: BrowserEngine,
    defaultBrowser: DefaultBrowser,
    @Suppress("UNUSED_PARAMETER") snackbar: SnackbarHostState,
    scope: CoroutineScope,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stack by rememberSaveable(stateSaver = SettingsStackSaver) { mutableStateOf(listOf(SettingsPage.Root)) }
    val pages = rememberSaveableStateHolder()
    val open: (SettingsPage) -> Unit = { stack = stack.pushed(it) }
    val back: () -> Unit = {
        val top = stack.last()
        val rest = stack.popped()
        if (rest == null) {
            onBack()
        } else {
            stack = rest
            pages.removeState(top.name)
        }
    }
    BackHandler(onBack = back)
    AnimatedContent(
        targetState = stack,
        modifier = modifier,
        contentKey = { it.last() },
        transitionSpec = {
            if (targetState.size >= initialState.size) {
                (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
            }
        },
        label = "settings",
    ) { shown ->
        val page = shown.last()
        val full = Modifier.fillMaxSize()
        pages.SaveableStateProvider(page.name) {
            when (page) {
                SettingsPage.Root -> RootPage(settings, defaultBrowser, open, back, full)
                SettingsPage.Search -> SearchSettings(settings, scope, { open(SettingsPage.SearchEngines) }, back, full)
                SettingsPage.SearchEngines -> {
                    val engine by settings.searchEngine.collectAsStateWithLifecycle(SearchEngine.Google)
                    SearchEngineScreen(engine, onPick = { scope.launch { settings.setSearchEngine(it) }; back() }, onBack = back, modifier = full)
                }
                SettingsPage.Tabs -> TabsSettings(settings, library, scope, back, full)
                SettingsPage.Updates -> UpdatesSettings(settings, scope, back, full)
                SettingsPage.About -> AboutHost(onBack = back, onOpenUrl = onOpenUrl, modifier = full)
                SettingsPage.Appearance -> SettingsPageScaffold(stringResource(R.string.settings_appearance), back, full) {}
                SettingsPage.Privacy -> SettingsPageScaffold(stringResource(R.string.settings_privacy), back, full) {}
                SettingsPage.Sites, SettingsPage.SiteData, SettingsPage.Unprotected, SettingsPage.LocalNetwork ->
                    SettingsPageScaffold(stringResource(R.string.settings_sites), back, full) {}
            }
        }
    }
}

/** S1: the default-browser card (only while Lumi is not the default), then the categories in three groups. */
@Composable
private fun RootPage(settings: SettingsStore, defaultBrowser: DefaultBrowser, open: (SettingsPage) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    val isDefault by defaultBrowser.isDefault.collectAsStateWithLifecycle()
    val engine by settings.searchEngine.collectAsStateWithLifecycle(SearchEngine.Google)
    val suggestions by settings.searchSuggestions.collectAsStateWithLifecycle(true)
    val updatesOn by settings.updatesOn.collectAsStateWithLifecycle(true)
    val channel by settings.updateChannel.collectAsStateWithLifecycle(null)
    val colors = MaterialTheme.colorScheme
    fun Modifier.opens(page: SettingsPage) = clickable(role = Role.Button) { open(page) }
    SettingsPageScaffold(stringResource(R.string.settings_title), onBack, modifier) {
        if (!isDefault) DefaultCard(onSet = defaultBrowser::request)
        SettingsGroup {
            CategoryRow(
                stringResource(R.string.settings_appearance), stringResource(R.string.settings_appearance_summary),
                painterResource(R.drawable.ic_palette), colors.primaryContainer, colors.onPrimaryContainer, Modifier.opens(SettingsPage.Appearance),
            )
            CategoryRow(
                stringResource(R.string.settings_section_search),
                stringResource(if (suggestions) R.string.settings_search_summary_on else R.string.settings_search_summary_off, engine.displayName()),
                rememberVectorPainter(Icons.Filled.Search),
                colors.secondaryContainer, colors.onSecondaryContainer, Modifier.opens(SettingsPage.Search),
            )
            CategoryRow(
                stringResource(R.string.settings_section_tabs), stringResource(R.string.settings_tabs_summary),
                painterResource(R.drawable.ic_tab), colors.tertiaryContainer, colors.onTertiaryContainer, Modifier.opens(SettingsPage.Tabs),
            )
        }
        SettingsGroup {
            CategoryRow(
                stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_summary),
                painterResource(R.drawable.ic_shield), colors.primaryContainer, colors.onPrimaryContainer, Modifier.opens(SettingsPage.Privacy),
            )
            CategoryRow(
                stringResource(R.string.settings_sites), stringResource(R.string.settings_sites_summary),
                painterResource(R.drawable.ic_language), colors.secondaryContainer, colors.onSecondaryContainer, Modifier.opens(SettingsPage.Sites),
            )
        }
        SettingsGroup {
            if (isDefault) DefaultBrowserRow(onChange = defaultBrowser::request)
            val channelName = stringResource((channel?.let(Channel::of) ?: installedChannel).labelRes())
            CategoryRow(
                stringResource(R.string.settings_section_updates),
                if (updatesOn) stringResource(R.string.settings_updates_summary, channelName) else stringResource(R.string.settings_updates_off),
                painterResource(R.drawable.ic_system_update), colors.tertiaryContainer, colors.onTertiaryContainer, Modifier.opens(SettingsPage.Updates),
            )
            CategoryRow(
                stringResource(R.string.settings_about), rememberAppVersion().copyText,
                rememberVectorPainter(Icons.Filled.Info), colors.surfaceContainerHighest, colors.onSurfaceVariant, Modifier.opens(SettingsPage.About),
            )
        }
    }
}

/** "Lumi is not the default browser" with Make default; it hides as soon as Lumi becomes the default. */
@Composable
private fun DefaultCard(onSet: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(colors.primaryContainer, RoundedCornerShape(20.dp)).padding(20.dp)) {
        Text(stringResource(R.string.settings_card_title), style = MaterialTheme.typography.titleMedium, color = colors.onPrimaryContainer)
        Text(
            stringResource(R.string.settings_card_body), style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Button(onClick = onSet, colors = ButtonDefaults.buttonColors(containerColor = colors.onPrimaryContainer, contentColor = colors.primaryContainer)) {
            Text(stringResource(R.string.settings_card_button))
        }
    }
}

/** Shown instead of the card while Lumi is the default; a tap opens the system's default-apps screen. */
@Composable
private fun DefaultBrowserRow(onChange: () -> Unit) {
    SettingsRow(
        stringResource(R.string.settings_default_browser), stringResource(R.string.settings_default_browser_on),
        painterResource(R.drawable.ic_open_in_browser),
        Modifier.clickable(onClickLabel = stringResource(R.string.cd_change), role = Role.Button, onClick = onChange),
    ) { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
}
