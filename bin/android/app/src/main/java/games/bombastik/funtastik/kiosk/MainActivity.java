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
import @@JAVA_PKG@@.mobile.EbitenView;
import @@JAVA_PKG@@.mobile.Mobile;
import @@JAVA_PKG@@.mobile.IMEBridge;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = "Ebiten/Android";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    System.out.println("MainActivity: DEBUG: onCreate started.");

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        hideSystemBarsApi30();
    } else {
        hideSystemBarsLegacy();
    }

    Seq.setContext(getApplicationContext());
    System.out.println("MainActivity: DEBUG: Seq.setContext() called.");

    EbitenView v = this.getEbitenView();
    if (v != null) {
      v.setFocusable(true);
      v.setFocusableInTouchMode(true);
      v.requestFocus();
    }

    Mobile.registerIMEBridge(new IMEBridge() {
      @Override
      public void show() {
        runOnUiThread(() -> showIme(v));
      }

      @Override
      public void hide() {
        runOnUiThread(() -> hideIme(v));
      }
    });

    System.out.println("MainActivity: DEBUG: MainActivity onCreate finished.");
  }

  @Override
  protected void onPause() {
    super.onPause();
    Log.v("MainActivity", "EbitenView.suspendGame() is about to be called.....");
    this.getEbitenView().suspendGame();
    Log.v("MainActivity", "EbitenView.suspendGame() has been called.....");
  }

  @Override
  protected void onResume() {
    super.onResume();
    Log.v("MainActivity", "EbitenView.resumeGame() is about to be called.....");
    this.getEbitenView().resumeGame();
    Log.v("MainActivity", "EbitenView.resumeGame() has been called!");
  }

  private EbitenView getEbitenView() {
      return (EbitenView)this.findViewById(R.id.ebitenview);
  }

  private int hideSystemBars() {
    int uiOptions =
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    | View.SYSTEM_UI_FLAG_FULLSCREEN
    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    return uiOptions;
  }

  //----------------------------------------------------------------------------------------------
  // IME: minimal helpers
  //----------------------------------------------------------------------------------------------
  private void showIme(View view) {
    if (view == null) return;
    view.requestFocus();
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }
  }

  private void hideIme(View view) {
    if (view == null) return;
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }

  //----------------------------------------------------------------------------------------------
  // Methods for API 30+ (Android 11 and above)
  //----------------------------------------------------------------------------------------------
  private void hideSystemBarsApi30() {
    WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    if (insetsController == null) {
      return;
    }
    insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    insetsController.hide(WindowInsetsCompat.Type.systemBars());
  }

  //----------------------------------------------------------------------------------------------
  // Methods for legacy APIs (below 30)
  //----------------------------------------------------------------------------------------------
  @SuppressWarnings("deprecation")
  private void hideSystemBarsLegacy() {
    View decorView = getWindow().getDecorView();
    decorView.setSystemUiVisibility(hideSystemBars());

    decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
      @Override
      public void onSystemUiVisibilityChange(int visibility) {
        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
          decorView.setSystemUiVisibility(hideSystemBars());
        }
      }
    });
  }
}