# Template generation

`make generate` copies `android/` into `.build/android/` and turns it into an
application-specific Gradle project.

It performs four steps:

1. Copies the reusable Android template with `rsync`.
2. Finds text templates such as `*.gradle`, `*.xml`, `*.java`, `*.kt`,
   `*.properties`, `*.toml` and `*.md`, then replaces matching `@@VAR@@`
   placeholders.
3. Relocates Java and Kotlin sources whose package declaration matches
   `APP_ID` into `app/src/main/java/<APP_ID as path>/`.
4. Optionally overlays app-owned Android resources.

## Placeholder values

The template replacement step receives these values:

`APP_NAME`, `APP_ID`, `GO_PKG`, `JAVA_PKG`, `MAIN_ACTIVITY`,
`ANDROID_SDK_ROOT`, `VERSION`, `VERSION_CODE`, `SCREEN_ORIENTATION`,
`ALLOW_BACKUP`, `USES_CLEARTEXT_TRAFFIC_ATTRIBUTE`,
`ENABLE_ON_BACK_INVOKED_CALLBACK_ATTRIBUTE` and `LOG_TAG`.

The two `*_ATTRIBUTE` values are generated from the corresponding user-facing
configuration variables. They are empty when their manifest override is not
set, so the template preserves Android's default behavior.

`JAVA_PKG` defaults to `$(APP_ID).corelib` and is passed to `ebitenmobile bind`
through `-javapkg`.

For the public configuration surface, see the root
[configuration table](../README.md#configuration-variables).

## Application resources

Set `APP_RES_DIR` to a directory whose contents follow Android's `res`
layout. The builder copies this directory after processing its own template,
so an app-owned file with the same relative path replaces the template file.
The overlay files themselves are not processed as `@@VAR@@` templates.

```make
APP_RES_DIR ?= $(abspath android-res)
```

For example, the app can provide its launcher icons without modifying
`.build`:

```text
android-res/
├── mipmap-mdpi/ic_launcher.png
├── mipmap-hdpi/ic_launcher.png
├── mipmap-xhdpi/ic_launcher.png
├── mipmap-xxhdpi/ic_launcher.png
├── mipmap-xxxhdpi/ic_launcher.png
├── mipmap-mdpi/ic_launcher_round.png
├── ...
└── mipmap-anydpi-v26/
    ├── ic_launcher.xml
    └── ic_launcher_round.xml
```

The generated manifest refers to `ic_launcher` and `ic_launcher_round`, so
keep those names. `APP_RES_DIR` may also contain other Android resources such
as `drawable/`, `values/`, `font/` or `raw/`.

Changes to existing overlay files are picked up by the next `make build`.
After removing an overlay file, run `make clean build` to remove its previous
generated copy. Leave `APP_RES_DIR` empty to disable the overlay; a non-empty
path must name a directory.
