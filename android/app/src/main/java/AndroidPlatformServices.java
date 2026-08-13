package @@APP_ID@@;

import android.content.Context;
import android.content.Intent;
import android.os.LocaleList;

import java.util.Locale;

/** Android-backed services exposed through the optional gomobile bridge. */
final class AndroidPlatformServices implements OptionalMobileHooks.PlatformServices {
  private final Context applicationContext;

  AndroidPlatformServices(Context context) {
    Context appContext = context.getApplicationContext();
    applicationContext = appContext != null ? appContext : context;
  }

  @Override
  public String noBackupFilesDir() {
    return applicationContext.getNoBackupFilesDir().getAbsolutePath();
  }

  @Override
  public String localeTags() {
    LocaleList locales = applicationContext
        .getResources()
        .getConfiguration()
        .getLocales();
    String tags = locales.toLanguageTags();
    if (!tags.isEmpty()) {
      return tags;
    }
    return Locale.getDefault().toLanguageTag();
  }

  @Override
  public void restartApp() {
    Intent restartIntent = new Intent(applicationContext, ProcessRestartActivity.class);
    restartIntent.putExtra(
        ProcessRestartActivity.EXTRA_PREVIOUS_PID,
        android.os.Process.myPid());
    restartIntent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_NO_ANIMATION
            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    applicationContext.startActivity(restartIntent);
  }
}
