package @@APP_ID@@;

import android.util.Log;
import android.content.Context;
import android.util.AttributeSet;
import @@JAVA_PKG@@.@@GO_PKG@@.EbitenView;

class EbitenExtendedView extends EbitenView {
  private static final String TAG = "@@LOG_TAG@@";

  public int currentInputType = android.text.InputType.TYPE_CLASS_TEXT;
  public int currentImeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE;

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
    android.view.inputmethod.InputConnection ic = super.onCreateInputConnection(outAttrs);
    if (outAttrs != null) {
        Log.i(TAG, "--- Original EditorInfo ---");
        Log.i(TAG, "inputType: 0x" + Integer.toHexString(outAttrs.inputType));
        Log.i(TAG, "imeOptions: 0x" + Integer.toHexString(outAttrs.imeOptions));
        Log.i(TAG, "initialSelStart: " + outAttrs.initialSelStart);
        Log.i(TAG, "packageName: " + outAttrs.packageName);
    }

    outAttrs.inputType = this.currentInputType;
    outAttrs.imeOptions = this.currentImeOptions;
    outAttrs.imeOptions |= android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI;
    
    return ic;
  }
}