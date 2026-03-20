package @@APP_ID@@;

import android.view.View;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

// EbitenInputConnection extends BaseInputConnection in order to intercept
// and dispatch some events that are not normally passed as key events. One
// good example is the ".com" special key shown on email keyboards.
public class EbitenInputConnection extends android.view.inputmethod.BaseInputConnection {
    private static final String TAG = "@@LOG_TAG@@";

    private View targetView;
    private final android.view.KeyCharacterMap kcm;
    private boolean muteNextCommit; // we have been asked something not supported by Ebitengine internals

    public EbitenInputConnection(View targetView, boolean fullEditor) {
        super(targetView, fullEditor);
        this.targetView = targetView;
        this.kcm = android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD);
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (text == null || text.length() == 0) {
            return super.commitText(text, newCursorPosition);
        }

        char[] letters = text.toString().toCharArray();
        if (this.muteNextCommit && letters.length > 0) {
            this.muteNextCommit = false;
            if (letters[letters.length-1] == ' ') {
                letters = new char[]{' '};
            } else {
                letters = new char[0];
            }
        }

        // convert the string into individual KeyEvents that Ebitengine can catch
        android.view.KeyEvent[] events = kcm.getEvents(letters);
        if (events == null) {
            return super.commitText(text, newCursorPosition);
        }

        for (android.view.KeyEvent event : events) {
            targetView.dispatchKeyEvent(event);
        }
        return super.commitText(text, newCursorPosition);
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        this.muteNextCommit = true;
        return super.setComposingRegion(start, end);
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        // NOTICE: ideally we should have a bridge to get "ComposingText"
        // and show it underlined while it's being built.
        this.muteNextCommit = false; // bypass muting for composing editors
        return super.setComposingText(text, newCursorPosition);
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (beforeLength + afterLength > 1) {
            this.muteNextCommit = true;
            return super.deleteSurroundingText(beforeLength, afterLength);
        }
        
        for (int i = 0; i < beforeLength; i++) {
            sendHardwareKey(android.view.KeyEvent.KEYCODE_DEL);
        }
        for (int i = 0; i < afterLength; i++) {
            sendHardwareKey(android.view.KeyEvent.KEYCODE_FORWARD_DEL);
        }
        return super.deleteSurroundingText(beforeLength, afterLength);
    }

    private void sendHardwareKey(int keyCode) {
        targetView.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode));
        targetView.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode));
    }
}