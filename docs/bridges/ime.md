# Android IME bridge

The IME bridge controls Android's software keyboard and exposes the current
composing text to an Ebiten application. `bridge.IMEClient` retains the desired
keyboard state while Android creates or recreates its Activity.

## Go setup

Put this in the `package mobile` passed to `ebitenmobile bind`. Merge the
example `Update` method into the game's existing input loop:

```go
package mobile

import (
	"github.com/bstkhq/apk-ebiten-builder/bridge"
	"github.com/hajimehoshi/ebiten/v2"
)

var ime = bridge.NewIMEClient()

type IMEBridge interface {
	bridge.IMEBridge
}

func RegisterIMEBridge(value IMEBridge) {
	ime.Register(value)
}

func focusNameField() {
	ime.Show(bridge.IMEText, bridge.IMEActionDone)
}

func closeField() {
	ime.Hide()
}

func (g *game) Update() error {
	g.composing = ime.Composing() // Draw this preedit text; do not save it.
	for _, char := range ebiten.AppendInputChars(nil) {
		g.name += string(char) // This is committed text.
	}
	return nil
}
```

The local `IMEBridge` is required only for gomobile; its canonical method set
lives in `bridge`.

## Focus, show and hide

Call `IMEClient.Show` when an application field gains focus. Use
`bridge.IMEText` for a normal text field or `bridge.IMENumber` for a numeric
one. `bridge.IMEActionDone` is the common Done action; other Android input and
editor-option flags may be supplied when needed.

`Show` is safe before Android calls the local `RegisterIMEBridge` export. The
client stores the latest request and replays it once a bridge is available. If
Android recreates the Activity while a field remains focused, the new bridge
receives that same request. `Hide` removes the saved request and asks Android
to dismiss the keyboard, so it will not reopen after recreation.

### Example: a name field with autofocus

An app can autofocus a name field during initialization:

1. It marks the field focused and requests a text keyboard with a Done action.
2. Android registers the bridge once the Activity is available; the saved
   request opens the keyboard when the Ebiten surface has focus.
3. Each game update appends committed characters to the field value.
4. The app draws the current composing text separately while the IME is
   building a character or word.
5. When the user confirms or leaves the field, the app hides the IME.

## Committed text versus composing text

`ebiten.AppendInputChars` is committed input and belongs in the application
field value. `IMEClient.Composing` is temporary IME preedit text. Draw it near
the field, but do not append it to the committed value; composition-capable
keyboards would otherwise duplicate text.

`Composing` returns an empty string until Android has registered a bridge or
when there is no active composition.

## Android behavior

The Ebiten surface identifies itself as a text editor only while an explicit
IME request is active. This prevents a keyboard from opening merely because a
legacy game gains focus. Android obtains a fresh input connection after focus,
uses the window-insets controller where available and retains a posted
`showSoftInput` fallback for compatible AndroidX implementations.

Newer `Show`, `Hide` and Activity destruction invalidate older posted requests,
so a stale callback cannot reopen a keyboard. The
[IME fixture](../../tests/fixtures/ime/mobile.go) demonstrates autofocus,
committed input, composing-text inspection and a Go-requested hide.
