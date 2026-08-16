package @@APP_ID@@;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

/** Replaces only the application process after Binder confirms its death. */
public final class ProcessRestartActivity extends Activity {
  static final String EXTRA_PREVIOUS_PID = "previous_pid";
  static final String EXTRA_DEATH_TOKEN = "death_token";

  private static final String TAG = "@@LOG_TAG@@";
  private static final long DEATH_TIMEOUT_MILLIS = 5000L;
  private static final long HELPER_EXIT_DELAY_MILLIS = 100L;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final IBinder.DeathRecipient deathRecipient =
      () -> handler.post(this::onPreviousProcessDied);

  private ComponentName launchComponent;
  private IBinder deathToken;
  private int previousPid;
  private boolean linked;
  private boolean completed;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle extras = getIntent().getExtras();
    previousPid = getIntent().getIntExtra(EXTRA_PREVIOUS_PID, -1);
    deathToken = extras != null ? extras.getBinder(EXTRA_DEATH_TOKEN) : null;
    if (previousPid <= 0 || previousPid == Process.myPid()) {
      failClosed("invalid previous process id " + previousPid);
      return;
    }
    if (deathToken == null) {
      failClosed("missing process death token");
      return;
    }

    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
    if (launchIntent == null || launchIntent.getComponent() == null) {
      failClosed("application has no launcher component");
      return;
    }
    launchComponent = launchIntent.getComponent();
    if (!getPackageName().equals(launchComponent.getPackageName())) {
      failClosed("launcher component belongs to another package");
      return;
    }

    try {
      deathToken.linkToDeath(deathRecipient, 0);
      linked = true;
    } catch (RemoteException alreadyDead) {
      Log.i(TAG, "restart: source Binder was already dead");
      onPreviousProcessDied();
      return;
    }

    if (!deathToken.isBinderAlive()) {
      onPreviousProcessDied();
      return;
    }

    handler.postDelayed(
        () -> failClosed("source process death was not confirmed"),
        DEATH_TIMEOUT_MILLIS);
    Log.i(TAG, "restart: death observer linked; terminating process " + previousPid);
    Process.killProcess(previousPid);
  }

  private void onPreviousProcessDied() {
    if (completed) {
      return;
    }
    completed = true;
    linked = false;
    handler.removeCallbacksAndMessages(null);

    try {
      Log.i(TAG, "restart: death confirmed for process " + previousPid + "; launching successor");
      startActivity(Intent.makeRestartActivityTask(launchComponent));
    } catch (RuntimeException e) {
      Log.e(TAG, "restart: failed to launch successor", e);
    }
    finishHelperProcess();
  }

  private void failClosed(String message) {
    if (completed) {
      return;
    }
    completed = true;
    handler.removeCallbacksAndMessages(null);
    unlinkDeathRecipient();
    Log.e(TAG, "restart: " + message + "; successor will not be launched");
    finishHelperProcess();
  }

  private void finishHelperProcess() {
    finishAndRemoveTask();
    handler.postDelayed(
        () -> Process.killProcess(Process.myPid()),
        HELPER_EXIT_DELAY_MILLIS);
  }

  private void unlinkDeathRecipient() {
    if (linked && deathToken != null) {
      deathToken.unlinkToDeath(deathRecipient, 0);
      linked = false;
    }
  }

  @Override
  protected void onDestroy() {
    if (!completed) {
      handler.removeCallbacksAndMessages(null);
      unlinkDeathRecipient();
    }
    super.onDestroy();
  }
}
