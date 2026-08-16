package bridge

// IMEBridge is implemented by the Android template to control the software
// keyboard from Go.
//
// The template may replace the implementation when Android recreates the
// activity. Applications should replace any stored implementation when their
// package mobile adapter receives a later registration.
type IMEBridge interface {
	// Show requests the software keyboard. inputType uses Android InputType
	// flags and imeOptions uses Android EditorInfo IME option flags.
	Show(inputType, imeOptions int32)

	// Composing returns the current composing text from the active input
	// connection. It returns an empty string when there is no composition.
	Composing() string

	// Hide cancels a pending Show request and asks Android to hide the software
	// keyboard.
	Hide()
}

// BackHandler handles one semantic Android Back event.
type BackHandler interface {
	// OnBack runs on Android's UI thread. Return true to consume the event, or
	// false to continue the Activity's default Back behavior.
	OnBack() bool
}

// BackBridge is implemented by the Android template when an application opts
// in to Back handling.
type BackBridge interface {
	// SetHandler replaces the current Back handler. Passing nil restores the
	// Activity's default Back behavior.
	SetHandler(BackHandler)
}

// FilePickerHandler receives one result from the Android system document
// picker.
type FilePickerHandler interface {
	// OnResult receives either a local temporary path and an empty message on
	// success, two empty strings on cancellation, or an empty path and a
	// non-empty message on failure. The application owns a successful temporary
	// file and should remove it when no longer needed.
	OnResult(path, message string)
}

// FilePickerOpener starts Android's system document picker.
//
// It is separate from FilePickerBridge so a gomobile adapter can embed this
// method without duplicating it while keeping its callback parameter local.
type FilePickerOpener interface {
	// Open starts Android's document picker. An empty mimeType accepts any file
	// type; otherwise it is passed to Android as the requested MIME type.
	Open(mimeType string)
}

// FilePickerBridge is implemented by the Android template when an application
// opts in to the system document picker.
type FilePickerBridge interface {
	FilePickerOpener

	// SetHandler replaces the receiver for picker results. Passing nil stops
	// result delivery.
	SetHandler(FilePickerHandler)
}
