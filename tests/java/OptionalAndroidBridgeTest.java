package test.builder;

public final class OptionalAndroidBridgeTest {
  private static int checks;

  public interface ValidBridge {
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

  public interface BridgeWithoutSDK {
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

  public interface WrongSDKBridge extends BridgeWithoutSDK {
    long sdkInt();
  }

  public interface ExtraBridge extends ValidBridge {
    String unexpected();
  }

  public interface WrongErrorBridge extends BridgeWithoutSDK {
    int sdkInt() throws Exception;
  }

  public static final class ValidMobile {
    static ValidBridge bridge;
    static int registrations;

    public static void registerAndroidBridge(ValidBridge value) {
      bridge = value;
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
    public static void registerAndroidBridge(String ignored) {}
  }

  public static final class MissingMethodMobile {
    public static void registerAndroidBridge(BridgeWithoutSDK ignored) {}
  }

  public static final class WrongReturnMobile {
    public static void registerAndroidBridge(WrongSDKBridge ignored) {}
  }

  public static final class WrongErrorMobile {
    public static void registerAndroidBridge(WrongErrorBridge ignored) {}
  }

  public static final class ExtraMethodMobile {
    public static void registerAndroidBridge(ExtraBridge ignored) {}
  }

  public static final class NonStaticRegistrationMobile {
    public void registerAndroidBridge(ValidBridge ignored) {}
  }

  public static final class OverloadedRegistrationMobile {
    public static void registerAndroidBridge(ValidBridge ignored) {}
    public static void registerAndroidBridge(String ignored) {}
  }

  public static final class ThrowingRegistrationMobile {
    public static void registerAndroidBridge(ValidBridge ignored) {
      throw new IllegalArgumentException("registration boom");
    }
  }

  public static void main(String[] args) throws Exception {
    testBridgeDelegatesEveryService();
    testRegistrationCanRefreshTheBridge();
    testServiceExceptionsReachTheGoContract();
    testLegacyCompatibility();
    testInvalidBridgeContractsFailExplicitly();
    testInvocationFailuresKeepTheirCause();
    System.out.println("OptionalAndroidBridgeTest: " + checks + " checks passed");
  }

  private static void testBridgeDelegatesEveryService() throws Exception {
    RecordingServices services = new RecordingServices();
    ValidMobile.bridge = null;
    ValidMobile.registrations = 0;

    check(OptionalAndroidBridge.register(ValidMobile.class, services));
    ValidBridge bridge = ValidMobile.bridge;
    check(bridge != null);
    check("0123456789abcdef".equals(bridge.androidID()));
    check("Samsung".equals(bridge.manufacturer()));
    check("SM-X216B".equals(bridge.model()));
    check("games.example.podium".equals(bridge.packageName()));
    check("1.19.2".equals(bridge.versionName()));
    check(bridge.versionCode() == 1190200L);
    check("14".equals(bridge.androidVersion()));
    check(bridge.sdkInt() == 34);
    check("Europe/Madrid".equals(bridge.timeZone()));
    check("es-ES,en-US".equals(bridge.locales()));
    check("/private/files".equals(bridge.filesDir()));
    check("/private/no-backup".equals(bridge.noBackupFilesDir()));
    check("/private/cache".equals(bridge.cacheDir()));
    check(bridge.batteryLevel() == 0.75d);
    check(bridge.batteryPlugged());
    check(bridge.interactive());
    check(!bridge.powerSaveMode());
    check("wifi,vpn".equals(bridge.networkTransports()));
    check(!bridge.networkMetered());
    check("10.0.3.17,fe80::1%wlan0".equals(bridge.localIPAddresses()));
    bridge.restartApp();
    check(services.restarts == 1);
    check(services.calls == 21);

    check(bridge.equals(bridge));
    check(!bridge.equals(services));
    check(bridge.hashCode() == System.identityHashCode(bridge));
    check("OptionalAndroidBridge.AndroidBridgeProxy".equals(bridge.toString()));
  }

