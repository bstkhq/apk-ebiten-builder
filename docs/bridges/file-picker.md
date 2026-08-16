# Android file picker bridge

The file picker bridge opens Android's system document picker and sends Go a
local temporary copy of the selected document. It is independent from the
runtime and Back bridges and needs no storage permission or application Java
code.

## Go setup

Put this in the `package mobile` passed to `ebitenmobile bind`:

```go
package mobile

import (
	"log"
	"os"

	"github.com/bstkhq/apk-ebiten-builder/bridge"
)

var picker = bridge.NewFilePickerClient()

// FilePickerHandler must remain local so gomobile emits it for SetHandler.
type FilePickerHandler interface {
	bridge.FilePickerHandler
}

type FilePickerBridge interface {
	bridge.FilePickerOpener

	// This parameter must use the local handler type for gomobile.
	SetHandler(FilePickerHandler)
}

func RegisterFilePickerBridge(value FilePickerBridge) {
	picker.Register(value)
	value.SetHandler(picker)
}

func init() {
	picker.SetResultHandler(bridge.FilePickerHandlerFunc(handlePickerResult))
}

func chooseDocument() {
	if !picker.Open("application/pdf") {
		// Android has not registered a picker yet; keep or retry the user action.
		return
	}
}

func handlePickerResult(path, message string) {
	if message != "" {
		log.Printf("document picker failed: %s", message)
		return
	}
	if path == "" {
		// The user cancelled the picker.
		return
	}
	defer os.Remove(path)

	data, err := os.ReadFile(path)
	if err != nil {
		log.Printf("read picked document: %v", err)
		return
	}
	// Validate or import data here.
	_ = data
}
```

`FilePickerClient` retains the current opener and application result handler.
The local `FilePickerHandler` and `FilePickerBridge` are required only for
gomobile; their canonical method sets live in `bridge`.

## Opening a document

Call `FilePickerClient.Open` only in response to a user action such as a
"Choose document" button. Pass a MIME type such as `application/pdf` or
`text/plain`; an empty string accepts any file type.

`Open` returns `false` before Android has registered an opener. It returns
`true` once the request has been forwarded, not when a user has made a
selection. Selection, cancellation and errors arrive later through the result
handler.

### Example: import a PDF

A document-import flow looks like this:

1. The user presses **Import PDF** and the app requests
   `application/pdf`.
2. Android shows `ACTION_OPEN_DOCUMENT`.
3. On success, Android copies the document to the app cache and delivers its
   local path to Go.
4. Go reads and validates the PDF, then removes the temporary file when it is
   no longer needed.
5. If the user cancels, the handler receives two empty strings; if Android
   cannot open or copy the document, it receives an empty path and an error
   message.

| Result | `path` | `message` | Required application action |
| --- | --- | --- | --- |
| Selection | local temporary path | empty | Consume it, then delete it when finished. |
| Cancellation | empty | empty | Treat it as a normal cancelled action. |
| Failure | empty | non-empty | Show or log the message as appropriate. |

The selected file lives under `cacheDir/picked-files`. The application owns
that copy, and Android may clear the cache under storage pressure, so it is not
a persistent-document location.

## Lifecycle and concurrency

Android stores one in-progress picker request at a time. A second request while
one is already open produces an error result. `FilePickerClient` intentionally
does not replay `Open` after Activity recreation: reopening a system picker
must remain a fresh user action. Re-registering a recreated Activity replaces
the opener while preserving the application result handler.

Clear the result handler with `SetResultHandler(nil)` when the application no
longer wants delivery. The [picker fixture](../../tests/fixtures/picker/mobile.go)
performs one successful import, deletes the copied file and then verifies a
cancellation.
