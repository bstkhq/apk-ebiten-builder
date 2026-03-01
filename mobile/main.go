package mobile

import (
	"github.com/erparts/go-uikit/demo"
	"github.com/hajimehoshi/ebiten/v2/mobile"
)

var g = demo.New()

func init() {
	mobile.SetGame(g)
}

// IMEBridge will be generated as a Java interface that you can implement.
type IMEBridge interface {
	Show()
	Hide()
}

// RegisterIMEBridge is exposed to Java as Mobile.registerIMEBridge(...)
func RegisterIMEBridge(b IMEBridge) {
	g.SetIMEBridge(b)
}
