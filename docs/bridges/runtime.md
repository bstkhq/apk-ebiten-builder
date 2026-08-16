# Android runtime bridge

The runtime bridge gives Go access to information and services owned by the
current Android Activity. It is for application bootstrap and Android-specific
operations that cannot be obtained reliably from a normal desktop build.

The complete Go adapter is in the
[README](../../README.md#android-runtime-bridge). It defines one local
`AndroidBridge` interface and registers it with `bridge.Client`.

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
may discard. The complete bootstrap code is in the
[README example](../../README.md#android-runtime-bridge); the executable
[fixture](../../tests/fixtures/bridge/mobile.go) exercises every service and
Activity recreation.

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
but new integrations should use `bridge.Client`.
