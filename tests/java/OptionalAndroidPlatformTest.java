package test.builder;

public final class OptionalAndroidPlatformTest {
  private static int checks;

  public interface ValidPlatform {
    String androidID() throws Exception;
    String manufacturer();
    String model();
    String packageName();
    String versionName() throws Exception;
    long versionCode() throws Exception;
    String androidVersion();
    int sdkInt();
    String timeZone() throws Exception;
    String locales() throws Exception;
    String filesDir() throws Exception;
    String noBackupFilesDir() throws Exception;
    String cacheDir() throws Exception;
    double batteryLevel() throws Exception;
    boolean batteryPlugged() throws Exception;
    boolean interactive() throws Exception;
    boolean powerSaveMode() throws Exception;
    String networkTransports() throws Exception;
    boolean networkMetered() throws Exception;
    String localIPAddresses() throws Exception;
    void restartApp() throws Exception;
  }

  public interface PlatformWithoutSDK {
    String androidID() throws Exception;
    String manufacturer();
    String model();
    String packageName();
    String versionName() throws Exception;
    long versionCode() throws Exception;
    String androidVersion();
    String timeZone() throws Exception;
    String locales() throws Exception;
    String filesDir() throws Exception;
    String noBackupFilesDir() throws Exception;
    String cacheDir() throws Exception;
    double batteryLevel() throws Exception;
    boolean batteryPlugged() throws Exception;
    boolean interactive() throws Exception;
    boolean powerSaveMode() throws Exception;
    String networkTransports() throws Exception;
    boolean networkMetered() throws Exception;
    String localIPAddresses() throws Exception;
    void restartApp() throws Exception;
  }

  public interface WrongSDKPlatform extends PlatformWithoutSDK {
    long sdkInt();
  }

  public interface ExtraPlatform extends ValidPlatform {
    String unexpected();
  }

  public interface WrongErrorPlatform extends PlatformWithoutSDK {
    int sdkInt() throws Exception;
  }

  public static final class ValidMobile {
    static ValidPlatform platform;
    static int registrations;

    public static void registerAndroidPlatform(ValidPlatform value) {
      platform = value;
      registrations++;
    }
  }

  public static final class LegacyMobile {
    static long androidId;
    static String timeZone;

    public static void setAndroidID(long value) {
      androidId = value;
    }

    public static void setTimezone(String value) {
      timeZone = value;
    }
  }

  public static final class LegacyWithoutTimezone {
    static long androidId;

    public static void setAndroidID(long value) {
      androidId = value;
    }
  }

  public static final class MissingLegacyAndroidID {}

  public static final class WrongLegacyAndroidID {
    public static void setAndroidID(String ignored) {}
  }

  public static final class WrongLegacyTimezone {
    static int androidIdCalls;

    public static void setAndroidID(long ignored) {
      androidIdCalls++;
    }
    public static void setTimezone(long ignored) {}
  }

  public static final class ThrowingLegacyMobile {
    public static void setAndroidID(long ignored) {
      throw new IllegalArgumentException("legacy boom");
    }
  }

  public static final class NonInterfaceMobile {
    public static void registerAndroidPlatform(String ignored) {}
  }

  public static final class MissingMethodMobile {
    public static void registerAndroidPlatform(PlatformWithoutSDK ignored) {}
  }

  public static final class WrongReturnMobile {
    public static void registerAndroidPlatform(WrongSDKPlatform ignored) {}
  }

  public static final class WrongErrorMobile {
    public static void registerAndroidPlatform(WrongErrorPlatform ignored) {}
  }

  public static final class ExtraMethodMobile {
    public static void registerAndroidPlatform(ExtraPlatform ignored) {}
  }

  public static final class NonStaticRegistrationMobile {
    public void registerAndroidPlatform(ValidPlatform ignored) {}
  }

  public static final class OverloadedRegistrationMobile {
    public static void registerAndroidPlatform(ValidPlatform ignored) {}
    public static void registerAndroidPlatform(String ignored) {}
  }

  public static final class ThrowingRegistrationMobile {
    public static void registerAndroidPlatform(ValidPlatform ignored) {
      throw new IllegalArgumentException("registration boom");
    }
  }

