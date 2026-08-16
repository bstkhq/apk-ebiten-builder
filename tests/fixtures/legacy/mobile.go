package mobile

import (
	"fmt"
	"image/color"

	"github.com/hajimehoshi/ebiten/v2"
	ebitenmobile "github.com/hajimehoshi/ebiten/v2/mobile"
)

type IMEBridge interface {
	Show(inputType, imeOptions int32)
	Composing() string
	Hide()
}

func RegisterIMEBridge(IMEBridge) {}

// SetAndroidID is retained to prove that pre-AndroidPlatform applications
// continue to receive the legacy signed value.
func SetAndroidID(value int64) {
	fmt.Printf("builder-legacy-fixture: android-id=%d\n", value)
}

// SetTimezone is optional in legacy applications.
func SetTimezone(value string) {
	fmt.Printf("builder-legacy-fixture: timezone=%s\n", value)
}

func init() {
	ebitenmobile.SetGame(fixtureGame{
		background: color.RGBA{R: 0x21, G: 0x55, B: 0x82, A: 0xff},
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
