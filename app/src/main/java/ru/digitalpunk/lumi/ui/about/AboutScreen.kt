package ru.digitalpunk.lumi.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.about.AppVersion
import ru.digitalpunk.lumi.about.showCopiedSnackbar

private const val AUTHOR_URL = "https://github.com/rstrlnkv"

/** The running app's version and build, read from the installed package (never typed into code). */
@Composable
fun rememberAppVersion(): AppVersion {
    val context = LocalContext.current
    return remember(context) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        AppVersion(info.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(info))
    }
}

/**
 * About Lumi over Settings, with its two sub-screens (What's new, Licenses) and a license text over Licenses.
 * Each open level swallows touches, hides the level below from TalkBack and closes on Back, topmost first.
 */
@Composable
fun AboutHost(onBack: () -> Unit, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf("main") }
    var licenseId by rememberSaveable { mutableStateOf<String?>(null) }
    val cover = Modifier.fillMaxSize().pointerInput(Unit) {}
    fun under(covered: Boolean) = if (covered) Modifier.clearAndSetSemantics {} else Modifier
    Box(modifier) {
        BackHandler(onBack = onBack)
        AboutScreen(onBack, onOpenUrl, { page = it }, Modifier.fillMaxSize().then(under(page != "main")))
        if (page == "news") {
            BackHandler { page = "main" }
            WhatsNewScreen(onBack = { page = "main" }, modifier = cover)
        }
        if (page == "licenses") {
            BackHandler { page = "main" }
            LicensesScreen(onBack = { page = "main" }, onOpen = { licenseId = it }, modifier = cover.then(under(licenseId != null)))
            licenseId?.let { id ->
                BackHandler { licenseId = null }
                LicenseTextScreen(id, onBack = { licenseId = null }, modifier = cover)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AboutScreen(onBack: () -> Unit, onOpenUrl: (String) -> Unit, onPage: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val version = rememberAppVersion()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.about_copied, version.copyText)
    val copy = {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Lumi version", version.copyText))
        if (showCopiedSnackbar(Build.VERSION.SDK_INT)) scope.launch { snackbar.showSnackbar(copied) }
        Unit
    }
    AboutScaffold(stringResource(R.string.about_title), onBack, modifier, snackbar) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            // The adaptive icon's two layers: the 144 dp box shows its 72 dp launcher window inside the 96 dp circle.
            Box(Modifier.size(96.dp).clip(CircleShape)) {
                Box(Modifier.wrapContentSize(unbounded = true).requiredSize(144.dp)) {
                    Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(16.dp))
            Mark {
                Text("Lumi", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                if (version.isDev) {
                    Text(
                        stringResource(R.string.about_dev_badge),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(top = badgeTop).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp).padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
            val copyLabel = stringResource(R.string.cd_copy_version, version.name, version.code.toString())
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_version)) },
                supportingContent = { Text(version.copyText) },
                leadingContent = { Icon(painterResource(R.drawable.ic_tag), contentDescription = null) },
                trailingContent = { Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null) },
                modifier = Modifier.heightIn(min = 72.dp).combinedClickable(onClick = copy, onLongClick = copy)
                    .semantics(mergeDescendants = true) { contentDescription = copyLabel },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_author)) },
                supportingContent = { Text(stringResource(R.string.about_contact)) },
                leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                trailingContent = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                modifier = Modifier.heightIn(min = 72.dp)
                    .clickable(onClickLabel = stringResource(R.string.cd_open_author_page), role = Role.Button) { onOpenUrl(AUTHOR_URL) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            NavRow(stringResource(R.string.about_whats_new), { Icon(painterResource(R.drawable.ic_new_releases), contentDescription = null) }) { onPage("news") }
            NavRow(stringResource(R.string.about_licenses), { Icon(painterResource(R.drawable.ic_description), contentDescription = null) }) { onPage("licenses") }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.about_copyright),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** The DEV badge's top sits 3 dp below the top of the name's line box, like a superscript tag. */
private val badgeTop = 3.dp

/** Where the name and the badge go on the mark line, in px. */
internal data class MarkPlacement(val width: Int, val nameX: Int, val badgeX: Int)

/** The name alone is centred: the line keeps the badge and its gap on both sides and draws them only after the name. */
internal fun markPlacement(name: Int, badge: Int, gap: Int): MarkPlacement =
    MarkPlacement(width = name + 2 * (gap + badge), nameX = gap + badge, badgeX = gap + badge + name + gap)

/** The name, then an optional badge [gap] after it, top-aligned; the reading order stays name, then badge. */
@Composable
private fun Mark(gap: Dp = 8.dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val name = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val badge = measurables.getOrNull(1)?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val at = markPlacement(name.width, badge?.width ?: 0, if (badge == null) 0 else gap.roundToPx())
        layout(at.width, maxOf(name.height, badge?.height ?: 0)) {
            name.placeRelative(at.nameX, 0)
            badge?.placeRelative(at.badgeX, 0)
        }
    }
}

/** A row that opens a sub-screen. */
@Composable
internal fun NavRow(text: String, leading: (@Composable () -> Unit)? = null, supporting: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = leading,
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick),
    )
}

/** The shared frame of the About screens: a top bar with a heading title and Back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    snackbar: SnackbarHostState? = null,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        // Lines stay readable in landscape and on tablets: the content is at most 600 dp wide, centred.
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) { Box(Modifier.widthIn(max = 600.dp)) { content() } }
    }
}
