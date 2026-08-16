package @@APP_ID@@;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.PowerManager;
import android.provider.Settings;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/** Android runtime information exposed to Go through AndroidPlatform. */
final class AndroidPlatformServices implements OptionalAndroidPlatform.Services {
  private static final Binder PROCESS_LIFETIME = new Binder();
  private static final AtomicBoolean RESTART_REQUESTED = new AtomicBoolean();

  private final Context applicationContext;

  AndroidPlatformServices(Context context) {
    if (context == null) {
      throw new NullPointerException("context");
    }
    Context appContext = context.getApplicationContext();
    applicationContext = appContext != null ? appContext : context;
  }

  @Override
  public String androidID() {
    String value = Settings.Secure.getString(
        applicationContext.getContentResolver(),
        Settings.Secure.ANDROID_ID);
    value = requireNonEmpty(value, "Android ID is unavailable");
    if (value.length() > 16) {
      throw new IllegalStateException("Android ID exceeds 64 bits");
    }
    for (int index = 0; index < value.length(); index++) {
      if (Character.digit(value.charAt(index), 16) < 0) {
        throw new IllegalStateException("Android ID is not hexadecimal");
      }
    }
    return value;
  }

  @Override
  public String manufacturer() {
    return buildValue(Build.MANUFACTURER);
  }

  @Override
  public String model() {
    return buildValue(Build.MODEL);
  }

  @Override
  public String packageName() {
    return applicationContext.getPackageName();
  }

  @Override
  public String versionName() {
    return requireNonEmpty(packageInfo().versionName, "application versionName is unavailable");
  }

