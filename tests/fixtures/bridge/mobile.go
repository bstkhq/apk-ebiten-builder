package mobile

import (
	"context"
	"fmt"
	"image/color"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/bstkhq/apk-ebiten-builder/bridge"
	"github.com/hajimehoshi/ebiten/v2"
	ebitenmobile "github.com/hajimehoshi/ebiten/v2/mobile"
)

type IMEBridge interface {
	Show(inputType, imeOptions int32)
	Composing() string
	Hide()
}

func RegisterIMEBridge(IMEBridge) {}

// AndroidBridge is local so gomobile exports it from package mobile. Embedding
// keeps the complete contract owned by bridge instead of copying its methods.
type AndroidBridge interface {
	bridge.AndroidBridge
}

var (
	androidRuntime  = bridge.NewClient()
	restartRequest  sync.Once
	processIdentity = fmt.Sprintf("%d:%d", os.Getpid(), time.Now().UnixNano())
)

// RegisterAndroidBridge is the only gomobile-facing adapter applications need.
// The complete Android contract and its lifecycle handling live in bridge.
func RegisterAndroidBridge(value AndroidBridge) {
	androidRuntime.Register(value)
}

func runBridgeScenario() {
	value, err := androidRuntime.Wait(context.Background())
	if err != nil {
		fmt.Printf("builder-bridge-fixture: runtime-wait-error=%v\n", err)
		return
	}

	noBackupDir, err := inspectBridge(value)
	if err != nil {
		fmt.Printf("builder-bridge-fixture: runtime-error=%v\n", err)
		return
	}
	fmt.Println("builder-bridge-fixture: runtime-ok")

	marker := filepath.Join(noBackupDir, "builder-bridge-restarted-v1")
	markerIdentity, err := os.ReadFile(marker)
	if err == nil {
		if strings.TrimSpace(string(markerIdentity)) != processIdentity {
			fmt.Println("builder-bridge-fixture: successor-ready")
			return
		}
		fmt.Println("builder-bridge-fixture: activity-recreated-in-initial-process")
	} else if !os.IsNotExist(err) {
		fmt.Printf("builder-bridge-fixture: marker-read-error=%v\n", err)
		return
	} else if err := os.WriteFile(marker, []byte(processIdentity+"\n"), 0o600); err != nil {
		fmt.Printf("builder-bridge-fixture: marker-write-error=%v\n", err)
		return
	}

	restartRequest.Do(func() {
		go func() {
			time.Sleep(1500 * time.Millisecond)
			fmt.Println("builder-bridge-fixture: requesting-restart")
			if err := value.RestartApp(); err != nil {
				fmt.Printf("builder-bridge-fixture: restart-error=%v\n", err)
			}
		}()
	})
}

