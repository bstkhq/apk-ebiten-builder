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
- [Bridge guides](docs/bridges/)
- [Android runtime bridge](#android-runtime-bridge)
- [Android Back bridge](#android-back-bridge)
- [Optional Android file picker](#optional-android-file-picker)
- [Android IME lifecycle](#android-ime-lifecycle)
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


## Android runtime bridge

An application can opt in to Android runtime services without copying the
long gomobile interface. Add the builder module at the same release version as
the template you clone:

```bash
go get github.com/bstkhq/apk-ebiten-builder/bridge@<release-tag>
```

The [`bridge`](bridge) package is the canonical Go API: it documents every
runtime, IME, Back and file-picker method. Gomobile still requires callback
types to be declared locally in `package mobile`; the adapters below embed the
canonical types instead of copying their methods. A callback-taking method must
retain a local parameter type, because gomobile generates that callback type
from `package mobile` too.

For behavior, lifecycle rules and concrete usage scenarios, read the
[runtime bridge guide](docs/bridges/runtime.md). This README keeps the complete
copy-ready Go adapter in one place.

Then put this adapter in the `package mobile` passed to `ebitenmobile bind`:

```go
package mobile

import (
	"context"
	"log"
	"time"
	_ "time/tzdata"

	"github.com/bstkhq/apk-ebiten-builder/bridge"
)

var androidRuntime = bridge.NewClient()

// It must be a local named interface so gomobile exports it in Mobile.java.
// The complete 21-method contract remains owned by bridge.
type AndroidBridge interface {
	bridge.AndroidBridge
}

// This is the only required gomobile-facing adapter.
func RegisterAndroidBridge(value AndroidBridge) {
	androidRuntime.Register(value)
}

func init() {
	go readAndroidRuntime()
}

func readAndroidRuntime() {
	runtime, err := androidRuntime.Wait(context.Background())
	if err != nil {
		log.Printf("Android runtime unavailable: %v", err)
		return
	}

	androidID, err := runtime.AndroidID()
	if err != nil {
		log.Printf("Android ID unavailable: %v", err)
		return
	}
	if androidID == "" {
		log.Printf("Android returned an empty ID")
		return
	}
	timeZone, err := runtime.TimeZone()
	if err != nil {
		log.Printf("time zone unavailable: %v", err)
		return
	}
	location, err := time.LoadLocation(timeZone)
	if err != nil {
		log.Printf("invalid Android time zone %q: %v", timeZone, err)
		return
	}
	time.Local = location

	log.Printf("Android ready: model=%s sdk=%d", runtime.Model(), runtime.SDKInt())
	// Start the game or use androidID here.
}
```

This pattern lets an application wait until the activity supplies Android
identity and locale data, then start with those values. The complete,
executable reference is the
[`bridge` fixture](tests/fixtures/bridge/mobile.go), which additionally reads
every service and exercises activity recreation.

`ebitenmobile` only exposes API declared in the package it binds. Therefore
the local embedded `AndroidBridge` and `RegisterAndroidBridge` are intentional:
passing `bridge.AndroidBridge` directly makes gomobile omit the export. The
embedding keeps the Java-facing type local while avoiding a copied contract.

`Client.Register` is safe to call repeatedly and replaces the old bridge when
Android recreates `MainActivity`. `Wait` blocks until the first registration or
context cancellation; `Current` returns the latest registered bridge for code
that is already running. Do not retain a returned bridge indefinitely if the
application must survive activity recreation—call `Current` again when the
value is needed.

The registration is discovered reflectively, so applications that do not
export it still build. If an export with that name has an incompatible
signature, startup fails with an explicit contract error instead of silently
falling back.

`AndroidID` is the complete hexadecimal Android ID as a string. This avoids
the sign loss that would occur through gomobile's signed `int64` binding.
`BatteryLevel` is in the range `0..1`. `Locales`, `NetworkTransports`, and
`LocalIPAddresses` are ordered comma-separated strings; locales use BCP 47
tags. Network values can be empty while the device is offline.

The three directory methods expose canonical app-private Android locations.
Persistent configuration that must not be backed up can resolve
`NoBackupFilesDir`, check its error, and then use
`filepath.Join(directory, "config.json")`. The builder does not impose a
filename or data format.

`ALLOW_BACKUP=false` sets the application-level Android backup policy and
disables cloud backup. Device-to-device transfer behavior on Android 12 and
later can still vary by manufacturer, so data that must never be transferred
belongs in `NoBackupFilesDir`. The default remains `true`, matching the
builder's existing manifest exactly. The value is strict; empty and
non-boolean values fail generation instead of producing a malformed manifest.

`RestartApp` replaces only the application process. A private helper process
links to a Binder owned by the old process before terminating it and launches
the successor only after Binder confirms death. A timeout is fail-closed: it
does not launch an overlapping successor. It never restarts Android or the
device, and a nil error means that Android accepted the restart request—not
that the successor is already ready.

Legacy applications continue to receive `SetAndroidID(int64)` and, when
present, `SetTimezone(string)`. Both exports are deprecated for new code; the
signed Android ID retains its historical high-bit mask for compatibility.

The runtime bridge needs no dangerous permission. It intentionally excludes
SSID, hardware serial, MAC address, public IP and location because those need
permissions, privileged access, or an external service. IME, Back and the
picker remain independent optional bridges below.

For an HTTP-only Control endpoint, set `USES_CLEARTEXT_TRAFFIC=true`. The
option is strict: only empty, `true`, or `false` is accepted, and the default
leaves Android's manifest policy untouched.


## Android Back bridge

Back dispatch is an independent optional contract because Android calls into
Go, while `AndroidBridge` exposes services that Go calls. Applications use the
documented `bridge.BackClient` and retain only the local types that gomobile
must export:

The [Back bridge guide](docs/bridges/back.md) explains dispatch, default
behavior and Activity recreation. The Go adapter below is its single canonical
copy.

```go
import "github.com/bstkhq/apk-ebiten-builder/bridge"

var back = bridge.NewBackClient()

type BackHandler interface {
	bridge.BackHandler
}

type BackBridge interface {
	// This parameter must use the local BackHandler so gomobile emits it.
	SetHandler(BackHandler)
}

func RegisterBackBridge(value BackBridge) {
	value.SetHandler(back)
}

func setScreenBackHandler(handle func() bool) {
	back.SetHandler(bridge.BackHandlerFunc(handle))
}
```

`OnBack` runs on Android's UI thread. It returns `true` to consume the event or
`false` to continue through the Activity's existing default behavior.
`BackClient.SetHandler(nil)` restores that default. Android can recreate the
Activity; its next `RegisterBackBridge` call installs the same client again,
while the application keeps its handler in `BackClient`.
`bridge.BackBridge` is the canonical documented shape; the one local
`SetHandler` declaration is necessary because its parameter is another
gomobile-generated interface.

Legacy applications that do not export the complete contract are unchanged.
The adapter deduplicates a physical Back key that also reaches AndroidX's
dispatcher, so Go observes one semantic event. Named exports with partial,
extra or incompatible methods fail explicitly.

Set `ENABLE_ON_BACK_INVOKED_CALLBACK=true` only when the application wants the
Android 13+ predictive-Back dispatcher. Empty preserves the existing manifest;
only empty, `true` and `false` are accepted.


## Optional Android file picker

An application can independently opt in to Android's system document picker:

The [file picker guide](docs/bridges/file-picker.md) describes the result
contract, ownership of copied files and the user-action lifecycle. The Go
adapter below is its single canonical copy.

```go
import "github.com/bstkhq/apk-ebiten-builder/bridge"

var picker = bridge.NewFilePickerClient()

type FilePickerHandler interface {
	bridge.FilePickerHandler
}

type FilePickerBridge interface {
	bridge.FilePickerOpener

	// This parameter must use the local handler type for gomobile.
	SetHandler(FilePickerHandler)
}

func RegisterFilePickerBridge(value FilePickerBridge) {
	picker.Register(value)
	value.SetHandler(picker)
}

func configurePicker() {
	picker.SetResultHandler(bridge.FilePickerHandlerFunc(func(path, message string) {
		// Open path, report message, and delete path when it is no longer needed.
	}))
}

func chooseDocument() {
	if !picker.Open("application/pdf") {
		// Android has not registered a picker yet; keep or retry the user action.
	}
}
```

`Open` launches `ACTION_OPEN_DOCUMENT`; an empty MIME type selects `*/*`.
`bridge.FilePickerOpener` supplies that method without duplicating it in the
local adapter. Android copies the chosen document into the application's
`cacheDir/picked-files` directory before calling `OnResult`. A successful result
has a non-empty local `path`, cancellation returns two empty strings, and an
error has an empty path and a non-empty `message`. `FilePickerClient.Open`
returns `false` until Android registers an opener and intentionally does not
replay an open request after Activity recreation.

The picker is absent unless the complete interface is exported, does not
change `AndroidBridge`, and requires no storage permission or application Java
code. The application owns every returned cache file and should delete it when
it is no longer needed. Calling `SetResultHandler(nil)` stops result delivery.


## Android IME lifecycle

Use `bridge.IMEClient` to retain the desired keyboard state while Android
creates or recreates its Activity. The local interface remains necessary only
so gomobile generates the Java-facing type:

The [IME guide](docs/bridges/ime.md) covers focus, composition and the
keyboard lifecycle. The Go adapter below is its single canonical copy.

```go
import (
	"github.com/bstkhq/apk-ebiten-builder/bridge"
	"github.com/hajimehoshi/ebiten/v2"
)

var ime = bridge.NewIMEClient()

type IMEBridge interface {
	bridge.IMEBridge
}

func RegisterIMEBridge(value IMEBridge) {
	ime.Register(value)
}

func focusNameField() {
	ime.Show(bridge.IMEText, bridge.IMEActionDone)
}

func closeField() {
	ime.Hide()
}

func (g *game) Update() error {
	g.composing = ime.Composing() // draw this preedit text; do not save it.
	for _, char := range ebiten.AppendInputChars(nil) {
		g.name += string(char) // committed text
	}
	return nil
}
```

`IMEText`, `IMENumber`, and `IMEActionDone` are named versions of common
Android flags. `Show` accepts other Android `InputType` and
`EditorInfo` flags when needed. Calling it before `RegisterIMEBridge` is
safe: `IMEClient` replays the latest request when Android supplies a bridge
and after Activity recreation. `Hide` clears that saved request, so the
keyboard does not reopen later.

`ebiten.AppendInputChars` supplies committed input and belongs in the field
value. `Composing` is only the current IME preedit text and should be drawn
without appending it, otherwise composition-capable keyboards duplicate text.

The surface identifies itself as a text editor only while an explicit IME
request is active, so merely focusing a legacy game cannot open the keyboard.
A newer `Show`, `Hide`, or Activity destruction invalidates older posted
requests; this prevents a stale callback from reopening the keyboard.

Once focused, Android receives a fresh `InputConnection` and the request uses
the window-insets controller, with a posted `showSoftInput` fallback for an
AndroidX implementation that cannot supply a controller. `Hide` retains its
existing API and cancels any pending show before dismissing the keyboard.


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
