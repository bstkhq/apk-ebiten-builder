# Android file picker bridge

The file picker bridge opens Android's system document picker and sends Go a
local temporary copy of the selected document. It is independent from the
runtime and Back bridges and needs no storage permission or application Java
code.

The complete local gomobile adapter and Go result-handler setup are in the
[README](../../README.md#optional-android-file-picker). It uses
`bridge.FilePickerClient` to retain the current opener and application result
handler.

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
