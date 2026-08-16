package bridge

import "sync"

const (
	// IMEText is Android's TYPE_CLASS_TEXT input type.
	IMEText int32 = 0x00000001

	// IMENumber is Android's TYPE_CLASS_NUMBER input type.
	IMENumber int32 = 0x00000002

	// IMEActionDone is Android's IME_ACTION_DONE editor action.
	IMEActionDone int32 = 0x00000006
)

// IMEBridge is implemented by the Android template to control the software
// keyboard from Go.
//
// The template may replace the implementation when Android recreates the
// activity. Applications should use IMEClient when they need it to retain a
// request across that replacement.
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

// IMEClient owns the lifecycle of an IMEBridge registered by Android.
//
// Its zero value is ready to use. Show records the desired keyboard state even
// before Android registers a bridge, and Register replays that state when an
// Activity is created or recreated. An IMEClient must not be copied after first
// use.
type IMEClient struct {
	mu         sync.RWMutex
	applyMu    sync.Mutex
	bridge     IMEBridge
	request    imeRequest
	showing    bool
	generation uint64
}

type imeRequest struct {
	inputType  int32
	imeOptions int32
}

// NewIMEClient returns an empty IMEClient ready to receive an Android bridge.
func NewIMEClient() *IMEClient {
	return new(IMEClient)
}

// Register records value as the current Android IME bridge.
//
// It must be called by the local RegisterIMEBridge export in the package bound
// by gomobile. If Show was called earlier, Register replays its latest request
// on value. Repeated registrations replace the old bridge after Android
// recreates the Activity.
func (c *IMEClient) Register(value IMEBridge) {
	if c == nil {
		panic("bridge: Register called on a nil IMEClient")
	}
	if value == nil {
		panic("bridge: Register called with a nil IMEBridge")
	}

	c.mu.Lock()
	c.bridge = value
	c.generation++
	generation := c.generation
	request := c.request
	showing := c.showing
	c.mu.Unlock()

	if showing {
		c.applyShow(value, generation, request)
	}
}

// Show requests the software keyboard with Android inputType and imeOptions
// flags.
//
// The request remains active until Hide replaces it. Calling Show before
// Register is safe: the latest request is replayed when Android supplies a
// bridge. IMEText, IMENumber, and IMEActionDone cover common text and numeric
// fields. Callers can pass other Android flags when needed.
func (c *IMEClient) Show(inputType, imeOptions int32) {
	if c == nil {
		panic("bridge: Show called on a nil IMEClient")
	}

	c.mu.Lock()
	c.request = imeRequest{inputType: inputType, imeOptions: imeOptions}
	c.showing = true
	c.generation++
	bridge := c.bridge
	generation := c.generation
	request := c.request
	c.mu.Unlock()

	if bridge != nil {
		c.applyShow(bridge, generation, request)
	}
}

// Hide cancels the active Show request and asks the current Android bridge to
// hide the software keyboard. A later Activity registration does not reopen a
// keyboard that Hide cancelled.
func (c *IMEClient) Hide() {
	if c == nil {
		panic("bridge: Hide called on a nil IMEClient")
	}

	c.mu.Lock()
	c.showing = false
	c.generation++
	bridge := c.bridge
	generation := c.generation
	c.mu.Unlock()

	if bridge != nil {
		c.applyHide(bridge, generation)
	}
}

// Composing returns the current composing text, or an empty string before
// Android registers an IME bridge.
func (c *IMEClient) Composing() string {
	if c == nil {
		return ""
	}

	c.mu.RLock()
	bridge := c.bridge
	c.mu.RUnlock()
	if bridge == nil {
		return ""
	}
	return bridge.Composing()
}

func (c *IMEClient) applyShow(bridge IMEBridge, generation uint64, request imeRequest) {
	c.applyMu.Lock()
	defer c.applyMu.Unlock()

	c.mu.RLock()
	fresh := c.generation == generation && c.showing
	c.mu.RUnlock()
	if fresh {
		bridge.Show(request.inputType, request.imeOptions)
	}
}

func (c *IMEClient) applyHide(bridge IMEBridge, generation uint64) {
	c.applyMu.Lock()
	defer c.applyMu.Unlock()

	c.mu.RLock()
	fresh := c.generation == generation && !c.showing
	c.mu.RUnlock()
	if fresh {
		bridge.Hide()
	}
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
