package mobile

import (
	"fmt"
	"image/color"
	"sync"

	"github.com/hajimehoshi/ebiten/v2"
	ebitenmobile "github.com/hajimehoshi/ebiten/v2/mobile"
)

type IMEBridge interface {
	Show(inputType, imeOptions int32)
	Composing() string
	Hide()
}

var (
	imeMu     sync.Mutex
	imeBridge IMEBridge
)

func RegisterIMEBridge(bridge IMEBridge) {
	imeMu.Lock()
	imeBridge = bridge
	imeMu.Unlock()

	// Deliberately request the IME synchronously during Activity.onCreate. This
	// is the lifecycle edge that used to lose showSoftInput before the Ebiten
	// surface had window focus.
	fmt.Println("builder-ime-fixture: requesting-ime-during-registration")
	bridge.Show(1, 6) // TYPE_CLASS_TEXT, IME_ACTION_DONE
}

func SetAndroidID(id int64) {
	fmt.Printf("builder-ime-fixture: android-id=%d\n", id)
}

func SetTimezone(value string) {
	fmt.Printf("builder-ime-fixture: timezone=%s\n", value)
}

func init() {
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

	imeMu.Lock()
	bridge := imeBridge
	imeMu.Unlock()
	if bridge == nil {
		return fmt.Errorf("IMEBridge was not registered")
	}

	fmt.Printf("builder-ime-fixture: composing=%q\n", bridge.Composing())
	fmt.Println("builder-ime-fixture: requesting-hide")
	bridge.Hide()
	return nil
}

func (g *imeFixtureGame) Draw(screen *ebiten.Image) {
	screen.Fill(color.RGBA{R: 0x65, G: 0x24, B: 0x83, A: 0xff})
}

func (g *imeFixtureGame) Layout(int, int) (int, int) { return 320, 180 }