  private static void testRegistrationCanRefreshTheBridge() {
    RecordingServices first = new RecordingServices();
    RecordingServices second = new RecordingServices();
    ValidMobile.bridge = null;
    ValidMobile.registrations = 0;

    check(OptionalAndroidBridge.register(ValidMobile.class, first));
    ValidBridge original = ValidMobile.bridge;
    check(OptionalAndroidBridge.register(ValidMobile.class, second));
    check(ValidMobile.registrations == 2);
    check(ValidMobile.bridge != original);
  }

  private static void testServiceExceptionsReachTheGoContract() {
    Exception expected = new Exception("android id unavailable");
    RecordingServices services = new RecordingServices() {
      @Override
      public String androidID() throws Exception {
        throw expected;
      }
    };
    check(OptionalAndroidBridge.register(ValidMobile.class, services));
    try {
      ValidMobile.bridge.androidID();
    } catch (Exception actual) {
      check(actual == expected);
      return;
    }
    throw new AssertionError("expected service exception");
  }

  private static void testLegacyCompatibility() {
    RecordingServices services = new RecordingServices();
    check(!OptionalAndroidBridge.register(LegacyMobile.class, services));

    OptionalAndroidBridge.LegacyValues values =
        OptionalAndroidBridge.configureLegacy(
            LegacyMobile.class,
            "ffffffffffffffff",
            "Europe/Madrid");
    check(values.androidId == Long.MAX_VALUE);
    check(values.timeZoneApplied);
    check(LegacyMobile.androidId == Long.MAX_VALUE);
    check("Europe/Madrid".equals(LegacyMobile.timeZone));

    values = OptionalAndroidBridge.configureLegacy(
        LegacyWithoutTimezone.class,
        "000000000000002a",
        "UTC");
    check(values.androidId == 42L);
    check(!values.timeZoneApplied);
    check(LegacyWithoutTimezone.androidId == 42L);

    expectIllegalState(() -> OptionalAndroidBridge.configureLegacy(
        MissingLegacyAndroidID.class, "1", "UTC"));
    expectIllegalState(() -> OptionalAndroidBridge.configureLegacy(
        WrongLegacyAndroidID.class, "1", "UTC"));
    WrongLegacyTimezone.androidIdCalls = 0;
    expectIllegalState(() -> OptionalAndroidBridge.configureLegacy(
        WrongLegacyTimezone.class, "1", "UTC"));
    check(WrongLegacyTimezone.androidIdCalls == 0);
    expectIllegalState(() -> OptionalAndroidBridge.configureLegacy(
        LegacyMobile.class, "not-hex", "UTC"));
  }

  private static void testInvalidBridgeContractsFailExplicitly() {
    RecordingServices services = new RecordingServices();
    expectIllegalState(() -> OptionalAndroidBridge.register(NonInterfaceMobile.class, services));
    expectIllegalState(() -> OptionalAndroidBridge.register(MissingMethodMobile.class, services));
    expectIllegalState(() -> OptionalAndroidBridge.register(WrongReturnMobile.class, services));
    expectIllegalState(() -> OptionalAndroidBridge.register(WrongErrorMobile.class, services));
    expectIllegalState(() -> OptionalAndroidBridge.register(ExtraMethodMobile.class, services));
    expectIllegalState(() -> OptionalAndroidBridge.register(
        NonStaticRegistrationMobile.class, services));
    expectIllegalState(() -> OptionalAndroidBridge.register(
        OverloadedRegistrationMobile.class, services));
  }

  private static void testInvocationFailuresKeepTheirCause() {
    IllegalStateException registration = expectIllegalState(() ->
        OptionalAndroidBridge.register(
            ThrowingRegistrationMobile.class,
            new RecordingServices()));
    check(registration.getCause() instanceof IllegalArgumentException);
    check("registration boom".equals(registration.getCause().getMessage()));

    IllegalStateException legacy = expectIllegalState(() ->
        OptionalAndroidBridge.configureLegacy(
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

  private static class RecordingServices implements OptionalAndroidBridge.Services {
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