  @Override
  @SuppressWarnings("deprecation")
  public long versionCode() {
    PackageInfo info = packageInfo();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      return info.getLongVersionCode();
    }
    return info.versionCode;
  }

  @Override
  public String androidVersion() {
    return buildValue(Build.VERSION.RELEASE);
  }

  @Override
  public int sdkInt() {
    return Build.VERSION.SDK_INT;
  }

  @Override
  public String timeZone() {
    return requireNonEmpty(TimeZone.getDefault().getID(), "Android time zone is unavailable");
  }

  @Override
  public String locales() {
    LocaleList locales = applicationContext.getResources().getConfiguration().getLocales();
    String tags = locales.toLanguageTags();
    if (!tags.isEmpty()) {
      return tags;
    }
    return requireNonEmpty(Locale.getDefault().toLanguageTag(), "Android locales are unavailable");
  }

  @Override
  public String filesDir() {
    return directoryPath(applicationContext.getFilesDir(), "files directory");
  }

  @Override
  public String noBackupFilesDir() {
    return directoryPath(applicationContext.getNoBackupFilesDir(), "no-backup files directory");
  }

  @Override
  public String cacheDir() {
    return directoryPath(applicationContext.getCacheDir(), "cache directory");
  }

  @Override
  public double batteryLevel() {
    Intent battery = batteryStatus();
    int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    if (level < 0 || scale <= 0 || level > scale) {
      throw new IllegalStateException("Android returned an invalid battery level");
    }
    return (double) level / (double) scale;
  }

  @Override
  public boolean batteryPlugged() {
    Intent battery = batteryStatus();
    int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
    if (plugged < 0) {
      throw new IllegalStateException("Android battery plugged state is unavailable");
    }
    return plugged != 0;
  }

  @Override
  public boolean interactive() {
    return powerManager().isInteractive();
  }

  @Override
  public boolean powerSaveMode() {
    return powerManager().isPowerSaveMode();
  }

  @Override
  public String networkTransports() {
    ConnectivityManager manager = connectivityManager();
    Network network = manager.getActiveNetwork();
    if (network == null) {
      return "";
    }
    NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
    if (capabilities == null) {
      return "";
    }

    List<String> transports = new ArrayList<>();
    addTransport(transports, capabilities, NetworkCapabilities.TRANSPORT_WIFI, "wifi");
    addTransport(transports, capabilities, NetworkCapabilities.TRANSPORT_ETHERNET, "ethernet");
    addTransport(transports, capabilities, NetworkCapabilities.TRANSPORT_CELLULAR, "cellular");
    addTransport(transports, capabilities, NetworkCapabilities.TRANSPORT_VPN, "vpn");
    addTransport(transports, capabilities, NetworkCapabilities.TRANSPORT_BLUETOOTH, "bluetooth");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      addTransport(
          transports,
          capabilities,
          NetworkCapabilities.TRANSPORT_WIFI_AWARE,
          "wifi-aware");
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      addTransport(transports, capabilities, NetworkCapabilities.TRANSPORT_LOWPAN, "lowpan");
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      addTransport(transports, capabilities, NetworkCapabilities.TRANSPORT_USB, "usb");
    }
    if (transports.isEmpty()) {
      transports.add("other");
    }
    return String.join(",", transports);
  }

  @Override
  public boolean networkMetered() {
    return connectivityManager().isActiveNetworkMetered();
  }

  @Override
  public String localIPAddresses() {
    ConnectivityManager manager = connectivityManager();
    Network network = manager.getActiveNetwork();
    if (network == null) {
      return "";
    }
    LinkProperties properties = manager.getLinkProperties(network);
    if (properties == null) {
      return "";
    }

    List<InetAddress> addresses = new ArrayList<>();
    for (LinkAddress linkAddress : properties.getLinkAddresses()) {
      InetAddress address = linkAddress.getAddress();
      if (address != null && !address.isAnyLocalAddress() && !address.isLoopbackAddress()) {
        addresses.add(address);
      }
    }
    addresses.sort(
        Comparator.comparingInt((InetAddress value) -> value instanceof Inet4Address ? 0 : 1)
            .thenComparing(InetAddress::getHostAddress));

    List<String> values = new ArrayList<>(addresses.size());
    for (InetAddress address : addresses) {
      values.add(address.getHostAddress());
    }
    return String.join(",", values);
  }

  @Override
  public void restartApp() {
    if (!RESTART_REQUESTED.compareAndSet(false, true)) {
      throw new IllegalStateException("an Android application restart is already in progress");
    }

    Intent restartIntent = new Intent(applicationContext, ProcessRestartActivity.class);
    restartIntent.putExtra(ProcessRestartActivity.EXTRA_PREVIOUS_PID, android.os.Process.myPid());
    Bundle binderExtra = new Bundle();
    binderExtra.putBinder(ProcessRestartActivity.EXTRA_DEATH_TOKEN, PROCESS_LIFETIME);
    restartIntent.putExtras(binderExtra);
    restartIntent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_NO_ANIMATION
            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    try {
      applicationContext.startActivity(restartIntent);
    } catch (RuntimeException e) {
      RESTART_REQUESTED.set(false);
      throw e;
    }
  }

  @SuppressWarnings("deprecation")
  private PackageInfo packageInfo() {
    try {
      PackageManager manager = applicationContext.getPackageManager();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return manager.getPackageInfo(
            applicationContext.getPackageName(),
            PackageManager.PackageInfoFlags.of(0));
      }
      return manager.getPackageInfo(applicationContext.getPackageName(), 0);
    } catch (PackageManager.NameNotFoundException e) {
      throw new IllegalStateException("Android cannot resolve its own package", e);
    }
  }

  @SuppressWarnings("deprecation")
  private Intent batteryStatus() {
    Intent status = applicationContext.registerReceiver(
        null,
        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if (status == null) {
      throw new IllegalStateException("Android battery status is unavailable");
    }
    return status;
  }

  private PowerManager powerManager() {
    PowerManager manager = applicationContext.getSystemService(PowerManager.class);
    if (manager == null) {
      throw new IllegalStateException("Android PowerManager is unavailable");
    }
    return manager;
  }

  private ConnectivityManager connectivityManager() {
    ConnectivityManager manager = applicationContext.getSystemService(ConnectivityManager.class);
    if (manager == null) {
      throw new IllegalStateException("Android ConnectivityManager is unavailable");
    }
    return manager;
  }

  private static void addTransport(
      List<String> values,
      NetworkCapabilities capabilities,
      int transport,
      String name) {
    if (capabilities.hasTransport(transport)) {
      values.add(name);
    }
  }

  private static String directoryPath(File directory, String description) {
    if (directory == null) {
      throw new IllegalStateException("Android " + description + " is unavailable");
    }
    return directory.getAbsolutePath();
  }

  private static String requireNonEmpty(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalStateException(message);
    }
    return value;
  }

  private static String buildValue(String value) {
    if (value == null || value.trim().isEmpty()) {
      return Build.UNKNOWN;
    }
    return value;
  }
}
