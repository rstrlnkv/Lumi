# Architecture

A short map for someone reading the code for the first time.

## Bird's-eye view

Lumi is an Android browser. GeckoView, Mozilla's embeddable Gecko engine, draws and runs the
pages; Lumi draws everything around them in Jetpack Compose with Material 3. All data stays on the
phone: there is no account, no sync and no server of Lumi's own.

```
:app      Compose UI, gestures, services   (depends on :engine and :data)
:engine   GeckoView: runtime, sessions      (the only module that sees GeckoView)
:data     Room, DataStore, files            (no GeckoView, no Compose)
```

`settings.gradle.kts` names the three modules; versions live only in `gradle/libs.versions.toml`.

## The modules

**`:engine`** (`engine/src/main/java/ru/digitalpunk/lumi/engine/`) owns the browser engine.
`BrowserEngine.kt` holds one `GeckoRuntime` for the process and one `GeckoSession` per tab, and
exposes tab state as flows (`PageState.kt`, `TabsStore.kt`) and plain commands (load, back, reload).
`LumiGeckoView.kt` is the page view; it reports touches and never keeps one from Gecko.
`LiveSessions.kt` keeps only a few sessions open and restores the rest from saved state.
`PageEndExtension.kt` installs a built-in WebExtension from `engine/src/main/assets/lumi-page-end/`
that ends long pages above the bar and reports the page's colours.

**`:data`** (`data/src/main/java/ru/digitalpunk/lumi/data/`) owns what survives a restart. One Room
database, `tabs.db`, holds tabs and groups (`tabs/`), bookmarks and history (`library/`) and
downloads (`downloads/`). `SettingsStore.kt` keeps settings in DataStore. `privatetabs/` stores
private-tab state encrypted with a Keystore key. `search/` and `icons/` make Lumi's own HTTPS
requests for search suggestions and site icons, outside GeckoView.

**`:app`** (`app/src/main/java/ru/digitalpunk/lumi/`) owns what is drawn. `MainActivity.kt` hosts
the browser screen; `LumiApplication.kt` owns the engine and the stores for the process. The
packages follow the features: `address`, `gesture`, `tabs`, `ui/tabgrid`, `library`, `startpage`,
`suggest`, `privacy`, `downloads`, `defaultbrowser`, `links`, `about`; `ui/` holds the screens.

## Where things live

- Visible text: `app/src/main/res/values/` (English, the default) and `app/src/main/res/values-ru/`
  (Russian).
- Icons: `app/src/main/res/drawable/`; the launcher icon in `mipmap-anydpi/` and `drawable-nodpi/`.
- Room schemas, one file per version: `data/schemas/ru.digitalpunk.lumi.data.tabs.TabDatabase/`.
- Test pages for manual checks on a device: `fixtures/`.
- The version name: `lumi.version` in `gradle.properties`; the build number is the git commit count,
  computed in the root `build.gradle.kts`.

## Invariants

- **Only `:engine` imports `org.mozilla.geckoview`.** `:engine` depends on GeckoView with
  `implementation`, so no GeckoView type reaches `:app`; the page is handed out as an
  `android.view.View`.
- **The page view is never inside a Compose scroll container.** Compose owns the bar's offset and
  tells Gecko how much of the bar slid away, so the page viewport follows the bar. No nested scroll.
- **`BrowserEngine.load` accepts only `http`, `https` and the allowed `about:` pages**, whatever the
  caller.
- **Nothing from a private tab is stored unencrypted**, and private tabs write no history.
- **Room migrations are written by hand** for every schema change, with a frozen schema file; the
  database is never rebuilt destructively.
- **Lumi's data is excluded from cloud backup and device transfer**
  (`app/src/main/res/xml/data_extraction_rules.xml`).

## Checking

`./gradlew test lint` runs the unit tests of all three modules and Android lint. Run both together:
lint catches a string with no Russian translation. The tests need Node.js on `PATH` for the page-end
content script.
