# Android bridge guides

`apk-ebiten-builder` can expose four independent Android bridges to the Go
package bound by `ebitenmobile`. Each guide explains the runtime behavior,
lifecycle and a representative user-facing flow. The complete Go adapters are
kept in the root [README](../../README.md), so there is one copy to maintain.

| Bridge | Use it for | Guide | Go adapter |
| --- | --- | --- | --- |
| Runtime | Device, application, storage, network and safe process-restart services | [Runtime bridge](runtime.md) | [README](../../README.md#android-runtime-bridge) |
| Back | Let Go consume or delegate Android Back events | [Back bridge](back.md) | [README](../../README.md#android-back-bridge) |
| File picker | Ask Android to choose a document and receive a local temporary copy | [File picker](file-picker.md) | [README](../../README.md#optional-android-file-picker) |
| IME | Show and hide the software keyboard, and read composing text | [IME](ime.md) | [README](../../README.md#android-ime-lifecycle) |

All bridges are optional. An application only exports the local gomobile
adapter for the bridge it uses. Local interfaces are intentional: gomobile
only generates Java-facing interfaces declared in the bound `package mobile`.
Those local interfaces embed the documented `bridge` contracts instead of
repeating their methods.
