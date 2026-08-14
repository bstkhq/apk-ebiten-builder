package mobile

import (
	"fmt"
	"image/color"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/hajimehoshi/ebiten/v2"
	ebitenmobile "github.com/hajimehoshi/ebiten/v2/mobile"
)

type IMEBridge interface {
	Show(inputType, imeOptions int32)
	Composing() string
	Hide()
}

func RegisterIMEBridge(bridge IMEBridge) {
	go func() {
		time.Sleep(2500 * time.Millisecond)
		fmt.Println("builder-hooks-fixture: requesting-ime")
		bridge.Show(1, 6) // TYPE_CLASS_TEXT, IME_ACTION_DONE
	}()
}

func SetAndroidID(id int64) {
	fmt.Printf("builder-hooks-fixture: android_id=%d\n", id)
}

// PlatformBridge is implemented by the builder through a dynamic Java proxy.
type PlatformBridge interface {
	NoBackupFilesDir() string
	LocaleTags() string
	RestartApp()
}

var registerOnce sync.Once

func RegisterPlatformBridge(bridge PlatformBridge) {
	registerOnce.Do(func() {
		directory := bridge.NoBackupFilesDir()
		fmt.Printf("builder-hooks-fixture: no_backup=%s locale=%s\n", directory, bridge.LocaleTags())

		marker := filepath.Join(directory, "builder-hooks-restarted-v1")
		if _, err := os.Stat(marker); err == nil {
			fmt.Println("builder-hooks-fixture: successor-ready")
			return
		} else if !os.IsNotExist(err) {
			fmt.Printf("builder-hooks-fixture: marker stat error: %v\n", err)
			return
		}
		if err := os.WriteFile(marker, []byte("restart requested\n"), 0o600); err != nil {
			fmt.Printf("builder-hooks-fixture: marker error: %v\n", err)
			return
		}

		go func() {
			time.Sleep(1500 * time.Millisecond)
			fmt.Println("builder-hooks-fixture: requesting-restart")
			bridge.RestartApp()
		}()
	})
}

func OnBackPressed() bool {
	fmt.Println("builder-hooks-fixture: back-consumed")
	return true
}

func init() {
	ebitenmobile.SetGame(&fixtureGame{background: color.RGBA{R: 0x3b, G: 0x7d, B: 0x23, A: 0xff}})
}

type fixtureGame struct {
	background color.Color
	input      string
}

func (g *fixtureGame) Update() error {
	if chars := ebiten.AppendInputChars(nil); len(chars) > 0 {
		g.input += string(chars)
		fmt.Printf("builder-hooks-fixture: ime-text=%s\n", g.input)
	}
	return nil
}

func (g *fixtureGame) Draw(screen *ebiten.Image) {
	screen.Fill(g.background)
}

func (g *fixtureGame) Layout(int, int) (int, int) { return 320, 180 }
