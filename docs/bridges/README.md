# Android bridge guides

`apk-ebiten-builder` can expose four independent Android bridges to the Go
package bound by `ebitenmobile`. Each guide contains the complete Go adapter,
runtime behavior, lifecycle rules and a representative user-facing example.

| Bridge | Use it for | Guide |
| --- | --- | --- |
| Runtime | Device, application, storage, network and safe process-restart services | [Runtime bridge](runtime.md) |
| Back | Let Go consume or delegate Android Back events | [Back bridge](back.md) |
| File picker | Ask Android to choose a document and receive a local temporary copy | [File picker](file-picker.md) |
| IME | Show and hide the software keyboard, and read composing text | [IME](ime.md) |

All bridges are optional. An application only exports the local gomobile
adapter for the bridge it uses. Local interfaces are intentional: gomobile
only generates Java-facing interfaces declared in the bound `package mobile`.
Those local interfaces embed the documented `bridge` contracts instead of
repeating their methods.
