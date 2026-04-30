package @@APP_ID@@;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import go.Seq;
import @@JAVA_PKG@@.@@GO_PKG@@.EbitenView;
import @@JAVA_PKG@@.@@GO_PKG@@.Mobile;
import @@JAVA_PKG@@.@@GO_PKG@@.IMEBridge;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = "@@LOG_TAG@@";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "onCreate: enter");
    super.onCreate(savedInstanceState);

    try {
      String androidId = android.provider.Settings.Secure.getString(
          getContentResolver(),
          android.provider.Settings.Secure.ANDROID_ID
      );

      long id = Long.parseUnsignedLong(androidId, 16) & 0x7FFFFFFFFFFFFFFFL;
      Mobile.setAndroidID(id);
      Log.i(TAG, "onCreate: androidID = " + androidId + " -> " + id);

      setContentView(R.layout.activity_main);
      Log.i(TAG, "onCreate: setContentView ok");

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        hideSystemBarsApi30();
        Log.i(TAG, "onCreate: hideSystemBarsApi30 ok");
      } else {
        hideSystemBarsLegacy();
        Log.i(TAG, "onCreate: hideSystemBarsLegacy ok");
      }

      Seq.setContext(getApplicationContext());
      Log.i(TAG, "onCreate: Seq.setContext ok");

      EbitenExtendedView v = getEbitenView();
      Log.i(TAG, "onCreate: ebiten view = " + v);

      if (v != null) {
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        v.requestFocus();
        Log.i(TAG, "onCreate: ebiten view focused");
      } else {
        Log.e(TAG, "onCreate: ebiten view is null");
      }

      Mobile.registerIMEBridge(new IMEBridge() {
        @Override
        public void show(int inputType, int imeOptions) {
          Log.i(TAG, "IMEBridge.show(0x" + Integer.toHexString(inputType) + ", 0x" + Integer.toHexString(imeOptions) + ")");
          runOnUiThread(() -> showIme(inputType, imeOptions));
        }

        @Override
        public void hide() {
          Log.i(TAG, "IMEBridge.hide()");
          runOnUiThread(() -> hideIme());
        }
      });

      Log.i(TAG, "onCreate: IME bridge registered");
      Log.i(TAG, "onCreate: finished");
    } catch (Throwable t) {
      Log.e(TAG, "onCreate: fatal error", t);
      throw t;
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    
    EbitenExtendedView view = getEbitenView();
    if (view != null) {
      view.suspendGame();
      Log.i(TAG, "onPause: suspendGame ok");
    } else {
      Log.e(TAG, "onPause: ebiten view is null");
    }
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    EbitenExtendedView view = getEbitenView();

    if (view != null) {
      view.resumeGame();
      Log.i(TAG, "onResume: resumeGame ok");
    } else {
      Log.e(TAG, "onResume: ebiten view is null");
    }
  }

  private EbitenExtendedView getEbitenView() {
    return (EbitenExtendedView) this.findViewById(R.id.ebitenview);
  }

  private int hideSystemBars() {
    return View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        | View.SYSTEM_UI_FLAG_FULLSCREEN
        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
  }

  private void showIme(int inputType, int imeOptions) {
    EbitenExtendedView view = getEbitenView();
    if (view == null) {
      Log.e(TAG, "showIme: view is null");
      return;
    }
    
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm == null) {
      Log.e(TAG, "showIme: InputMethodManager is null");
      return;
    }

    if (!isFriendlyKeyboard(imm)) {
      // clear capitalization, autocomplete, multiline... flags
      inputType = inputType & ~0xF000;

      // get base class
      final int TEXT_CLASS = android.text.InputType.TYPE_CLASS_TEXT;
      int baseClass = inputType & android.text.InputType.TYPE_MASK_CLASS;
    
      final int PASSWORD = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
      if (baseClass == TEXT_CLASS && (inputType & PASSWORD) != PASSWORD) {
        inputType = 0;
      }
    }

    boolean refresh = (view.currentInputType != inputType || view.currentImeOptions != imeOptions);
    view.currentInputType = inputType;
    view.currentImeOptions = imeOptions;
    view.requestFocus();
    if (refresh) {
      imm.restartInput(view);
    }
    imm.showSoftInput(view, 0);
    Log.i(TAG, "showIme: requested");
  }

  // precondition: imm is not null
  private boolean isFriendlyKeyboard(InputMethodManager imm) {
    String currentImeId = android.provider.Settings.Secure.getString(
        getContentResolver(), 
        android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
    );
    if (currentImeId == null) {
      return false;
    }
    // Log.i(TAG, "IME ID: " + currentImeId);
    // Example values:
    //   com.samsung.android.honeyboard/.service.HoneyBoardService
    //   com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME
    if (currentImeId.startsWith("com.samsung.android")) {
      return false;
    }
    return true;
  }

  private void hideIme() {
    EbitenExtendedView view = getEbitenView();
    if (view == null) {
      Log.e(TAG, "hideIme: failed to find EbitenExtendedView");
      return;
    }
    view.currentInputType = -1;
    view.currentImeOptions = -1;
    
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
      Log.i(TAG, "hideIme: requested");
    } else {
      Log.e(TAG, "hideIme: InputMethodManager is null");
    }
  }

  private void hideSystemBarsApi30() {
    WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(
        getWindow(),
        getWindow().getDecorView());
    if (insetsController == null) {
      Log.e(TAG, "hideSystemBarsApi30: controller is null");
      return;
    }
    insetsController.setSystemBarsBehavior(
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    insetsController.hide(WindowInsetsCompat.Type.systemBars());
  }

  @SuppressWarnings("deprecation")
  private void hideSystemBarsLegacy() {
    View decorView = getWindow().getDecorView();
    decorView.setSystemUiVisibility(hideSystemBars());

    decorView.setOnSystemUiVisibilityChangeListener(
        new View.OnSystemUiVisibilityChangeListener() {
          @Override
          public void onSystemUiVisibilityChange(int visibility) {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
              decorView.setSystemUiVisibility(hideSystemBars());
            }
          }
        });
  }
}