  public static void main(String[] args) throws Exception {
    testPlatformDelegatesEveryService();
    testRegistrationCanRefreshThePlatform();
    testServiceExceptionsReachTheGoContract();
    testLegacyCompatibility();
    testInvalidPlatformContractsFailExplicitly();
    testInvocationFailuresKeepTheirCause();
    System.out.println("OptionalAndroidPlatformTest: " + checks + " checks passed");
  }

  private static void testPlatformDelegatesEveryService() throws Exception {
    RecordingServices services = new RecordingServices();
    ValidMobile.platform = null;
    ValidMobile.registrations = 0;

    check(OptionalAndroidPlatform.register(ValidMobile.class, services));
    ValidPlatform platform = ValidMobile.platform;
    check(platform != null);
    check("0123456789abcdef".equals(platform.androidID()));
    check("Samsung".equals(platform.manufacturer()));
    check("SM-X216B".equals(platform.model()));
    check("games.example.podium".equals(platform.packageName()));
    check("1.19.2".equals(platform.versionName()));
    check(platform.versionCode() == 1190200L);
    check("14".equals(platform.androidVersion()));
    check(platform.sdkInt() == 34);
    check("Europe/Madrid".equals(platform.timeZone()));
    check("es-ES,en-US".equals(platform.locales()));
    check("/private/files".equals(platform.filesDir()));
    check("/private/no-backup".equals(platform.noBackupFilesDir()));
    check("/private/cache".equals(platform.cacheDir()));
    check(platform.batteryLevel() == 0.75d);
    check(platform.batteryPlugged());
    check(platform.interactive());
    check(!platform.powerSaveMode());
    check("wifi,vpn".equals(platform.networkTransports()));
    check(!platform.networkMetered());
    check("10.0.3.17,fe80::1%wlan0".equals(platform.localIPAddresses()));
    platform.restartApp();
    check(services.restarts == 1);
    check(services.calls == 21);

    check(platform.equals(platform));
    check(!platform.equals(services));
    check(platform.hashCode() == System.identityHashCode(platform));
    check("OptionalAndroidPlatform.AndroidPlatformProxy".equals(platform.toString()));
  }

  private static void testRegistrationCanRefreshThePlatform() {
    RecordingServices first = new RecordingServices();
    RecordingServices second = new RecordingServices();
    ValidMobile.platform = null;
    ValidMobile.registrations = 0;

    check(OptionalAndroidPlatform.register(ValidMobile.class, first));
    ValidPlatform original = ValidMobile.platform;
    check(OptionalAndroidPlatform.register(ValidMobile.class, second));
    check(ValidMobile.registrations == 2);
    check(ValidMobile.platform != original);
  }

  private static void testServiceExceptionsReachTheGoContract() {
    Exception expected = new Exception("android id unavailable");
    RecordingServices services = new RecordingServices() {
      @Override
      public String androidID() throws Exception {
        throw expected;
      }
    };
    check(OptionalAndroidPlatform.register(ValidMobile.class, services));
    try {
      ValidMobile.platform.androidID();
    } catch (Exception actual) {
      check(actual == expected);
      return;
    }
    throw new AssertionError("expected service exception");
  }

  private static void testLegacyCompatibility() {
    RecordingServices services = new RecordingServices();
    check(!OptionalAndroidPlatform.register(LegacyMobile.class, services));

    OptionalAndroidPlatform.LegacyValues values =
        OptionalAndroidPlatform.configureLegacy(
            LegacyMobile.class,
            "ffffffffffffffff",
            "Europe/Madrid");
    check(values.androidId == Long.MAX_VALUE);
    check(values.timeZoneApplied);
    check(LegacyMobile.androidId == Long.MAX_VALUE);
    check("Europe/Madrid".equals(LegacyMobile.timeZone));

    values = OptionalAndroidPlatform.configureLegacy(
        LegacyWithoutTimezone.class,
        "000000000000002a",
        "UTC");
    check(values.androidId == 42L);
    check(!values.timeZoneApplied);
    check(LegacyWithoutTimezone.androidId == 42L);

    expectIllegalState(() -> OptionalAndroidPlatform.configureLegacy(
        MissingLegacyAndroidID.class, "1", "UTC"));
    expectIllegalState(() -> OptionalAndroidPlatform.configureLegacy(
        WrongLegacyAndroidID.class, "1", "UTC"));
    WrongLegacyTimezone.androidIdCalls = 0;
    expectIllegalState(() -> OptionalAndroidPlatform.configureLegacy(
        WrongLegacyTimezone.class, "1", "UTC"));
    check(WrongLegacyTimezone.androidIdCalls == 0);
    expectIllegalState(() -> OptionalAndroidPlatform.configureLegacy(
        LegacyMobile.class, "not-hex", "UTC"));
  }

