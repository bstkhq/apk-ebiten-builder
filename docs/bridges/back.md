# Android Back bridge

The Back bridge lets Go decide whether one Android Back event has been handled.
It is independent from the runtime bridge: an application may use either,
both, or neither.

The complete local gomobile adapter and Go handler setup are in the
[README](../../README.md#android-back-bridge). It uses `bridge.BackClient` so
the application stores its handler once while Android may recreate the
Activity.

## Dispatch contract

Android invokes `OnBack` on the UI thread.

- Return `true` when Go consumed the event. Android stays in the current
  Activity.
- Return `false` when Go did not consume it. Android continues with its normal
  Back behavior.
- Clear the handler with `BackClient.SetHandler(nil)` to always use the normal
  Android behavior.

Keep the handler short. It should change application state or schedule work,
not perform blocking I/O on Android's UI thread.

### Example: close an in-game panel before leaving

Imagine a game with an overlay settings panel:

1. The panel is open and the Back handler closes it, then returns `true`.
2. The next Back press sees no panel to close and returns `false`.
3. Android receives that `false` result and performs the Activity's usual Back
   action.

This gives one physical Back press one semantic Go decision. The Android
template also avoids delivering the same hardware key to Go twice when it
reaches AndroidX's dispatcher.

## Activity recreation

Each new Activity calls the local `RegisterBackBridge` export. The adapter
installs the same `BackClient` into that new bridge, so the application handler
survives the replacement without copying or re-declaring the contract.

The bridge is optional. If the complete local export is absent, Android retains
its normal Back behavior. An export with a partial or incompatible signature is
reported as a startup contract error rather than silently behaving differently.

## Predictive Back

`ENABLE_ON_BACK_INVOKED_CALLBACK=true` opts the generated manifest into the
Android 13+ predictive-Back dispatcher. Leave it empty to preserve the
existing manifest behavior; only empty, `true` and `false` are accepted.

The [Back fixture](../../tests/fixtures/back/mobile.go) demonstrates a handler
that consumes its first event and delegates the second.
