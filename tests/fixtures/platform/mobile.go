package mobile

import (
	"fmt"
	"image/color"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/hajimehoshi/ebiten/v2"
	ebitenmobile "github.com/hajimehoshi/ebiten/v2/mobile"
)

type IMEBridge interface {
	Show(inputType, imeOptions int32)
	Composing() string
	Hide()
}

func RegisterIMEBridge(IMEBridge) {}

// AndroidPlatform is the complete runtime contract implemented by the APK
// builder. Methods returning an error map to Java methods that throw Exception.
type AndroidPlatform interface {
	AndroidID() (string, error)
	Manufacturer() string
	Model() string
	PackageName() string
	VersionName() (string, error)
	VersionCode() (int64, error)
	AndroidVersion() string
	SDKInt() int32
	TimeZone() (string, error)
	Locales() (string, error)
	FilesDir() (string, error)
	NoBackupFilesDir() (string, error)
	CacheDir() (string, error)
	BatteryLevel() (float64, error)
	BatteryPlugged() (bool, error)
	Interactive() (bool, error)
	PowerSaveMode() (bool, error)
	NetworkTransports() (string, error)
	NetworkMetered() (bool, error)
	LocalIPAddresses() (string, error)
	RestartApp() error
}

func RegisterAndroidPlatform(platform AndroidPlatform) {
	noBackupDir, err := inspectPlatform(platform)
	if err != nil {
		fmt.Printf("builder-platform-fixture: runtime-error=%v\n", err)
		return
	}
	fmt.Println("builder-platform-fixture: runtime-ok")

	marker := filepath.Join(noBackupDir, "builder-platform-restarted-v1")
	if _, err := os.Stat(marker); err == nil {
		fmt.Println("builder-platform-fixture: successor-ready")
		return
	} else if !os.IsNotExist(err) {
		fmt.Printf("builder-platform-fixture: marker-stat-error=%v\n", err)
		return
	}
	if err := os.WriteFile(marker, []byte("restart requested\n"), 0o600); err != nil {
		fmt.Printf("builder-platform-fixture: marker-write-error=%v\n", err)
		return
	}

	go func() {
		time.Sleep(1500 * time.Millisecond)
		fmt.Println("builder-platform-fixture: requesting-restart")
		if err := platform.RestartApp(); err != nil {
			fmt.Printf("builder-platform-fixture: restart-error=%v\n", err)
		}
	}()
}

func inspectPlatform(platform AndroidPlatform) (string, error) {
	androidID, err := platform.AndroidID()
	if err != nil {
		return "", fmt.Errorf("AndroidID: %w", err)
	}
	if len(androidID) == 0 || len(androidID) > 16 {
		return "", fmt.Errorf("AndroidID: invalid length %d", len(androidID))
	}
	if _, err := strconv.ParseUint(androidID, 16, 64); err != nil {
		return "", fmt.Errorf("AndroidID: invalid hexadecimal value: %w", err)
	}
	fmt.Printf("builder-platform-fixture: android-id=%s\n", androidID)

	manufacturer := platform.Manufacturer()
	model := platform.Model()
	packageName := platform.PackageName()
	androidVersion := platform.AndroidVersion()
	sdkInt := platform.SDKInt()
	if manufacturer == "" || model == "" || packageName == "" || androidVersion == "" || sdkInt <= 0 {
		return "", fmt.Errorf("static runtime metadata is incomplete")
	}
	fmt.Printf(
		"builder-platform-fixture: device=%s/%s android=%s sdk=%d package=%s\n",
		manufacturer,
		model,
		androidVersion,
		sdkInt,
		packageName,
	)

	versionName, err := platform.VersionName()
	if err != nil {
		return "", fmt.Errorf("VersionName: %w", err)
	}
	versionCode, err := platform.VersionCode()
	if err != nil {
		return "", fmt.Errorf("VersionCode: %w", err)
	}
	if versionName == "" || versionCode <= 0 {
		return "", fmt.Errorf("invalid application version %q/%d", versionName, versionCode)
	}
	fmt.Printf("builder-platform-fixture: version=%s/%d\n", versionName, versionCode)

	timeZone, err := platform.TimeZone()
	if err != nil {
		return "", fmt.Errorf("TimeZone: %w", err)
	}
	locales, err := platform.Locales()
	if err != nil {
		return "", fmt.Errorf("Locales: %w", err)
	}
	if timeZone == "" || locales == "" {
		return "", fmt.Errorf("locale metadata is incomplete")
	}
	fmt.Printf("builder-platform-fixture: timezone=%s locales=%s\n", timeZone, locales)

	filesDir, err := platform.FilesDir()
	if err != nil {
		return "", fmt.Errorf("FilesDir: %w", err)
	}
	noBackupDir, err := platform.NoBackupFilesDir()
	if err != nil {
		return "", fmt.Errorf("NoBackupFilesDir: %w", err)
	}
	cacheDir, err := platform.CacheDir()
	if err != nil {
		return "", fmt.Errorf("CacheDir: %w", err)
	}
	if !filepath.IsAbs(filesDir) || !filepath.IsAbs(noBackupDir) || !filepath.IsAbs(cacheDir) {
		return "", fmt.Errorf("Android directories must be absolute")
	}
	fmt.Printf(
		"builder-platform-fixture: dirs=%s|%s|%s\n",
		filesDir,
		noBackupDir,
		cacheDir,
	)

	batteryLevel, err := platform.BatteryLevel()
	if err != nil {
		return "", fmt.Errorf("BatteryLevel: %w", err)
	}
	batteryPlugged, err := platform.BatteryPlugged()
	if err != nil {
		return "", fmt.Errorf("BatteryPlugged: %w", err)
	}
	if batteryLevel < 0 || batteryLevel > 1 {
		return "", fmt.Errorf("battery level outside 0..1: %f", batteryLevel)
	}
	interactive, err := platform.Interactive()
	if err != nil {
		return "", fmt.Errorf("Interactive: %w", err)
	}
	powerSave, err := platform.PowerSaveMode()
	if err != nil {
		return "", fmt.Errorf("PowerSaveMode: %w", err)
	}
	fmt.Printf(
		"builder-platform-fixture: power=%.3f plugged=%t interactive=%t save=%t\n",
		batteryLevel,
		batteryPlugged,
		interactive,
		powerSave,
	)

	transports, err := platform.NetworkTransports()
	if err != nil {
		return "", fmt.Errorf("NetworkTransports: %w", err)
	}
	metered, err := platform.NetworkMetered()
	if err != nil {
		return "", fmt.Errorf("NetworkMetered: %w", err)
	}
	addresses, err := platform.LocalIPAddresses()
	if err != nil {
		return "", fmt.Errorf("LocalIPAddresses: %w", err)
	}
	if strings.Contains(transports, " ") || strings.Contains(addresses, " ") {
		return "", fmt.Errorf("network lists must be compact comma-separated values")
	}
	fmt.Printf(
		"builder-platform-fixture: network=%s metered=%t ips=%s\n",
		transports,
		metered,
		addresses,
	)

	return noBackupDir, nil
}

func init() {
	ebitenmobile.SetGame(fixtureGame{
		background: color.RGBA{R: 0x3b, G: 0x7d, B: 0x23, A: 0xff},
	})
}

type fixtureGame struct {
	background color.Color
}

func (g fixtureGame) Update() error { return nil }

func (g fixtureGame) Draw(screen *ebiten.Image) {
	screen.Fill(g.background)
}

func (g fixtureGame) Layout(int, int) (int, int) { return 320, 180 }