  private static void testInvalidPlatformContractsFailExplicitly() {
    RecordingServices services = new RecordingServices();
    expectIllegalState(() -> OptionalAndroidPlatform.register(NonInterfaceMobile.class, services));
    expectIllegalState(() -> OptionalAndroidPlatform.register(MissingMethodMobile.class, services));
    expectIllegalState(() -> OptionalAndroidPlatform.register(WrongReturnMobile.class, services));
    expectIllegalState(() -> OptionalAndroidPlatform.register(WrongErrorMobile.class, services));
    expectIllegalState(() -> OptionalAndroidPlatform.register(ExtraMethodMobile.class, services));
    expectIllegalState(() -> OptionalAndroidPlatform.register(
        NonStaticRegistrationMobile.class, services));
    expectIllegalState(() -> OptionalAndroidPlatform.register(
        OverloadedRegistrationMobile.class, services));
  }

  private static void testInvocationFailuresKeepTheirCause() {
    IllegalStateException registration = expectIllegalState(() ->
        OptionalAndroidPlatform.register(
            ThrowingRegistrationMobile.class,
            new RecordingServices()));
    check(registration.getCause() instanceof IllegalArgumentException);
    check("registration boom".equals(registration.getCause().getMessage()));

    IllegalStateException legacy = expectIllegalState(() ->
        OptionalAndroidPlatform.configureLegacy(
            ThrowingLegacyMobile.class,
            "1",
            "UTC"));
    check(legacy.getCause() instanceof IllegalArgumentException);
    check("legacy boom".equals(legacy.getCause().getMessage()));
  }

  private static IllegalStateException expectIllegalState(Runnable runnable) {
    try {
      runnable.run();
    } catch (IllegalStateException expected) {
      checks++;
      return expected;
    }
    throw new AssertionError("expected IllegalStateException");
  }

  private static void check(boolean condition) {
    checks++;
    if (!condition) {
      throw new AssertionError("check " + checks + " failed");
    }
  }

  private static class RecordingServices implements OptionalAndroidPlatform.Services {
    int calls;
    int restarts;

    @Override public String androidID() throws Exception { calls++; return "0123456789abcdef"; }
    @Override public String manufacturer() { calls++; return "Samsung"; }
    @Override public String model() { calls++; return "SM-X216B"; }
    @Override public String packageName() { calls++; return "games.example.podium"; }
    @Override public String versionName() throws Exception { calls++; return "1.19.2"; }
    @Override public long versionCode() throws Exception { calls++; return 1190200L; }
    @Override public String androidVersion() { calls++; return "14"; }
    @Override public int sdkInt() { calls++; return 34; }
    @Override public String timeZone() throws Exception { calls++; return "Europe/Madrid"; }
    @Override public String locales() throws Exception { calls++; return "es-ES,en-US"; }
    @Override public String filesDir() throws Exception { calls++; return "/private/files"; }
    @Override public String noBackupFilesDir() throws Exception { calls++; return "/private/no-backup"; }
    @Override public String cacheDir() throws Exception { calls++; return "/private/cache"; }
    @Override public double batteryLevel() throws Exception { calls++; return 0.75d; }
    @Override public boolean batteryPlugged() throws Exception { calls++; return true; }
    @Override public boolean interactive() throws Exception { calls++; return true; }
    @Override public boolean powerSaveMode() throws Exception { calls++; return false; }
    @Override public String networkTransports() throws Exception { calls++; return "wifi,vpn"; }
    @Override public boolean networkMetered() throws Exception { calls++; return false; }
    @Override public String localIPAddresses() throws Exception {
      calls++;
      return "10.0.3.17,fe80::1%wlan0";
    }
    @Override public void restartApp() throws Exception { calls++; restarts++; }
  }
}
