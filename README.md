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
  - [VERSION_CODE derived from VERSION](#version_code-derived-from-version)
  - [Injecting variables into Go (ldflags)](#injecting-variables-into-go-ldflags)
- [Android bridges](#android-bridges)
- [Automated tests](#automated-tests)
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
On Debian/Ubuntu, the host-side Go test gate additionally needs Ebiten's native
development packages:

```bash
sudo apt-get install libc6-dev libasound2-dev libgl1-mesa-dev libx11-dev \
  libxcursor-dev libxi-dev libxinerama-dev libxrandr-dev libxxf86vm-dev pkg-config
```

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
| `VERSION`            | `v1.0.0`                    | `versionName`. `VERSION_CODE` is derived automatically.                  |
| `SCREEN_ORIENTATION` | `fullSensor`                | Value for `android:screenOrientation`.                                   |
| `ALLOW_BACKUP`       | `true`                      | Strict `true`/`false` value for the manifest `android:allowBackup` policy. |
| `USES_CLEARTEXT_TRAFFIC` | *(empty)*               | Optional `true`/`false` manifest override. Empty preserves Android's default. |
| `ENABLE_ON_BACK_INVOKED_CALLBACK` | *(empty)*       | Optional `true`/`false` predictive-Back manifest override. |
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
The generated value is emitted from decimal arithmetic rather than as a
zero-padded literal. This matters for versions such as `v0.8.9`: Gradle sees
`80900`, never an octal-looking `0080900`.

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


## Automated tests

The repository includes three progressively broader gates:

```bash
make test          # Java/Go contracts, templates and device-helper tests
make test-android  # gates above + all fixture APKs and Android lint
make test-device   # build gate + runtime, restart, Back, picker and IME checks
```

The Android build gate compiles legacy, the `bridge`-package AndroidBridge
example, and independent Back, file-picker and IME lifecycle packages. Those
fixtures embed the canonical callback contracts and inspect the actual gomobile
Java signatures. The gate also runs Debug and Release lint, verifies APK
signatures, and checks 16 KiB ZIP and native ELF alignment. It defaults to
amd64 plus arm64; override `ANDROID_TARGET` when a narrower fixture is
required.

The builder supplies the linker flags required by NDK r26 for 16 KiB-aligned
native libraries, independently of any values injected through `GO_LDFLAGS`.

GitHub Actions runs `make test-android` for every pull request and every push
to `main`. It installs Android tooling through `Dependencies.mk`, pins
`ebitenmobile` to the fixture's Ebitengine version, and caches the SDK based on
that installer configuration. The device gate remains manual because it
requires a connected Android device.

The device scripts accept `ADB_SERIAL=<serial>` and
`DEVICE_TIMEOUT_SECONDS=<seconds>`. They install only the private fixture
packages under `games.example.builder.*` and force-stop those fixtures on
exit. Before changing the tablet, the shared device harness records its
`GoLog` level and keep-awake setting. It wakes and unlocks the display, waits
for the launcher transition to settle, requires a stable top-resumed Activity,
and restores the recorded settings even when a gate fails. The helper itself
is covered with a fake ADB transport, including the unset-setting case.
The IME gate requests the keyboard during Activity creation, sends text through
the focused surface, then verifies that Go can hide it again; the Back gate
independently verifies consumed and delegated events.


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

Substitutable variables: `APP_NAME`, `APP_ID`, `GO_PKG`, `JAVA_PKG`, `MAIN_ACTIVITY`, `ANDROID_SDK_ROOT`, `VERSION`, `VERSION_CODE`, `SCREEN_ORIENTATION`, `ALLOW_BACKUP`, `LOG_TAG`.

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
