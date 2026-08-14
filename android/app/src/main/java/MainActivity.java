package @@APP_ID@@;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;

import androidx.activity.OnBackPressedCallback;
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

  private OptionalMobileHooks.BackHandler optionalBackHandler;
  private boolean backKeyInProgress;
  private boolean backKeyConsumed;
  private int imeRequestGeneration;
  private boolean imeShowPending;
  private int pendingImeInputType;
  private int pendingImeOptions;

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

      try {
        String timezone = java.util.TimeZone.getDefault().getID();
        Mobile.class.getMethod("setTimezone", String.class).invoke(null, timezone);
      } catch (NoSuchMethodException e) {
        Log.i(TAG, "onCreate: setTimezone(string) not declared, skipping");
      } catch (Exception e) {
        Log.e(TAG, "onCreate: setTimezone error", e);
      }

      setContentView(R.layout.activity_main);
      Log.i(TAG, "onCreate: setContentView ok");

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        hideSystemBarsApi30();
        Log.i(TAG, "onCreate: hideSystemBarsApi30 ok");
      } else {
        hideSystemBarsLegacy();
        Log.i(TAG, "onCreate: hideSystemBarsLegacy ok");
      }

      // prevent canvas size from changing due to IME or system bar insets
      WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

      Seq.setContext(getApplicationContext());
      Log.i(TAG, "onCreate: Seq.setContext ok");

      registerOptionalPlatformBridge();
      registerOptionalBackHandler();

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

        @Override
        public String composing() {
          return getComposingText();
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
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    Log.i(TAG, "focus change: " + Boolean.toString(hasFocus));
    if (hasFocus && imeShowPending) {
      requestPendingIme();
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getKeyCode() != KeyEvent.KEYCODE_BACK || optionalBackHandler == null) {
      return super.dispatchKeyEvent(event);
    }

    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
      backKeyInProgress = true;
      backKeyConsumed = optionalBackHandler.onBackPressed();
      if (backKeyConsumed) {
        Log.i(TAG, "Back key consumed by Go");
      }
    }

    if (backKeyConsumed) {
      if (event.getAction() == KeyEvent.ACTION_UP) {
        backKeyInProgress = false;
        backKeyConsumed = false;
      }
      return true;
    }

    boolean handled = super.dispatchKeyEvent(event);
    if (event.getAction() == KeyEvent.ACTION_UP) {
      backKeyInProgress = false;
    }
    return handled;
  }

  @Override
  protected void onStart() {
    Log.i(TAG, "onStart");
    super.onStart();
  }

  @Override
  protected void onStop() {
    Log.i(TAG, "onStop");
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    Log.i(TAG, "onDestroy");
    imeRequestGeneration++;
    imeShowPending = false;
    super.onDestroy();
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

  private void registerOptionalPlatformBridge() {
    boolean registered = OptionalMobileHooks.registerPlatformBridge(
        Mobile.class,
        new AndroidPlatformServices(getApplicationContext()));
    if (registered) {
      Log.i(TAG, "onCreate: optional platform bridge registered");
    } else {
      Log.i(TAG, "onCreate: optional platform bridge not declared, skipping");
    }
  }

  private void registerOptionalBackHandler() {
    optionalBackHandler = OptionalMobileHooks.backHandler(Mobile.class);
    if (optionalBackHandler == null) {
      Log.i(TAG, "onCreate: optional Back hook not declared, preserving default behavior");
      return;
    }

    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        // A hardware Back key may reach the dispatcher only after
        // dispatchKeyEvent already asked Go. Reuse that decision so a false
        // hook has no duplicate side effects.
        boolean consumed = backKeyInProgress
            ? backKeyConsumed
            : optionalBackHandler.onBackPressed();
        if (consumed) {
          Log.i(TAG, "Back consumed by Go");
          return;
        }

        // Temporarily disable this callback so the dispatcher reaches the
        // Activity's pre-existing fallback instead of recursing into Go.
        setEnabled(false);
        try {
          getOnBackPressedDispatcher().onBackPressed();
        } finally {
          setEnabled(true);
        }
      }
    });
    Log.i(TAG, "onCreate: optional Back hook registered");
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
    
    view.prepareShowIME(inputType, imeOptions, keyboardCompatibility());
    imeRequestGeneration++;
    imeShowPending = true;
    pendingImeInputType = inputType;
    pendingImeOptions = imeOptions;

    if (!view.requestFocus()) {
      Log.e(TAG, "showIme: view refused focus");
      return;
    }

    requestPendingIme();
  }

  private void requestPendingIme() {
    if (!imeShowPending) {
      return;
    }

    EbitenExtendedView view = getEbitenView();
    if (view == null) {
      Log.e(TAG, "showIme: view is null while request is pending");
      return;
    }

    final int generation = imeRequestGeneration;
    view.post(() -> showImeWhenReady(view, generation));
  }

  private void showImeWhenReady(EbitenExtendedView view, int generation) {
    if (!imeShowPending || generation != imeRequestGeneration) {
      return;
    }

    if (!view.hasWindowFocus() || !view.isFocused()) {
      // A request made while the Activity is starting is valid. Keep it
      // pending until Android connects this window instead of issuing a
      // showSoftInput call that the framework will reject as "not served".
      Log.i(TAG, "showIme: waiting for focused window");
      return;
    }

    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm == null) {
      Log.e(TAG, "showIme: InputMethodManager is null");
      return;
    }

    imeShowPending = false;
    // Reapply the latest values in case an older InputConnection was created
    // while the request was waiting for focus.
    view.prepareShowIME(pendingImeInputType, pendingImeOptions, keyboardCompatibility());
    imm.restartInput(view);

    WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(
        getWindow(), view);
    if (insetsController == null) {
      view.post(() -> {
        if (generation == imeRequestGeneration) {
          imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
      });
      Log.i(TAG, "showIme: requested through posted fallback");
      return;
    }
    insetsController.show(WindowInsetsCompat.Type.ime());
    Log.i(TAG, "showIme: requested through window insets");
  }

  // returns "default", "samsung", "raw"
  private String keyboardCompatibility() {
    String currentImeId = android.provider.Settings.Secure.getString(
        getContentResolver(), 
        android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
    );
    if (currentImeId == null) {
      return "raw";
    }
    // Log.i(TAG, "IME ID: " + currentImeId);
    // Example values:
    //   com.samsung.android.honeyboard/.service.HoneyBoardService
    //   com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME
    if (currentImeId.startsWith("com.samsung.android")) {
      return "samsung";
    }
    return "default";
  }

  private void hideIme() {
    EbitenExtendedView view = getEbitenView();
    if (view == null) {
      Log.e(TAG, "hideIme: failed to find EbitenExtendedView");
      return;
    }
    
    imeRequestGeneration++;
    imeShowPending = false;
    view.prepareHideIME();
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
      Log.i(TAG, "hideIme: requested");
    } else {
      Log.e(TAG, "hideIme: InputMethodManager is null");
    }
  }

  private String getComposingText() {
    EbitenExtendedView view = getEbitenView();
    if (view == null) {
      Log.e(TAG, "getComposingText: failed to find EbitenExtendedView");
      return "";
    }

    return view.getComposingText();
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
