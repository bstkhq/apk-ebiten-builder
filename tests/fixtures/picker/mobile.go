package mobile

import (
	"fmt"
	"image/color"
	"os"
	"sync"

	"github.com/hajimehoshi/ebiten/v2"
	ebitenmobile "github.com/hajimehoshi/ebiten/v2/mobile"
)

type IMEBridge interface {
	Show(inputType, imeOptions int32)
	Composing() string
	Hide()
}

func RegisterIMEBridge(IMEBridge) {}

// FilePickerHandler receives the asynchronous result of a picker request.
type FilePickerHandler interface {
	OnResult(path, message string)
}

// FilePickerBridge is implemented by the APK builder when this optional
// contract is present.
type FilePickerBridge interface {
	SetHandler(FilePickerHandler)
	Open(mimeType string)
}

var filePicker FilePickerBridge
var openFilePicker sync.Once

const pickerFixturePayload = "builder-picker-fixture-payload-v1\n"

func RegisterFilePickerBridge(bridge FilePickerBridge) {
	filePicker = bridge
	bridge.SetHandler(filePickerHandler{})
	fmt.Println("builder-picker-fixture: registered")
}

type filePickerHandler struct{}

func (filePickerHandler) OnResult(path, message string) {
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
	filePicker.Open("text/plain")
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
	ebitenmobile.SetGame(fixtureGame{
		background: color.RGBA{R: 0x82, G: 0x55, B: 0x21, A: 0xff},
	})
}

type fixtureGame struct {
	background color.Color
}

func (g fixtureGame) Update() error {
	openFilePicker.Do(func() {
		filePicker.Open("text/plain")
		fmt.Println("builder-picker-fixture: selection-requested")
	})
	return nil
}

func (g fixtureGame) Draw(screen *ebiten.Image) {
	screen.Fill(g.background)
}

func (g fixtureGame) Layout(int, int) (int, int) { return 320, 180 }
