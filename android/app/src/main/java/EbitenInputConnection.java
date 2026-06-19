package @@APP_ID@@;

import android.view.View;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import android.util.Log;

// EbitenInputConnection extends BaseInputConnection in order to intercept
// and dispatch some events that are not normally passed as key events. One
// good example is the ".com" special key shown on email keyboards.
public class EbitenInputConnection extends android.view.inputmethod.BaseInputConnection {
    private static final String TAG = "@@LOG_TAG@@";

    private View targetView;
    private final android.view.KeyCharacterMap kcm;
    private String composing = "";
    private int composingCommitted;
    private boolean silenced;

    public EbitenInputConnection(View targetView) {
        super(targetView, false);
        this.targetView = targetView;
        this.kcm = android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD);
    }

    public String getComposingText() {
        return this.composing.substring(Math.min(this.composingCommitted, this.composing.length()));
    }

    // silence makes the connection stop sending key events
    public void silence() {
        this.silenced = true;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        this.composing = "";
        this.composingCommitted = 0;
        return super.commitText(text, newCursorPosition);
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        this.composing = text.toString();
        return super.setComposingText(text, newCursorPosition);
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        this.composingCommitted = Math.max(0, end-start);
        return false; // don't allow super to continue with more complex composing regions
    }

    @Override
    public boolean replaceText(int start, int end, CharSequence text, int newCursorPosition, android.view.inputmethod.TextAttribute textAttribute) {
        int cut = Math.max(end - start, 0);
        if (cut > 0) {
            sendHardwareKey(android.view.KeyEvent.KEYCODE_DEL);
            cut -= 1;
        }
        if (cut > 0) {
            text = text.subSequence(Math.min(cut, text.length()), text.length());
        }
        dispatchTextEvents(text);
        
        return false;
    }

    @Override
    public void closeConnection() {
        this.composing = "";
        this.composingCommitted = 0;
        super.closeConnection();
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (this.silenced) {
            return true;
        }

        // samsung reports left/right arrows as undefined source, so they are not
        // caught by default. The events can be made detectable by updating the
        // source, but... samsung only fires left/right a few times anyway due to
        // internal state, so this is left disabled to prevent even more inconsistent
        // behavior. If that is improved, the following code can be restored.
        if (event.getSource() == android.view.InputDevice.SOURCE_UNKNOWN && event.getDeviceId() != KeyCharacterMap.VIRTUAL_KEYBOARD) {
            // returning false prevents "ghost events" that we can't react to from
            // going through and having minor side effects
            return false; // event.setSource(android.view.InputDevice.SOURCE_KEYBOARD);
        }

        // edge case: when composingCommitted applies and the composing text is
        // passed as an ACTION_MULTIPLE event, the contents can be incorrect. This
        // can happen for example when typing some@address and then pressing the
        // .com button, or pressing www. and then continuing writing, which trigger
        // multiple ACTION_MULTIPLE and with a modified composing region. This has
        // to be specially taken into account during finishComposing.
        if (this.composingCommitted > 0 && event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            if (this.composing.equals(event.getCharacters())) {
                event = new KeyEvent(event.getDownTime(), getComposingText(), KeyCharacterMap.VIRTUAL_KEYBOARD, 0);
            }
        }

        // when pressing left/right, commit composing text first
        if (this.composing != null && this.composing.length() > 0 && event.getAction() == KeyEvent.ACTION_DOWN) {
            int kc = event.getKeyCode();
            if (kc == KeyEvent.KEYCODE_DPAD_LEFT || kc == KeyEvent.KEYCODE_DPAD_RIGHT) {
                finishComposingText();
            }
        }

        // Log.i(TAG, "send key event " + event.toString());
        return super.sendKeyEvent(event);
    }

    @Override
    public boolean finishComposingText () {
        // allow sendKeyEvents to be called before clearing composing
        boolean ok = super.finishComposingText(); 
        this.composing = "";
        this.composingCommitted = 0;
        return ok;
    }

    private void dispatchTextEvents(CharSequence text) {
        if (text == null || text.length() == 0) {
            return;
        }

        // convert the string into individual KeyEvents that Ebitengine can catch
        KeyEvent[] events = getKeyEvents(this.kcm, text.toString());
        if (events == null) {
            return;
        }

        for (KeyEvent event : events) {
            sendKeyEvent(event);
        }
    }

    private KeyEvent[] getKeyEvents(KeyCharacterMap kcm, String s) {
        KeyEvent[] events = this.kcm.getEvents(s.toCharArray());
        if (events != null) {
            return events;
        }

        KeyEvent event = new KeyEvent(android.os.SystemClock.uptimeMillis(), s, KeyCharacterMap.VIRTUAL_KEYBOARD, 0);
        return new KeyEvent[] {event};
    }

    private void sendHardwareKey(int keyCode) {
        targetView.dispatchKeyEvent(new KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode));
        targetView.dispatchKeyEvent(new KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode));
    }
}
