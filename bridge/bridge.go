// Package bridge defines the Go-side contracts for optional Android runtime
// services and callbacks supplied by apk-ebiten-builder.
//
// A gomobile application must still expose locally named interfaces and
// registration functions from its package mobile so the generated Java binding
// has entry points. This package owns the canonical method sets and
// lifecycle-safe registration state for AndroidBridge and IMEClient, so
// applications do not need to duplicate either of them.
package bridge

import (
	"context"
	"sync"
)

// AndroidBridge is implemented by the Android template at runtime.
//
// Methods with an error result can fail when Android does not expose the
// corresponding service. Their gomobile Java counterparts declare throws
// Exception. Implementations are replaced when Android recreates the activity,
// so callers that need a current value should obtain it through Client.Current
// or Client.Wait rather than retaining an old value indefinitely.
type AndroidBridge interface {
	// AndroidID returns Settings.Secure.ANDROID_ID as its complete hexadecimal
	// string. It returns an error when Android does not provide a valid ID.
	AndroidID() (string, error)

	// Manufacturer returns Build.MANUFACTURER, or Android's "unknown" value
	// when the device does not report one.
	Manufacturer() string

	// Model returns Build.MODEL, or Android's "unknown" value when the device
	// does not report one.
	Model() string

	// PackageName returns the installed application's package name.
	PackageName() string

	// VersionName returns the application's Android versionName.
	VersionName() (string, error)

	// VersionCode returns the application's long Android version code.
	VersionCode() (int64, error)

	// AndroidVersion returns Build.VERSION.RELEASE, or Android's "unknown"
	// value when the release is unavailable.
	AndroidVersion() string

	// SDKInt returns Build.VERSION.SDK_INT.
	SDKInt() int32

	// TimeZone returns Android's current time-zone identifier. Android normally
	// supplies an IANA name suitable for time.LoadLocation.
	TimeZone() (string, error)

	// Locales returns the ordered device locales as comma-separated BCP 47 tags.
	Locales() (string, error)

	// FilesDir returns the absolute path to the app-private persistent files
	// directory.
	FilesDir() (string, error)

	// NoBackupFilesDir returns the absolute path to the app-private persistent
	// directory that Android excludes from cloud backup.
	NoBackupFilesDir() (string, error)

	// CacheDir returns the absolute path to the app-private cache directory.
	// Android may delete files in this directory when space is needed.
	CacheDir() (string, error)

	// BatteryLevel returns the current battery charge in the inclusive range
	// 0..1.
	BatteryLevel() (float64, error)

	// BatteryPlugged reports whether Android currently sees external power.
	BatteryPlugged() (bool, error)

	// Interactive reports whether the device is in an interactive state, such
	// as having an active display.
	Interactive() (bool, error)

	// PowerSaveMode reports whether Android's battery-saver mode is enabled.
	PowerSaveMode() (bool, error)

	// NetworkTransports returns the active network transports as a
	// comma-separated list. It is empty when there is no active network.
	NetworkTransports() (string, error)

	// NetworkMetered reports whether Android considers the active network
	// metered.
	NetworkMetered() (bool, error)

	// LocalIPAddresses returns non-loopback IP addresses for the active network,
	// with IPv4 addresses before IPv6 addresses. It is empty while offline.
	LocalIPAddresses() (string, error)

	// RestartApp requests a safe replacement of this app process. A nil error
	// means Android accepted the request, not that the successor is ready.
	RestartApp() error
}

// Client stores the Android bridge registered by the activity.
//
// Its zero value is ready to use. Register is safe to call repeatedly: each
// registration replaces the previous bridge, as happens after an Activity is
// recreated. A Client is intended to be owned by one mobile package and must
// not be copied after first use.
type Client struct {
	mu        sync.RWMutex
	bridge    AndroidBridge
	ready     chan struct{}
	readyOnce sync.Once
}

// NewClient returns an empty Client ready to receive an Android bridge.
func NewClient() *Client {
	return new(Client)
}

// Default is convenient for applications with a single Android activity.
// Applications that benefit from explicit dependency ownership can use
// NewClient instead.
var Default = NewClient()

// Register records bridge as the current Android bridge. It must be called by
// the exported RegisterAndroidBridge function in the package bound by
// ebitenmobile.
func (c *Client) Register(bridge AndroidBridge) {
	if c == nil {
		panic("bridge: Register called on a nil Client")
	}
	if bridge == nil {
		panic("bridge: Register called with a nil AndroidBridge")
	}

	c.mu.Lock()
	if c.ready == nil {
		c.ready = make(chan struct{})
	}
	ready := c.ready
	c.bridge = bridge
	c.mu.Unlock()
	c.readyOnce.Do(func() { close(ready) })
}

// Current returns the most recently registered Android bridge.
func (c *Client) Current() (AndroidBridge, bool) {
	if c == nil {
		return nil, false
	}

	c.mu.RLock()
	bridge := c.bridge
	c.mu.RUnlock()
	return bridge, bridge != nil
}

// Wait blocks until Android registers a bridge or ctx is cancelled. It returns
// the newest bridge available at the time it completes.
func (c *Client) Wait(ctx context.Context) (AndroidBridge, error) {
	if c == nil {
		panic("bridge: Wait called on a nil Client")
	}
	if ctx == nil {
		panic("bridge: Wait called with a nil Context")
	}

	for {
		if bridge, ok := c.Current(); ok {
			return bridge, nil
		}

		select {
		case <-c.readySignal():
			// Register won the race with Current; read the value under the lock.
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}
}

func (c *Client) readySignal() <-chan struct{} {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.ready == nil {
		c.ready = make(chan struct{})
	}
	return c.ready
}
