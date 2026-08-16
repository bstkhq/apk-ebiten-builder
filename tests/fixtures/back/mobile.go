package mobile

import (
	"fmt"
	"image/color"
	"sync/atomic"

	"github.com/bstkhq/apk-ebiten-builder/bridge"
	"github.com/hajimehoshi/ebiten/v2"
	ebitenmobile "github.com/hajimehoshi/ebiten/v2/mobile"
)

type IMEBridge interface {
	bridge.IMEBridge
}

func RegisterIMEBridge(IMEBridge) {}

// Legacy exports remain only so this fixture also exercises the builder's
// unchanged fallback when AndroidBridge is not declared.
func SetAndroidID(id int64) {
	fmt.Printf("builder-back-fixture: android-id=%d\n", id)
}

func SetTimezone(value string) {
	fmt.Printf("builder-back-fixture: timezone=%s\n", value)
}

type BackHandler interface {
	bridge.BackHandler
}

type BackBridge interface {
	// SetHandler uses the local BackHandler so gomobile emits both callback
	// interfaces in package mobile.
	SetHandler(BackHandler)
}

var (
	back      = bridge.NewBackClient()
	backCalls atomic.Int32
)

func RegisterBackBridge(value BackBridge) {
	value.SetHandler(back)
	fmt.Println("builder-back-fixture: handler-ready")
}

func init() {
	back.SetHandler(bridge.BackHandlerFunc(handleBack))
	ebitenmobile.SetGame(backFixtureGame{})
}

func handleBack() bool {
	call := backCalls.Add(1)
	consumed := call == 1
	fmt.Printf("builder-back-fixture: call=%d consumed=%t\n", call, consumed)
	return consumed
}

type backFixtureGame struct{}

func (backFixtureGame) Update() error { return nil }

func (backFixtureGame) Draw(screen *ebiten.Image) {
	screen.Fill(color.RGBA{R: 0x1c, G: 0x48, B: 0x7d, A: 0xff})
}

func (backFixtureGame) Layout(int, int) (int, int) { return 320, 180 }
