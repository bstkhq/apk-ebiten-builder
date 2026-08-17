# apk-ebiten-builder

[![CI](https://github.com/bstkhq/apk-ebiten-builder/actions/workflows/ci.yml/badge.svg)](https://github.com/bstkhq/apk-ebiten-builder/actions/workflows/ci.yml)

Build any **Ebiten** game as an **Android APK** using a `Makefile`-based workflow, **without Android Studio**.

This repo is a reusable Android/Gradle template with build rules (`Include.mk`)
and an optional Go runtime-bridge package that your project imports. It handles:

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
  - [Injecting variables into Go (ldflags)](#injecting-variables-into-go-ldflags)
- [Android bridges](#android-bridges)
- [Include.mk targets](#includemk-targets)
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
- `ebitenmobile` via `go install`, pinned to `v2.9.9` by default

Set `EBITENMOBILE_VERSION` to the Ebitengine version used by your application.
For example, from the application's Go module:

```bash
make -f /path/to/apk-ebiten-builder/Dependencies.mk install_dependencies \
  EBITENMOBILE_VERSION="$(go list -m -f '{{.Version}}' github.com/hajimehoshi/ebiten/v2)"
```

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
BUILDER_REPO ?= https://github.com/bstkhq/apk-ebiten-builder
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
| `APP_RES_DIR`        | *(empty)*                   | Optional [app-owned Android resource overlay](docs/template-generation.md#application-resources). |
| `VERSION`            | `v1.0.0`                    | `versionName`. `VERSION_CODE` is derived automatically.                  |
| `SCREEN_ORIENTATION` | `fullSensor`                | Value for `android:screenOrientation`.                                   |
| `ALLOW_BACKUP`       | `true`                      | Strict `true`/`false` value for the manifest `android:allowBackup` policy. |
| `USES_CLEARTEXT_TRAFFIC` | *(empty)*               | Optional `true`/`false` manifest override. Empty preserves Android's default. |
| `ENABLE_ON_BACK_INVOKED_CALLBACK` | *(empty)*       | Optional `true`/`false` predictive-Back manifest override. |
| `ANDROID_SDK_ROOT`   | *(required)*                | SDK root. Populated by `Dependencies.mk`.                                |
| `DEBUG`              | `0`                         | `1` shows full Gradle output.                                            |
| `NO_COLOR`           | *(empty)*                   | `1` disables colored log prefixes.                                       |

See the [versioning guide](docs/versioning.md) for `VERSION_CODE` derivation.

<a id="injecting-variables-into-go-ldflags"></a>
### Injecting variables into Go (ldflags)

```make
GO_LDFLAGS := -X 'my/pkg/env.DefaultURL=http://192.168.1.10:8080'
export GO_LDFLAGS
```

Use the same variable in the consuming application's `Makefile`; leave it
empty when no compile-time configuration is needed.

## Android bridges

The optional [`bridge`](bridge) package connects a Go `package mobile` to
selected Android services. Add it at the same release version as the Android
template:

```bash
go get github.com/bstkhq/apk-ebiten-builder/bridge@<release-tag>
```

Every bridge is independent and opt-in. Gomobile requires its Java-facing
interfaces to be declared locally in `package mobile`; the guides contain the
small local adapters, complete Go examples and lifecycle details.

| Bridge | Purpose | Guide |
| --- | --- | --- |
| Runtime | Device, app, storage and network services; safe process restart | [Runtime bridge](docs/bridges/runtime.md) |
| Back | Let Go consume or delegate Android Back | [Back bridge](docs/bridges/back.md) |
| File picker | Select a document and receive a local temporary copy | [File picker](docs/bridges/file-picker.md) |
| IME | Control the keyboard and read composing text | [IME bridge](docs/bridges/ime.md) |

Applications that do not export an optional bridge retain their existing
behavior. Legacy runtime exports remain supported; new integrations should use
the documented `bridge` clients.


<a id="includemk-targets"></a>
## `Include.mk` targets

| Target      | What it does                                                         |
| ----------- | -------------------------------------------------------------------- |
| `all`       | `clean` + `build` + `install`                                        |
| `info`      | Prints the resolved configuration (paths, versions, etc.).           |
| `generate`  | Copies `android/` to `.build/android/` and substitutes placeholders; see [template generation](docs/template-generation.md). |
| `compile`   | Runs `ebitenmobile bind` and produces `app/libs/game.aar`.           |
| `build`     | `generate` + `compile` + `gradlew assembleDebug`.                    |
| `install`   | `gradlew installDebug` + launches the activity via `adb am start`.   |
| `clean`     | Removes `.build/android`.                                            |
| `clean_arr` | Removes only the compiled `.aar` (forces Go recompilation).          |
| `log`       | `adb logcat` filtered by the `GoLog` and `Go` tags.                  |

When `DEBUG=0` (default), Gradle output is captured to `.build/android/.make-gradle.log` and only shown (last 200 lines) if the build fails.


## Signed release builds

`Include.mk` produces debug APKs. See the
[signed release guide](docs/releasing.md) for the release target and usage.


## Troubleshooting

- **`GO_SRC is empty`** — Set `GO_SRC` in your `Makefile` or on the CLI (`make build GO_SRC=$(pwd)`).
- **`ANDROID_SDK_ROOT is empty`** — Your shell rc is missing the exports. Run `make -f Dependencies.mk info_sdk` to verify.
- **`ebitenmobile: command not found`** — `$(go env GOPATH)/bin` is not on `PATH`.
- **Gradle fails silently** — Rerun with `DEBUG=1` to see full output, or inspect `.build/android/.make-gradle.log`.
- **APK doesn't launch after `install`** — Check that `MAIN_ACTIVITY` and `APP_ID` match what the generated manifest declares; run `make info`.
- **Game logs** — Use `make log`. The default Go tag is `GoLog`.


## License

MIT. See `LICENSE`.
