package @@APP_ID@@;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import android.content.Context;
import android.view.inputmethod.InputMethodManager;

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

      EbitenView v = getEbitenView();
      Log.i(TAG, "onCreate: ebiten view = " + v);

      if (v != null) {
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        v.requestFocus();
        Log.i(TAG, "onCreate: ebiten view focused");
      } else {
        Log.e(TAG, "onCreate: ebiten view is null");
      }

      EbitenExtendedView exview = getEbitenExtendedView();
      Mobile.registerIMEBridge(new IMEBridge() {
        @Override
        public void show(int opts) {
          Log.i(TAG, "IMEBridge.show(0x" + Integer.toHexString(opts) + ")");
          runOnUiThread(() -> showIme(exview, opts));
        }

        @Override
        public void hide() {
          Log.i(TAG, "IMEBridge.hide()");
          runOnUiThread(() -> hideIme(v));
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
    Log.i(TAG, "onPause: enter");
    super.onPause();
    EbitenView view = getEbitenView();
    if (view != null) {
      view.suspendGame();
      Log.i(TAG, "onPause: suspendGame ok");
    } else {
      Log.e(TAG, "onPause: ebiten view is null");
    }
  }

  @Override
  protected void onResume() {
    Log.i(TAG, "onResume: enter");
    super.onResume();
    EbitenView view = getEbitenView();

    if (view != null) {
      view.resumeGame();
      Log.i(TAG, "onResume: resumeGame ok");
    } else {
      Log.e(TAG, "onResume: ebiten view is null");
    }
  }

  private EbitenView getEbitenView() {
    return (EbitenView) this.findViewById(R.id.ebitenview);
  }

  private EbitenExtendedView getEbitenExtendedView() {
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

  private void showIme(EbitenExtendedView view, int goImeOpts) {
    if (view == null) {
      Log.e(TAG, "showIme: view is null");
      return;
    }

    int type = applyCapitalization(extractInputType(goImeOpts), goImeOpts);
    int options = extractImeOptions(goImeOpts);
    boolean refresh = (view.currentInputType != type || view.currentImeOptions != options);
    view.currentInputType = type;
    view.currentImeOptions = options;
    Log.i(TAG, "DEBUG currentInputType: 0x" + Integer.toHexString(type) + " / currentImeOptions: 0x"+ Integer.toHexString(options));

    view.requestFocus();
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      if (refresh) {
        imm.restartInput(view);
      }
      imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
      Log.i(TAG, "showIme: requested");
    } else {
      Log.e(TAG, "showIme: InputMethodManager is null");
    }
  }

  private int applyCapitalization(int inputType, int goImeOpts) {
    if ((inputType & android.text.InputType.TYPE_MASK_CLASS) != android.text.InputType.TYPE_CLASS_TEXT) {
      return inputType;
    }

    // Only apply if the base class is TEXT
    switch (goImeOpts & 0x00F) {
      case 0x001: return inputType | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
      case 0x002: return inputType | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS;
      case 0x003: return inputType | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
      default: return inputType;
    }
  }

  private int extractInputType(int opts) {
    switch (opts & 0xF00) {
      case 0x100: return android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
      case 0x200: return android.text.InputType.TYPE_CLASS_NUMBER;
      case 0x300: return android.text.InputType.TYPE_CLASS_PHONE;
      case 0x400: return android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
      case 0x500: return android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI;
      case 0x600: return android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
      default:    return android.text.InputType.TYPE_CLASS_TEXT;
    }
  }

  private int extractImeOptions(int opts) {
    switch (opts & 0x0F0) {
      case 0x010: return android.view.inputmethod.EditorInfo.IME_ACTION_GO;
      case 0x020: return android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH;
      case 0x030: return android.view.inputmethod.EditorInfo.IME_ACTION_SEND;
      case 0x040: return android.view.inputmethod.EditorInfo.IME_ACTION_NEXT;
      case 0x050: return android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
      default:    return android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED;
    }
  }

  private void hideIme(View view) {
    if (view == null) {
      Log.e(TAG, "hideIme: view is null");
      return;
    }
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