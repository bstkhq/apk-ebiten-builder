package test.builder;

public final class OptionalBackBridgeTest {
  private static int checks;

  public interface ValidHandler {
    boolean onBack();
  }

  public interface ValidBridge {
    void setHandler(ValidHandler handler);
  }

  public interface ExtraBridge extends ValidBridge {
    void clearHandler();
  }

  public interface WrongBridgeReturn {
    boolean setHandler(ValidHandler handler);
  }

  public interface ThrowingBridge {
    void setHandler(ValidHandler handler) throws Exception;
  }

  public interface WrongHandler {
    int onBack();
  }

  public interface ExtraHandler extends ValidHandler {
    boolean onBackAgain();
  }

  public interface ThrowingHandler {
    boolean onBack() throws Exception;
  }

  public interface WrongHandlerBridge {
    void setHandler(WrongHandler handler);
  }

  public interface ExtraHandlerBridge {
    void setHandler(ExtraHandler handler);
  }

  public interface ThrowingHandlerBridge {
    void setHandler(ThrowingHandler handler);
  }

  public static final class ValidMobile {
    static ValidBridge bridge;
    static ValidHandler initialHandler;
    static int registrations;

    public static void registerBackBridge(ValidBridge value) {
      bridge = value;
      registrations++;
      value.setHandler(initialHandler);
    }
  }

  public static final class LegacyMobile {}

  public static final class NonInterfaceMobile {
    public static void registerBackBridge(String ignored) {}
  }

  public static final class WrongBridgeReturnMobile {
    public static void registerBackBridge(WrongBridgeReturn ignored) {}
  }

  public static final class ExtraBridgeMobile {
    public static void registerBackBridge(ExtraBridge ignored) {}
  }

  public static final class ThrowingBridgeMobile {
    public static void registerBackBridge(ThrowingBridge ignored) {}
  }

  public static final class WrongHandlerMobile {
    public static void registerBackBridge(WrongHandlerBridge ignored) {}
  }

  public static final class ExtraHandlerMobile {
    public static void registerBackBridge(ExtraHandlerBridge ignored) {}
  }

  public static final class ThrowingHandlerMobile {
    public static void registerBackBridge(ThrowingHandlerBridge ignored) {}
  }

  public static final class NonStaticMobile {
    public void registerBackBridge(ValidBridge ignored) {}
  }

  public static final class OverloadedMobile {
    public static void registerBackBridge(ValidBridge ignored) {}
    public static void registerBackBridge(String ignored) {}
  }

  public static final class ThrowingRegistrationMobile {
    public static void registerBackBridge(ValidBridge ignored) {
      throw new IllegalArgumentException("registration boom");
    }
  }

  public static void main(String[] args) {
    testLegacyApplicationIsUnavailable();
    testRegistrationAndDispatch();
    testHandlerCanBeReplacedAndCleared();
    testRegistrationCanRefresh();
    testHandlerFailureKeepsItsCause();
    testInvalidContractsFailExplicitly();
    System.out.println("OptionalBackBridgeTest: " + checks + " checks passed");
  }

  private static void testLegacyApplicationIsUnavailable() {
    OptionalBackBridge.Registration registration = OptionalBackBridge.register(LegacyMobile.class);
    check(!registration.isAvailable());
    check(!registration.hasHandler());
    check(!registration.onBack());
  }

  private static void testRegistrationAndDispatch() {
    CountingHandler handler = new CountingHandler(true);
    ValidMobile.bridge = null;
    ValidMobile.initialHandler = handler;
    ValidMobile.registrations = 0;

    OptionalBackBridge.Registration registration = OptionalBackBridge.register(ValidMobile.class);
    check(registration.isAvailable());
    check(registration.hasHandler());
    check(ValidMobile.registrations == 1);
    check(ValidMobile.bridge != null);
    check(registration.onBack());
    check(handler.calls == 1);

    Object bridge = ValidMobile.bridge;
    check(bridge.equals(bridge));
    check(!bridge.equals(handler));
    check(bridge.hashCode() == System.identityHashCode(bridge));
    check("OptionalBackBridge.BackBridgeProxy".equals(bridge.toString()));
  }

  private static void testHandlerCanBeReplacedAndCleared() {
    CountingHandler first = new CountingHandler(true);
    CountingHandler second = new CountingHandler(false);
    ValidMobile.initialHandler = first;
    OptionalBackBridge.Registration registration = OptionalBackBridge.register(ValidMobile.class);

    check(registration.onBack());
    ValidMobile.bridge.setHandler(second);
    check(registration.hasHandler());
    check(!registration.onBack());
    check(first.calls == 1);
    check(second.calls == 1);

    ValidMobile.bridge.setHandler(null);
    check(!registration.hasHandler());
    check(!registration.onBack());
  }

  private static void testRegistrationCanRefresh() {
    ValidMobile.initialHandler = new CountingHandler(true);
    OptionalBackBridge.Registration first = OptionalBackBridge.register(ValidMobile.class);
    ValidBridge firstBridge = ValidMobile.bridge;

    ValidMobile.initialHandler = new CountingHandler(false);
    OptionalBackBridge.Registration second = OptionalBackBridge.register(ValidMobile.class);
    check(first != second);
    check(firstBridge != ValidMobile.bridge);
    check(first.onBack());
    check(!second.onBack());
  }

  private static void testHandlerFailureKeepsItsCause() {
    ValidMobile.initialHandler = () -> {
      throw new IllegalArgumentException("handler boom");
    };
    OptionalBackBridge.Registration registration = OptionalBackBridge.register(ValidMobile.class);
    IllegalStateException failure = expectIllegalState(registration::onBack);
    check(failure.getCause() instanceof IllegalArgumentException);
    check("handler boom".equals(failure.getCause().getMessage()));

    IllegalStateException registrationFailure = expectIllegalState(() ->
        OptionalBackBridge.register(ThrowingRegistrationMobile.class));
    check(registrationFailure.getCause() instanceof IllegalArgumentException);
    check("registration boom".equals(registrationFailure.getCause().getMessage()));
  }

  private static void testInvalidContractsFailExplicitly() {
    expectIllegalState(() -> OptionalBackBridge.register(NonInterfaceMobile.class));
    expectIllegalState(() -> OptionalBackBridge.register(WrongBridgeReturnMobile.class));
    expectIllegalState(() -> OptionalBackBridge.register(ExtraBridgeMobile.class));
    expectIllegalState(() -> OptionalBackBridge.register(ThrowingBridgeMobile.class));
    expectIllegalState(() -> OptionalBackBridge.register(WrongHandlerMobile.class));
    expectIllegalState(() -> OptionalBackBridge.register(ExtraHandlerMobile.class));
    expectIllegalState(() -> OptionalBackBridge.register(ThrowingHandlerMobile.class));
    expectIllegalState(() -> OptionalBackBridge.register(NonStaticMobile.class));
    expectIllegalState(() -> OptionalBackBridge.register(OverloadedMobile.class));
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

  private static final class CountingHandler implements ValidHandler {
    private final boolean consumed;
    private int calls;

    CountingHandler(boolean consumed) {
      this.consumed = consumed;
    }

    @Override
    public boolean onBack() {
      calls++;
      return consumed;
    }
  }
}
