package @@APP_ID@@;

import android.util.Log;
import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import @@JAVA_PKG@@.@@GO_PKG@@.EbitenView;
import @@JAVA_PKG@@.ebitenmobileview.Ebitenmobileview;

class EbitenExtendedView extends EbitenView {
  private static final String TAG = "@@LOG_TAG@@";

  private int currentInputType = -1; // set to -1 to force initial refresh
  private int currentImeOptions = -1;
  private String currentImeCompat = "default";
  private EbitenInputConnection inputConnection;

  public EbitenExtendedView(Context context) {
    super(context);
  }

  public EbitenExtendedView(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
  }

  @Override
  protected void onErrorOnGameUpdate(Exception e) {
    Log.e(TAG, "onErrorOnGameUpdate", e);
    super.onErrorOnGameUpdate(e);
  }

  @Override
  public android.view.inputmethod.InputConnection onCreateInputConnection(android.view.inputmethod.EditorInfo outAttrs) {
    final int TEXT_CLASS = android.text.InputType.TYPE_CLASS_TEXT;
    final int PASSWORD = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;

    outAttrs.inputType = this.currentInputType;
    outAttrs.imeOptions = this.currentImeOptions;

    switch (this.currentImeCompat) {
    case "samsung":
      break;
    case "raw":
      // clear capitalization, autocomplete, multiline... flags
      outAttrs.inputType = outAttrs.inputType & ~0xF000;

      // simplify to base class (text, numeric, password, etc)
      int baseClass = outAttrs.inputType & android.text.InputType.TYPE_MASK_CLASS;
      if (baseClass == TEXT_CLASS && (outAttrs.inputType & PASSWORD) != PASSWORD) {
        outAttrs.inputType = 0;
      }
      break;
    }

    Log.i(TAG, "new input connection");
    this.inputConnection = new EbitenInputConnection(this);
    return this.inputConnection;
  }

  public String getComposingText() {
    if (this.inputConnection == null) {
        return "";
    }
    return this.inputConnection.getComposingText();
  }

  public void prepareShowIME(int inputType, int opts, String compatMode) {
    if (this.inputConnection != null) {
      // this is required to ensure that flushing composing text is a manual
      // operation and not a side effect of calling IME show/hide methods in
      // a specific order
      this.inputConnection.silence();
    }

    this.currentInputType = inputType;
    this.currentImeOptions = opts;
    this.currentImeCompat = compatMode;
  }

  public void prepareHideIME() {
    this.currentInputType = -1;
    this.currentImeOptions = -1;
    this.inputConnection = null;
  }

  @Override
  public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
    String chars = event.getCharacters();
    if (chars == null || chars.isEmpty() || repeatCount > 0) {
      return super.onKeyMultiple(keyCode, repeatCount, event);
    }

    int i = 0;
    while (i < chars.length()) {
      int codePoint = chars.codePointAt(i);
      Ebitenmobileview.onKeyDownOnAndroid(KeyEvent.KEYCODE_UNKNOWN, codePoint, event.getSource(), event.getDeviceId());
      Ebitenmobileview.onKeyUpOnAndroid(KeyEvent.KEYCODE_UNKNOWN, event.getSource(), event.getDeviceId());
      i += Character.charCount(codePoint);
    }

    return true;
  }
}