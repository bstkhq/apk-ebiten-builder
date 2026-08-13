package test.builder;

public final class OptionalMobileHooksTest {
  private static int checks;

  public interface ValidBridge {
    String noBackupFilesDir();

    String localeTags();

    void restartApp();
  }

  public static final class ValidMobile {
    static ValidBridge bridge;
    static boolean consumeBack;

    public static void registerPlatformBridge(ValidBridge value) {
      bridge = value;
    }

    public static boolean onBackPressed() {
      return consumeBack;
    }
  }

  public static final class LegacyMobile {}

  public static final class WrongBridgeParameterMobile {
    public static void registerPlatformBridge(String ignored) {}
  }

  public interface PartialBridge {
    String noBackupFilesDir();

    void restartApp();
  }

  public static final class PartialBridgeMobile {
    public static void registerPlatformBridge(PartialBridge ignored) {}
  }

  public interface ExtraBridge {
    String noBackupFilesDir();

    String localeTags();

    void restartApp();

    String unexpected();
  }

  public static final class ExtraBridgeMobile {
    public static void registerPlatformBridge(ExtraBridge ignored) {}
  }

  public static final class NonStaticRegistrationMobile {
    public void registerPlatformBridge(ValidBridge ignored) {}
  }

  public static final class OverloadedRegistrationMobile {
    public static void registerPlatformBridge(ValidBridge ignored) {}

    public static void registerPlatformBridge(String ignored) {}
  }

  public static final class ThrowingRegistrationMobile {
    public static void registerPlatformBridge(ValidBridge ignored) {
      throw new IllegalArgumentException("registration boom");
    }
  }

  public static final class WrongBackReturnMobile {
    public static String onBackPressed() {
      return "no";
    }
  }

  public static final class WrongBackArgumentsMobile {
    public static boolean onBackPressed(int ignored) {
      return false;
    }
  }

  public static final class NonStaticBackMobile {
    public boolean onBackPressed() {
      return false;
    }
  }

  public static final class ThrowingBackMobile {
    public static boolean onBackPressed() {
      throw new IllegalArgumentException("back boom");
    }
  }

  public static void main(String[] args) {
    testLegacyHasNoHooks();
    testBridgeDelegatesAllPlatformServices();
    testBackHandlerPreservesTrueAndFalse();
    testInvalidBridgeContractsFailExplicitly();
    testInvalidBackContractsFailExplicitly();
    testHookFailuresKeepTheirCause();
    System.out.println("OptionalMobileHooksTest: " + checks + " checks passed");
  }

  private static void testLegacyHasNoHooks() {
    RecordingServices services = new RecordingServices();
    check(!OptionalMobileHooks.registerPlatformBridge(LegacyMobile.class, services));
    check(OptionalMobileHooks.backHandler(LegacyMobile.class) == null);
  }

  private static void testBridgeDelegatesAllPlatformServices() {
    RecordingServices services = new RecordingServices();
    ValidMobile.bridge = null;

    check(OptionalMobileHooks.registerPlatformBridge(ValidMobile.class, services));
    check(ValidMobile.bridge != null);
    check("/private/no-backup".equals(ValidMobile.bridge.noBackupFilesDir()));
    check("es-ES,en-US".equals(ValidMobile.bridge.localeTags()));
    ValidMobile.bridge.restartApp();
    check(services.restarts == 1);

    check(ValidMobile.bridge.equals(ValidMobile.bridge));
    check(!ValidMobile.bridge.equals(services));
    check(ValidMobile.bridge.hashCode() == System.identityHashCode(ValidMobile.bridge));
    check("OptionalMobileHooks.PlatformBridgeProxy".equals(ValidMobile.bridge.toString()));
  }

  private static void testBackHandlerPreservesTrueAndFalse() {
    OptionalMobileHooks.BackHandler handler =
        OptionalMobileHooks.backHandler(ValidMobile.class);
    check(handler != null);

    ValidMobile.consumeBack = true;
    check(handler.onBackPressed());
    ValidMobile.consumeBack = false;
    check(!handler.onBackPressed());
  }

  private static void testInvalidBridgeContractsFailExplicitly() {
    RecordingServices services = new RecordingServices();
    expectIllegalState(() -> OptionalMobileHooks.registerPlatformBridge(
        WrongBridgeParameterMobile.class, services));
    expectIllegalState(() -> OptionalMobileHooks.registerPlatformBridge(
        PartialBridgeMobile.class, services));
    expectIllegalState(() -> OptionalMobileHooks.registerPlatformBridge(
        ExtraBridgeMobile.class, services));
    expectIllegalState(() -> OptionalMobileHooks.registerPlatformBridge(
        NonStaticRegistrationMobile.class, services));
    expectIllegalState(() -> OptionalMobileHooks.registerPlatformBridge(
        OverloadedRegistrationMobile.class, services));
  }

  private static void testInvalidBackContractsFailExplicitly() {
    expectIllegalState(() -> OptionalMobileHooks.backHandler(WrongBackReturnMobile.class));
    expectIllegalState(() -> OptionalMobileHooks.backHandler(WrongBackArgumentsMobile.class));
    expectIllegalState(() -> OptionalMobileHooks.backHandler(NonStaticBackMobile.class));
  }

  private static void testHookFailuresKeepTheirCause() {
    IllegalStateException registration = expectIllegalState(() ->
        OptionalMobileHooks.registerPlatformBridge(
            ThrowingRegistrationMobile.class,
            new RecordingServices()));
    check(registration.getCause() instanceof IllegalArgumentException);
    check("registration boom".equals(registration.getCause().getMessage()));

    OptionalMobileHooks.BackHandler handler =
        OptionalMobileHooks.backHandler(ThrowingBackMobile.class);
    IllegalStateException back = expectIllegalState(handler::onBackPressed);
    check(back.getCause() instanceof IllegalArgumentException);
    check("back boom".equals(back.getCause().getMessage()));
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

  private static final class RecordingServices
      implements OptionalMobileHooks.PlatformServices {
    int restarts;

    @Override
    public String noBackupFilesDir() {
      return "/private/no-backup";
    }

    @Override
    public String localeTags() {
      return "es-ES,en-US";
    }

    @Override
    public void restartApp() {
      restarts++;
    }
  }
}
