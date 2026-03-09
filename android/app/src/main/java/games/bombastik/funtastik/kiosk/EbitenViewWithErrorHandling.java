package @@APP_ID@@;

import android.util.Log;
import android.content.Context;
import android.util.AttributeSet;
import @@JAVA_PKG@@.@@GO_PKG@@.EbitenView;

class EbitenViewWithErrorHandling extends EbitenView {
  private static final String TAG = "@@LOG_TAG@@";

  public EbitenViewWithErrorHandling(Context context) {
    super(context);
  }

  public EbitenViewWithErrorHandling(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
  }

  @Override
  protected void onErrorOnGameUpdate(Exception e) {
    Log.e(TAG, "onErrorOnGameUpdate", e);
    super.onErrorOnGameUpdate(e);
  }
}