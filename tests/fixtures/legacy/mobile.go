package mobile

import (
	"image/color"

	"github.com/hajimehoshi/ebiten/v2"
	ebitenmobile "github.com/hajimehoshi/ebiten/v2/mobile"
)

// IMEBridge is intentionally the only Java-to-Go bridge in this legacy
// fixture. It proves that the optional platform and Back hooks are not static
// dependencies of the generated Android project.
type IMEBridge interface {
	Show(inputType, imeOptions int32)
	Composing() string
	Hide()
}

func RegisterIMEBridge(IMEBridge) {}

func SetAndroidID(int64) {}

func init() {
	ebitenmobile.SetGame(fixtureGame{background: color.RGBA{R: 0x21, G: 0x55, B: 0x82, A: 0xff}})
}

type fixtureGame struct {
	background color.Color
}

func (g fixtureGame) Update() error { return nil }

func (g fixtureGame) Draw(screen *ebiten.Image) {
	screen.Fill(g.background)
}

func (g fixtureGame) Layout(int, int) (int, int) { return 320, 180 }
