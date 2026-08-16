package mobile

import (
	"fmt"
	"image/color"
	"os"

	"github.com/bstkhq/apk-ebiten-builder/bridge"
	"github.com/hajimehoshi/ebiten/v2"
	ebitenmobile "github.com/hajimehoshi/ebiten/v2/mobile"
)

type IMEBridge interface {
	bridge.IMEBridge
}

func RegisterIMEBridge(IMEBridge) {}

// FilePickerHandler remains local so gomobile emits it in package mobile.
type FilePickerHandler interface {
	bridge.FilePickerHandler
}

// FilePickerBridge uses the local handler type required by gomobile.
type FilePickerBridge interface {
	bridge.FilePickerOpener
	SetHandler(FilePickerHandler)
}

var filePicker = bridge.NewFilePickerClient()

const pickerFixturePayload = "builder-picker-fixture-payload-v1\n"

func RegisterFilePickerBridge(value FilePickerBridge) {
	filePicker.Register(value)
	value.SetHandler(filePicker)
	fmt.Println("builder-picker-fixture: registered")
}

func handlePickerResult(path, message string) {
	if message != "" {
		fmt.Printf("builder-picker-fixture: result-error=%s\n", message)
		return
	}
	if path == "" {
		fmt.Println("builder-picker-fixture: cancellation-ok")
		return
	}

	data, err := os.ReadFile(path)
	if err != nil {
		fmt.Printf("builder-picker-fixture: result-error=read: %v\n", err)
		return
	}
	if string(data) != pickerFixturePayload {
		fmt.Printf("builder-picker-fixture: result-error=unexpected payload %q\n", data)
		return
	}
	if err := os.Remove(path); err != nil {
		fmt.Printf("builder-picker-fixture: result-error=remove: %v\n", err)
		return
	}

	fmt.Printf("builder-picker-fixture: selection-ok bytes=%d\n", len(data))
	if !filePicker.Open("text/plain") {
		fmt.Println("builder-picker-fixture: result-error=picker unavailable")
		return
	}
	fmt.Println("builder-picker-fixture: cancellation-requested")
}

// The picker is independent of AndroidBridge, so this fixture deliberately
// retains the legacy runtime exports.
func SetAndroidID(value int64) {
	fmt.Printf("builder-picker-fixture: android-id=%d\n", value)
}

func SetTimezone(value string) {
	fmt.Printf("builder-picker-fixture: timezone=%s\n", value)
}

func init() {
	filePicker.SetResultHandler(bridge.FilePickerHandlerFunc(handlePickerResult))
	ebitenmobile.SetGame(&fixtureGame{
		background: color.RGBA{R: 0x82, G: 0x55, B: 0x21, A: 0xff},
	})
}

type fixtureGame struct {
	background      color.Color
	pickerRequested bool
}

func (g *fixtureGame) Update() error {
	if g.pickerRequested || !filePicker.Open("text/plain") {
		return nil
	}
	g.pickerRequested = true
	fmt.Println("builder-picker-fixture: selection-requested")
	return nil
}

func (g *fixtureGame) Draw(screen *ebiten.Image) {
	screen.Fill(g.background)
}

func (g *fixtureGame) Layout(int, int) (int, int) { return 320, 180 }
