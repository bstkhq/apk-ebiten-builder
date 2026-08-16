package mobile

import (
	"fmt"
	"image/color"

	"github.com/bstkhq/apk-ebiten-builder/bridge"
	"github.com/hajimehoshi/ebiten/v2"
	ebitenmobile "github.com/hajimehoshi/ebiten/v2/mobile"
)

type IMEBridge interface {
	bridge.IMEBridge
}

var ime = bridge.NewIMEClient()

func RegisterIMEBridge(value IMEBridge) {
	ime.Register(value)
}

func SetAndroidID(id int64) {
	fmt.Printf("builder-ime-fixture: android-id=%d\n", id)
}

func SetTimezone(value string) {
	fmt.Printf("builder-ime-fixture: timezone=%s\n", value)
}

func init() {
	// This models an application whose initial text field has autofocus. The
	// IMEClient retains this request until Android registers its bridge.
	fmt.Println("builder-ime-fixture: auto-focusing-text-field")
	ime.Show(bridge.IMEText, bridge.IMEActionDone)
	ebitenmobile.SetGame(&imeFixtureGame{})
}

type imeFixtureGame struct {
	input       string
	hideStarted bool
}

func (g *imeFixtureGame) Update() error {
	chars := ebiten.AppendInputChars(nil)
	if len(chars) == 0 {
		return nil
	}

	g.input += string(chars)
	fmt.Printf("builder-ime-fixture: ime-text=%s\n", g.input)
	if g.input != "ime42" || g.hideStarted {
		return nil
	}
	g.hideStarted = true

	fmt.Printf("builder-ime-fixture: composing=%q\n", ime.Composing())
	fmt.Println("builder-ime-fixture: requesting-hide")
	ime.Hide()
	return nil
}

func (g *imeFixtureGame) Draw(screen *ebiten.Image) {
	screen.Fill(color.RGBA{R: 0x65, G: 0x24, B: 0x83, A: 0xff})
}

func (g *imeFixtureGame) Layout(int, int) (int, int) { return 320, 180 }
