# apk-ebiten-builder

Build any **Ebiten** game as an **Android APK** using a `Makefile`-based workflow, **without Android Studio**.

This repo is a reusable Android/Gradle template with build rules (`Include.mk`) that your project imports from its own `Makefile`. It handles:

1. Generating the Android project from the templates (substituting `@@VAR@@` placeholders).
2. Compiling an Android library (`.aar`) from your Go `package mobile` with `ebitenmobile bind`.
3. Assembling a debug APK with Gradle.
4. Optionally installing and launching the APK on a connected device via `adb`.

Inspired by the practices from `github.com/programatta/demoandroid` (the "do it by hand / no Android Studio" style, minimal Gradle and manifest setup).


## Table of contents

- [Prerequisites](#prerequisites)
- [Installing dependencies](#installing-dependencies)
- [Usage from your project](#usage-from-your-project)
- [Configuration variables](#configuration-variables)
  - [VERSION_CODE derived from VERSION](#version_code-derived-from-version)
  - [Injecting variables into Go (ldflags)](#injecting-variables-into-go-ldflags)
- [Include.mk targets](#includemk-targets)
- [How template substitution works](#how-template-substitution-works)
- [Signed release builds](#signed-release-builds)
- [Troubleshooting](#troubleshooting)
- [License](#license)


## Prerequisites

- **Java** (JDK 17 recommended, required by Gradle).
- **Go** on `PATH`.
- **bash**, `make`, `git`, `curl`, `unzip`, `rsync`, `perl`.
- An Ebiten project with a `package mobile` that `ebitenmobile bind` can target.

Everything else (Android SDK, NDK, CMake, `ebitenmobile`) is installed by this repo.

## Installing dependencies

`Dependencies.mk` installs the SDK under `$HOME/Android/Sdk` without using any system package manager.

```bash
make -f Dependencies.mk install_dependencies
```

This installs:
- Android `cmdline-tools` + `platform-tools`
- `platforms;android-35`, `build-tools;35.0.0`
- NDK `26.3.11579264`, CMake `3.22.1`
- `ebitenmobile` via `go install`

When it finishes, add the following to your `.bashrc` / `.zshrc`:

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
export PATH="$(go env GOPATH)/bin:$PATH"
```

Other useful `Dependencies.mk` targets: `info_sdk`, `list_sdk`, `update_sdk`, `accept_licenses`, `clean_sdk`.

---

## Usage from your project

In your Ebiten project, create a `Makefile` that clones this repo and includes it:

```make
# Configuration
APP_NAME ?= My Game
APP_ID   ?= games.mycompany.mygame
GO_SRC   ?= $(abspath .)

# Internal
BUILDER_DIR  ?= .build/apk-ebiten-builder
BUILDER_REPO ?= https://github.com/erparts/apk-ebiten-builder
INCLUDE_PATH ?= $(BUILDER_DIR)/Include.mk

export APP_ID

$(INCLUDE_PATH):
	git clone $(BUILDER_REPO) $(BUILDER_DIR)

include $(INCLUDE_PATH)
```

Then run:

```bash
make build        # generate + compile .aar + assembleDebug
make install      # install and launch on any adb-connected device
make all          # clean + build + install
make info         # show resolved configuration
make log          # adb logcat filtered by GoLog/Go tags
make clean        # remove .build/android
```


## Configuration variables

Defined in `Include.mk`. Override from your `Makefile` or on the command line.

| Variable             | Default                     | Description                                                              |
| -------------------- | --------------------------- | ------------------------------------------------------------------------ |
| `APP_NAME`           | `Ebiten Android`            | App display name.                                                        |
| `APP_ID`             | `games.orgname.project`     | Application ID / package name. Also drives the Java source path.         |
| `MAIN_ACTIVITY`      | `.MainActivity`             | Main activity (relative to `APP_ID`).                                    |
| `GO_PKG`             | `mobile`                    | Go package name passed to `ebitenmobile bind`.                           |
| `GO_SRC`             | *(required)*                | Absolute path to the game's Go `mobile` package.                         |
| `GO_LDFLAGS`         | *(empty)*                   | `-ldflags` passed to `ebitenmobile bind`. Useful for injecting variables.|
| `VERSION`            | `v1.0.0`                    | `versionName`. `VERSION_CODE` is derived automatically.                  |
| `SCREEN_ORIENTATION` | `fullSensor`                | Value for `android:screenOrientation`.                                   |
| `ANDROID_SDK_ROOT`   | *(required)*                | SDK root. Populated by `Dependencies.mk`.                                |
| `DEBUG`              | `0`                         | `1` shows full Gradle output.                                            |
| `NO_COLOR`           | *(empty)*                   | `1` disables colored log prefixes.                                       |

<a id="version_code-derived-from-version"></a>
### `VERSION_CODE` derived from `VERSION`

Extracts the first 4 integers found in `VERSION` and packs them:

```
VERSION_CODE = major * 1_000_000 + minor * 10_000 + patch * 100 + extra
```

Example: `v2.3.1` → `2030100`. Missing numbers default to `0`.

<a id="injecting-variables-into-go-ldflags"></a>
### Injecting variables into Go (ldflags)

```make
GO_LDFLAGS := -X 'my/pkg/env.DefaultURL=http://192.168.1.10:8080'
export GO_LDFLAGS
```

See the sample project `Makefile` (Buzzattack Kiosk) for a complete pattern with an optional `CONTROL_URL`.


<a id="includemk-targets"></a>
## `Include.mk` targets

| Target      | What it does                                                         |
| ----------- | -------------------------------------------------------------------- |
| `all`       | `clean` + `build` + `install`                                        |
| `info`      | Prints the resolved configuration (paths, versions, etc.).           |
| `generate`  | Copies `android/` to `.build/android/` and substitutes placeholders. |
| `compile`   | Runs `ebitenmobile bind` and produces `app/libs/game.aar`.           |
| `build`     | `generate` + `compile` + `gradlew assembleDebug`.                    |
| `install`   | `gradlew installDebug` + launches the activity via `adb am start`.   |
| `clean`     | Removes `.build/android`.                                            |
| `clean_arr` | Removes only the compiled `.aar` (forces Go recompilation).          |
| `log`       | `adb logcat` filtered by the `GoLog` and `Go` tags.                  |

When `DEBUG=0` (default), Gradle output is captured to `.build/android/.make-gradle.log` and only shown (last 200 lines) if the build fails.


## How template substitution works

`generate` rsyncs `android/` → `.build/android/`, then:

1. Finds `*.gradle`, `*.xml`, `*.java`, `*.kt`, `*.properties`, `*.toml`, `*.md`, etc.
2. Replaces every `@@VAR@@` with the value of the matching variable (`APP_NAME`, `APP_ID`, `VERSION`, …).
3. Relocates any `.java`/`.kt` whose `package` declaration matches `APP_ID` into `app/src/main/java/<APP_ID as path>/`.

Substitutable variables: `APP_NAME`, `APP_ID`, `GO_PKG`, `JAVA_PKG`, `MAIN_ACTIVITY`, `ANDROID_SDK_ROOT`, `VERSION`, `VERSION_CODE`, `SCREEN_ORIENTATION`, `LOG_TAG`.

`JAVA_PKG` defaults to `$(APP_ID).corelib` and is the `-javapkg` passed to `ebitenmobile bind`.


## Signed release builds

`Include.mk` only produces debug APKs. For a signed release, add a target like this to your project's `Makefile`:

```make
KEYSTORE_PATH ?= $(ROOT_DIR)/release.keystore
KEYSTORE_PASS ?=
KEY_ALIAS ?=
KEY_PASS ?= $(KEYSTORE_PASS)

APK_RELEASE := $(ANDROID_DIR)/app/build/outputs/apk/release/app-release.apk

release: generate compile
	$(Q)test -f "$(KEYSTORE_PATH)" || { echo "Keystore not found"; exit 1; }
	$(Q)test -n "$(KEYSTORE_PASS)" || { echo "KEYSTORE_PASS empty"; exit 1; }
	$(Q)test -n "$(KEY_ALIAS)"     || { echo "KEY_ALIAS empty"; exit 1; }
	$(call GRADLE_RUN,assembleRelease \
		-Pandroid.injected.signing.store.file=$(KEYSTORE_PATH) \
		-Pandroid.injected.signing.store.password=$(KEYSTORE_PASS) \
		-Pandroid.injected.signing.key.alias=$(KEY_ALIAS) \
		-Pandroid.injected.signing.key.password=$(KEY_PASS))

.PHONY: release
```

Usage:

```bash
make release KEYSTORE_PASS='***' KEY_ALIAS=upload KEY_PASS='***'
```


## Troubleshooting

- **`GO_SRC is empty`** — Set `GO_SRC` in your `Makefile` or on the CLI (`make build GO_SRC=$(pwd)`).
- **`ANDROID_SDK_ROOT is empty`** — Your shell rc is missing the exports. Run `make -f Dependencies.mk info_sdk` to verify.
- **`ebitenmobile: command not found`** — `$(go env GOPATH)/bin` is not on `PATH`.
- **Gradle fails silently** — Rerun with `DEBUG=1` to see full output, or inspect `.build/android/.make-gradle.log`.
- **APK doesn't launch after `install`** — Check that `MAIN_ACTIVITY` and `APP_ID` match what the generated manifest declares; run `make info`.
- **Game logs** — Use `make log`. The default Go tag is `GoLog`.


## License

MIT. See `LICENSE`.
