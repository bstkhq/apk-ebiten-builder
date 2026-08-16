package bridge

import (
	"context"
	"errors"
	"testing"
	"time"
)

// fakeBridge embeds the contract because these tests exercise Client's
// lifecycle rather than any Android service call.
type fakeBridge struct {
	AndroidBridge
	id int
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
