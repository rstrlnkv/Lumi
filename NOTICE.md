# Notice

The tree carries the third-party material listed below. It is not covered by the Mozilla Public
License 2.0 in `LICENSE` and keeps its own terms. Libraries that the build downloads (GeckoView,
AndroidX, Kotlin and others) are not in the tree; the app lists them with their licences under
Settings → About Lumi → Licenses.

## Material Symbols

- What it is: vector icons from Material Symbols Rounded, used for the interface icons.
- Where it lies: `app/src/main/res/drawable/ic_*.xml`, except `ic_launcher_background.xml`,
  `ic_launcher_monochrome.xml` and `ic_stat_lumi.xml`, which are Lumi's own.
- Source: https://github.com/google/material-design-icons
- Copyright: Copyright Google LLC
- Licence: Apache License, Version 2.0; https://www.apache.org/licenses/LICENSE-2.0
- Changes: each icon's path is copied into an Android vector drawable; `ic_domino_mask.xml` is
  redrawn for Lumi after `domino_mask` and is not the original glyph.

## Gradle Wrapper

- What it is: the script and JAR that download and run the pinned Gradle version.
- Where it lies: `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`.
- Source: https://github.com/gradle/gradle
- Copyright: Copyright © 2015 the original authors
- Licence: Apache License, Version 2.0; https://www.apache.org/licenses/LICENSE-2.0
- Changes: none; the files are used as Gradle generates them.
