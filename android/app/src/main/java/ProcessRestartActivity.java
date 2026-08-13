package @@APP_ID@@;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * Restarts the application from a short-lived, isolated Android process.
 *
 * <p>The old process is gone before the launcher Activity is requested, so a
 * native listener cannot overlap with its successor. No alarm permission,
 * service or device restart is required.</p>
 */
public final class ProcessRestartActivity extends AppCompatActivity {
  static final String EXTRA_PREVIOUS_PID = "previous_pid";

  private static final String TAG = "@@LOG_TAG@@";
  private static final long POLL_DELAY_MILLIS = 50L;
  private static final int MAX_POLLS = 40;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private int previousPid;
  private int polls;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    previousPid = getIntent().getIntExtra(EXTRA_PREVIOUS_PID, -1);
    if (previousPid <= 0 || previousPid == Process.myPid()) {
      fail("invalid previous process id " + previousPid);
      return;
    }

    Log.i(TAG, "restart: terminating previous process " + previousPid);
    Process.killProcess(previousPid);
    awaitPreviousProcessExit();
  }

  private void awaitPreviousProcessExit() {
    if (!isProcessRunning(previousPid)) {
      launchSuccessor();
      return;
    }

    polls++;
    if (polls >= MAX_POLLS) {
      fail("previous process did not exit: " + previousPid);
      return;
    }

    Process.killProcess(previousPid);
    handler.postDelayed(this::awaitPreviousProcessExit, POLL_DELAY_MILLIS);
  }

  private boolean isProcessRunning(int pid) {
    ActivityManager manager =
        (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    if (manager == null) {
      return false;
    }
    List<ActivityManager.RunningAppProcessInfo> processes =
        manager.getRunningAppProcesses();
    if (processes == null) {
      return false;
    }
    for (ActivityManager.RunningAppProcessInfo process : processes) {
      if (process.pid == pid) {
        return true;
      }
    }
    return false;
  }

  private void launchSuccessor() {
    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
    if (launchIntent == null) {
      fail("application has no launch intent");
      return;
    }
    ComponentName component = launchIntent.getComponent();
    if (component == null) {
      fail("application launch intent has no component");
      return;
    }

    Log.i(TAG, "restart: launching successor after process " + previousPid);
    startActivity(Intent.makeRestartActivityTask(component));
    finishAndRemoveTask();
    handler.postDelayed(
        () -> Process.killProcess(Process.myPid()),
        POLL_DELAY_MILLIS);
  }

  private void fail(String message) {
    Log.e(TAG, "restart: " + message);
    finishAndRemoveTask();
  }
}
