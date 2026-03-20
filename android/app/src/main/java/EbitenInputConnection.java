package @@APP_ID@@;

import android.view.View;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

// EbitenInputConnection extends BaseInputConnection in order to intercept
// and dispatch some events that are not normally passed as key events. One
// good example is the ".com" special key shown on email keyboards.
public class EbitenInputConnection extends android.view.inputmethod.BaseInputConnection {
    private View targetView;
    private final android.view.KeyCharacterMap kcm;

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

        // convert the string into individual KeyEvents that Ebitengine can catch
        android.view.KeyEvent[] events = kcm.getEvents(text.toString().toCharArray());
        if (events == null) {
            return super.commitText(text, newCursorPosition);
        }


        for (android.view.KeyEvent event : events) {
            targetView.dispatchKeyEvent(event);
        }
        return true;
    }
}