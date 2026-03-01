package main

import (
	"log"

	"github.com/erparts/go-uikit/demo"
	"github.com/hajimehoshi/ebiten/v2"
)

const GameWindowWidth int = 420
const GameWindowHeight int = 760

func main() {
	ebiten.SetWindowSize(GameWindowWidth, GameWindowHeight)
	ebiten.SetWindowTitle("Demo Ebiten")

	game := demo.New()
	if err := ebiten.RunGame(game); err != nil {
		log.Fatal(err)
	}
}
