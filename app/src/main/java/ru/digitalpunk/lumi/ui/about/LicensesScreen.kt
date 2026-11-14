package ru.digitalpunk.lumi.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.about.Library
import ru.digitalpunk.lumi.about.parseLicenses

/** The bundled libraries, generated at build time by the AboutLibraries plugin into res/raw. */
@Composable
private fun rememberLibraries(): State<List<Library>> {
    val context = LocalContext.current
    return produceState(emptyList()) {
        value = withContext(Dispatchers.IO) {
            context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { parseLicenses(it.readText()) }
        }
    }
}

/** One row per bundled third-party library; a tap opens its license text. */
@Composable
internal fun LicensesScreen(onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier) {
    val libraries by rememberLibraries()
    AboutScaffold(stringResource(R.string.about_licenses), onBack, modifier) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    stringResource(R.string.licenses_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(libraries, key = { it.id }) { lib ->
                NavRow(lib.name, supporting = lib.licenses.joinToString(" / ") { it.name }) { onOpen(lib.id) }
            }
        }
    }
}

/** A library's license texts in monospace boxes; a license without text shows its name only. */
@Composable
internal fun LicenseTextScreen(id: String, onBack: () -> Unit, modifier: Modifier) {
    val libraries by rememberLibraries()
    val lib = libraries.firstOrNull { it.id == id }
    AboutScaffold(lib?.name.orEmpty(), onBack, modifier) {
        if (lib == null) return@AboutScaffold
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(listOfNotNull(lib.id, lib.version).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            lib.licenses.forEach { license ->
                Text(license.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                if (license.text.isNotBlank()) {
                    SelectionContainer(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                    ) {
                        Text(license.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}
