# Template generation

`make generate` copies `android/` into `.build/android/` and turns it into an
application-specific Gradle project.

It performs three steps:

1. Copies the reusable Android template with `rsync`.
2. Finds text templates such as `*.gradle`, `*.xml`, `*.java`, `*.kt`,
   `*.properties`, `*.toml` and `*.md`, then replaces matching `@@VAR@@`
   placeholders.
3. Relocates Java and Kotlin sources whose package declaration matches
   `APP_ID` into `app/src/main/java/<APP_ID as path>/`.

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
