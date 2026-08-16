package bridge

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"
)

// fakeBridge embeds the contract because these tests exercise Client's
// lifecycle rather than any Android service call.
type fakeBridge struct {
	AndroidBridge
	id int
}

type fakeIMEBridge struct {
	mu        sync.Mutex
	shows     []imeRequest
	hides     int
	composing string
}

func (b *fakeIMEBridge) Show(inputType, imeOptions int32) {
	b.mu.Lock()
	b.shows = append(b.shows, imeRequest{inputType: inputType, imeOptions: imeOptions})
	b.mu.Unlock()
}

func (b *fakeIMEBridge) Composing() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.composing
}

func (b *fakeIMEBridge) Hide() {
	b.mu.Lock()
	b.hides++
	b.mu.Unlock()
}

func (b *fakeIMEBridge) calls() ([]imeRequest, int) {
	b.mu.Lock()
	defer b.mu.Unlock()
	return append([]imeRequest(nil), b.shows...), b.hides
}

func TestClientWaitsForRegistration(t *testing.T) {
	client := NewClient()
	waitResult := make(chan AndroidBridge, 1)
	waitError := make(chan error, 1)

	go func() {
		value, err := client.Wait(context.Background())
		waitResult <- value
		waitError <- err
	}()

	select {
	case <-waitResult:
		t.Fatal("Wait returned before Register")
	case <-time.After(20 * time.Millisecond):
	}

	want := fakeBridge{id: 1}
	client.Register(want)

	if err := <-waitError; err != nil {
		t.Fatalf("Wait error = %v", err)
	}
	if got := <-waitResult; got != want {
		t.Fatalf("Wait bridge = %#v, want %#v", got, want)
	}
}

func TestClientUsesTheLatestRegistration(t *testing.T) {
	client := NewClient()
	first := fakeBridge{id: 1}
	second := fakeBridge{id: 2}

	client.Register(first)
	client.Register(second)

	if got, ok := client.Current(); !ok || got != second {
		t.Fatalf("Current = (%#v, %t), want (%#v, true)", got, ok, second)
	}
}

func TestZeroClientIsReadyToUse(t *testing.T) {
	var client Client
	want := fakeBridge{id: 1}

	client.Register(want)
	got, err := client.Wait(context.Background())
	if err != nil {
		t.Fatalf("Wait error = %v", err)
	}
	if got != want {
		t.Fatalf("Wait bridge = %#v, want %#v", got, want)
	}
}

func TestClientWaitHonorsContext(t *testing.T) {
	client := NewClient()
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := client.Wait(ctx)
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("Wait error = %v, want context.Canceled", err)
	}
}

func TestIMEClientReplaysLatestShowWhenAndroidRegisters(t *testing.T) {
	client := NewIMEClient()
	client.Show(IMEText, IMEActionDone)
	client.Show(IMENumber, IMEActionDone)

	android := new(fakeIMEBridge)
	client.Register(android)

	shows, hides := android.calls()
	if len(shows) != 1 || shows[0] != (imeRequest{inputType: IMENumber, imeOptions: IMEActionDone}) {
		t.Fatalf("Show calls = %#v, want one number/done request", shows)
	}
	if hides != 0 {
		t.Fatalf("Hide calls = %d, want 0", hides)
	}
}

func TestIMEClientReplaysLatestShowAfterActivityRecreation(t *testing.T) {
	client := NewIMEClient()
	first := new(fakeIMEBridge)
	second := new(fakeIMEBridge)

	client.Register(first)
	client.Show(IMENumber, IMEActionDone)
	client.Register(second)

	firstShows, firstHides := first.calls()
	if len(firstShows) != 1 || firstShows[0] != (imeRequest{inputType: IMENumber, imeOptions: IMEActionDone}) {
		t.Fatalf("first Show calls = %#v, want one number/done request", firstShows)
	}
	if firstHides != 0 {
		t.Fatalf("first Hide calls = %d, want 0", firstHides)
	}
	secondShows, secondHides := second.calls()
	if len(secondShows) != 1 || secondShows[0] != (imeRequest{inputType: IMENumber, imeOptions: IMEActionDone}) {
		t.Fatalf("second Show calls = %#v, want replayed number/done request", secondShows)
	}
	if secondHides != 0 {
		t.Fatalf("second Hide calls = %d, want 0", secondHides)
	}
}

func TestIMEClientHideCancelsPendingShow(t *testing.T) {
	var client IMEClient
	android := new(fakeIMEBridge)

	client.Show(IMEText, IMEActionDone)
	client.Register(android)
	client.Hide()

	recreated := new(fakeIMEBridge)
	client.Register(recreated)

	shows, hides := android.calls()
	if len(shows) != 1 {
		t.Fatalf("Show calls = %#v, want one request", shows)
	}
	if hides != 1 {
		t.Fatalf("Hide calls = %d, want 1", hides)
	}
	recreatedShows, recreatedHides := recreated.calls()
	if len(recreatedShows) != 0 || recreatedHides != 0 {
		t.Fatalf("recreated calls = (%#v, %d), want none", recreatedShows, recreatedHides)
	}
}

func TestIMEClientHideWinsOverQueuedShow(t *testing.T) {
	client := NewIMEClient()
	android := new(fakeIMEBridge)
	client.Register(android)

	// Queue both operations behind applyMu so the test covers the race where a
	// later Hide changes state before an earlier Show reaches Android.
	client.applyMu.Lock()
	showDone := make(chan struct{})
	go func() {
		client.Show(IMEText, IMEActionDone)
		close(showDone)
	}()
	waitForIMEShowing(t, client, true)

	hideDone := make(chan struct{})
	go func() {
		client.Hide()
		close(hideDone)
	}()
	waitForIMEShowing(t, client, false)
	client.applyMu.Unlock()

	select {
	case <-showDone:
	case <-time.After(time.Second):
		t.Fatal("Show did not complete")
	}
	select {
	case <-hideDone:
	case <-time.After(time.Second):
		t.Fatal("Hide did not complete")
	}

	shows, hides := android.calls()
	if len(shows) != 0 || hides != 1 {
		t.Fatalf("calls = (%#v, %d), want no Show and one Hide", shows, hides)
	}
}

func TestIMEClientComposingUsesCurrentBridge(t *testing.T) {
	var client IMEClient
	if got := client.Composing(); got != "" {
		t.Fatalf("Composing before Register = %q, want empty", got)
	}

	first := &fakeIMEBridge{composing: "draft"}
	second := &fakeIMEBridge{composing: "replacement"}
	client.Register(first)
	if got := client.Composing(); got != "draft" {
		t.Fatalf("Composing = %q, want %q", got, "draft")
	}
	client.Register(second)
	if got := client.Composing(); got != "replacement" {
		t.Fatalf("Composing after recreation = %q, want %q", got, "replacement")
	}
}

func waitForIMEShowing(t *testing.T, client *IMEClient, want bool) {
	t.Helper()
	deadline := time.Now().Add(time.Second)
	for {
		client.mu.RLock()
		got := client.showing
		client.mu.RUnlock()
		if got == want {
			return
		}
		if time.Now().After(deadline) {
			t.Fatalf("IMEClient showing = %t, want %t", got, want)
		}
		time.Sleep(time.Millisecond)
	}
}