func inspectBridge(bridge AndroidBridge) (string, error) {
	androidID, err := bridge.AndroidID()
	if err != nil {
		return "", fmt.Errorf("AndroidID: %w", err)
	}
	if len(androidID) == 0 || len(androidID) > 16 {
		return "", fmt.Errorf("AndroidID: invalid length %d", len(androidID))
	}
	if _, err := strconv.ParseUint(androidID, 16, 64); err != nil {
		return "", fmt.Errorf("AndroidID: invalid hexadecimal value: %w", err)
	}
	fmt.Printf("builder-bridge-fixture: android-id=%s\n", androidID)

	manufacturer := bridge.Manufacturer()
	model := bridge.Model()
	packageName := bridge.PackageName()
	androidVersion := bridge.AndroidVersion()
	sdkInt := bridge.SDKInt()
	if manufacturer == "" || model == "" || packageName == "" || androidVersion == "" || sdkInt <= 0 {
		return "", fmt.Errorf("static runtime metadata is incomplete")
	}
	fmt.Printf(
		"builder-bridge-fixture: device=%s/%s android=%s sdk=%d package=%s\n",
		manufacturer,
		model,
		androidVersion,
		sdkInt,
		packageName,
	)

	versionName, err := bridge.VersionName()
	if err != nil {
		return "", fmt.Errorf("VersionName: %w", err)
	}
	versionCode, err := bridge.VersionCode()
	if err != nil {
		return "", fmt.Errorf("VersionCode: %w", err)
	}
	if versionName == "" || versionCode <= 0 {
		return "", fmt.Errorf("invalid application version %q/%d", versionName, versionCode)
	}
	fmt.Printf("builder-bridge-fixture: version=%s/%d\n", versionName, versionCode)

	timeZone, err := bridge.TimeZone()
	if err != nil {
		return "", fmt.Errorf("TimeZone: %w", err)
	}
	locales, err := bridge.Locales()
	if err != nil {
		return "", fmt.Errorf("Locales: %w", err)
	}
	if timeZone == "" || locales == "" {
		return "", fmt.Errorf("locale metadata is incomplete")
	}
	fmt.Printf("builder-bridge-fixture: timezone=%s locales=%s\n", timeZone, locales)

	filesDir, err := bridge.FilesDir()
	if err != nil {
		return "", fmt.Errorf("FilesDir: %w", err)
	}
	noBackupDir, err := bridge.NoBackupFilesDir()
	if err != nil {
		return "", fmt.Errorf("NoBackupFilesDir: %w", err)
	}
	cacheDir, err := bridge.CacheDir()
	if err != nil {
		return "", fmt.Errorf("CacheDir: %w", err)
	}
	if !filepath.IsAbs(filesDir) || !filepath.IsAbs(noBackupDir) || !filepath.IsAbs(cacheDir) {
		return "", fmt.Errorf("Android directories must be absolute")
	}
	fmt.Printf(
		"builder-bridge-fixture: dirs=%s|%s|%s\n",
		filesDir,
		noBackupDir,
		cacheDir,
	)

	batteryLevel, err := bridge.BatteryLevel()
	if err != nil {
		return "", fmt.Errorf("BatteryLevel: %w", err)
	}
	batteryPlugged, err := bridge.BatteryPlugged()
	if err != nil {
		return "", fmt.Errorf("BatteryPlugged: %w", err)
	}
	if batteryLevel < 0 || batteryLevel > 1 {
		return "", fmt.Errorf("battery level outside 0..1: %f", batteryLevel)
	}
	interactive, err := bridge.Interactive()
	if err != nil {
		return "", fmt.Errorf("Interactive: %w", err)
	}
	powerSave, err := bridge.PowerSaveMode()
	if err != nil {
		return "", fmt.Errorf("PowerSaveMode: %w", err)
	}
	fmt.Printf(
		"builder-bridge-fixture: power=%.3f plugged=%t interactive=%t save=%t\n",
		batteryLevel,
		batteryPlugged,
		interactive,
		powerSave,
	)

	transports, err := bridge.NetworkTransports()
	if err != nil {
		return "", fmt.Errorf("NetworkTransports: %w", err)
	}
	metered, err := bridge.NetworkMetered()
	if err != nil {
		return "", fmt.Errorf("NetworkMetered: %w", err)
	}
	addresses, err := bridge.LocalIPAddresses()
	if err != nil {
		return "", fmt.Errorf("LocalIPAddresses: %w", err)
	}
	if strings.Contains(transports, " ") || strings.Contains(addresses, " ") {
		return "", fmt.Errorf("network lists must be compact comma-separated values")
	}
	fmt.Printf(
		"builder-bridge-fixture: network=%s metered=%t ips=%s\n",
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
	go runBridgeScenario()
}

type fixtureGame struct {
	background color.Color
}

func (g fixtureGame) Update() error { return nil }

func (g fixtureGame) Draw(screen *ebiten.Image) {
	screen.Fill(g.background)
}

func (g fixtureGame) Layout(int, int) (int, int) { return 320, 180 }
