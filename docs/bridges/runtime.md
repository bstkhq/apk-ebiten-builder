# Android runtime bridge

The runtime bridge gives Go access to information and services owned by the
current Android Activity. It is for application bootstrap and Android-specific
operations that cannot be obtained reliably from a normal desktop build.

## Go setup

Add the builder module at the same release version as the Android template:

```bash
go get github.com/bstkhq/apk-ebiten-builder/bridge@<release-tag>
```

Put this in the `package mobile` passed to `ebitenmobile bind`:

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

Gomobile exposes only API declared in the package it binds. The local embedded
`AndroidBridge` and `RegisterAndroidBridge` are therefore required; embedding
keeps the Java-facing type local without duplicating the 21-method contract.

## What it provides

`bridge.AndroidBridge` exposes:

- app and device identity: Android ID, manufacturer, model, package and app
  version;
- Android environment: SDK level, time zone and ordered locales;
- app-private storage directories: files, no-backup files and cache;
- device state: battery, power-save and interactive state;
- active-network details: transport, metering and local IP addresses; and
- a safe application-process restart request.

Methods that depend on an Android service return an error. This lets an app
remain usable on devices where a particular service is unavailable.

`AndroidID` is the complete hexadecimal Android ID, avoiding the sign loss of
gomobile's signed `int64` binding. `BatteryLevel` is in the inclusive range
`0..1`. `Locales`, `NetworkTransports` and `LocalIPAddresses` are ordered
comma-separated strings; locales use BCP 47 tags, and network values can be
empty while the device is offline.

The bridge deliberately does not provide SSIDs, hardware serials, MAC
addresses, public IP addresses or location. Those values require permissions,
privileged access or an external service and do not belong in this optional
runtime contract.

## Lifecycle and use

Android calls the local `RegisterAndroidBridge` export during Activity
creation. Registering again replaces the old implementation after an Activity
recreation.

Use `Client.Wait` when startup needs Android data before it can continue. Use
`Client.Current` for a later, short-lived operation. Do not retain an
`AndroidBridge` value indefinitely: obtain the current value again after a
possible recreation.

### Example: initialize local settings

An app that needs an Android-derived time zone can follow this flow:

1. At startup, wait for the first runtime registration.
2. Read `AndroidID` and `TimeZone`, handling either error independently.
3. Load the time zone into Go with `time.LoadLocation` and use the Android ID
   as application data if it is needed.
4. Later, when opening a diagnostics screen, call `Current` and read values
   such as `VersionName`, `SDKInt` or `BatteryLevel` from the latest bridge.

For persistent application data, use `FilesDir`. Use `NoBackupFilesDir` for
data that must not be part of cloud backup, and `CacheDir` only for data Android
may discard. The executable [fixture](../../tests/fixtures/bridge/mobile.go)
exercises every service and Activity recreation.

### Backup and cleartext options

`ALLOW_BACKUP=false` disables the application-level cloud-backup policy.
Device-to-device transfer behavior on Android 12 and later can still vary by
manufacturer, so data that must never transfer belongs in `NoBackupFilesDir`.
The default is `true`; only `true` and `false` are accepted.

For an HTTP-only endpoint, set `USES_CLEARTEXT_TRAFFIC=true`. Empty keeps
Android's default policy, and only empty, `true` and `false` are accepted.

## Restarting the app

`RestartApp` requests a replacement of the application process, not a device
or Android-system restart. A successful call means Android accepted the
request; it does not mean the successor is ready. The template waits for the
old process to die before launching the replacement and fails closed rather
than create overlapping app processes.

## Permissions and optionality

The runtime bridge needs no dangerous permission. If the local registration
export is absent, the generated app keeps its legacy behavior instead. Legacy
`SetAndroidID` and `SetTimezone` exports continue to work for existing apps,
but new integrations should use `bridge.Client`. The template discovers the
registration reflectively: a missing export is optional, while a named export
with an incompatible signature is a startup contract error.
