package @@APP_ID@@;

import android.util.Log;
import android.content.Context;
import android.util.AttributeSet;
import @@JAVA_PKG@@.@@GO_PKG@@.EbitenView;

class EbitenExtendedView extends EbitenView {
  private static final String TAG = "@@LOG_TAG@@";

  public int currentInputType = -1; // set to -1 to force initial refresh
  public int currentImeOptions = -1;

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
    outAttrs.inputType = this.currentInputType;
    outAttrs.imeOptions = this.currentImeOptions;
    return new EbitenInputConnection(this, true);
  }
}