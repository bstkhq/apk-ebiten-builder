package test.builder;

public final class OptionalFilePickerBridgeTest {
  private static int checks;

  public interface ValidHandler {
    void onResult(String path, String message);
  }

  public interface ValidBridge {
    void setHandler(ValidHandler handler);
    void open(String mimeType);
  }

  public interface ExtraBridge extends ValidBridge {
    void close();
  }

  public interface MissingOpenBridge {
    void setHandler(ValidHandler handler);
  }

  public interface WrongSetHandlerReturnBridge {
    boolean setHandler(ValidHandler handler);
    void open(String mimeType);
  }

  public interface NonInterfaceHandlerBridge {
    void setHandler(String handler);
    void open(String mimeType);
  }

  public interface ThrowingSetHandlerBridge {
    void setHandler(ValidHandler handler) throws Exception;
    void open(String mimeType);
  }

  public interface WrongOpenArgumentBridge {
    void setHandler(ValidHandler handler);
    void open(int mimeType);
  }

  public interface WrongOpenReturnBridge {
    void setHandler(ValidHandler handler);
    boolean open(String mimeType);
  }

  public interface ThrowingOpenBridge {
    void setHandler(ValidHandler handler);
    void open(String mimeType) throws Exception;
  }

  public interface DefaultOpenBridge {
    void setHandler(ValidHandler handler);
    default void open(String mimeType) {}
  }

  public interface WrongHandler {
    boolean onResult(String path, String message);
  }

  public interface ExtraHandler extends ValidHandler {
    void onCancel();
  }

  public interface ThrowingHandler {
    void onResult(String path, String message) throws Exception;
  }

  public interface WrongHandlerBridge {
    void setHandler(WrongHandler handler);
    void open(String mimeType);
  }

  public interface ExtraHandlerBridge {
    void setHandler(ExtraHandler handler);
    void open(String mimeType);
  }

  public interface ThrowingHandlerBridge {
    void setHandler(ThrowingHandler handler);
    void open(String mimeType);
  }

  public static final class ValidMobile {
    static ValidBridge bridge;
    static ValidHandler initialHandler;
    static int registrations;

    public static void registerFilePickerBridge(ValidBridge value) {
      bridge = value;
      registrations++;
      value.setHandler(initialHandler);
    }
  }

  public static final class LegacyMobile {}

  public static final class NonInterfaceMobile {
    public static void registerFilePickerBridge(String ignored) {}
  }

  public static final class WrongRegistrationReturnMobile {
    public static boolean registerFilePickerBridge(ValidBridge ignored) {
      return true;
    }
  }

  public static final class ExtraBridgeMobile {
    public static void registerFilePickerBridge(ExtraBridge ignored) {}
  }

  public static final class MissingOpenMobile {
    public static void registerFilePickerBridge(MissingOpenBridge ignored) {}
  }

  public static final class WrongSetHandlerReturnMobile {
    public static void registerFilePickerBridge(WrongSetHandlerReturnBridge ignored) {}
  }

  public static final class NonInterfaceHandlerMobile {
    public static void registerFilePickerBridge(NonInterfaceHandlerBridge ignored) {}
  }

  public static final class ThrowingSetHandlerMobile {
    public static void registerFilePickerBridge(ThrowingSetHandlerBridge ignored) {}
  }

  public static final class WrongOpenArgumentMobile {
    public static void registerFilePickerBridge(WrongOpenArgumentBridge ignored) {}
  }

  public static final class WrongOpenReturnMobile {
    public static void registerFilePickerBridge(WrongOpenReturnBridge ignored) {}
  }

  public static final class ThrowingOpenMobile {
    public static void registerFilePickerBridge(ThrowingOpenBridge ignored) {}
  }

  public static final class DefaultOpenMobile {
    public static void registerFilePickerBridge(DefaultOpenBridge ignored) {}
  }

  public static final class WrongHandlerMobile {
    public static void registerFilePickerBridge(WrongHandlerBridge ignored) {}
  }

  public static final class ExtraHandlerMobile {
    public static void registerFilePickerBridge(ExtraHandlerBridge ignored) {}
  }

  public static final class ThrowingHandlerMobile {
    public static void registerFilePickerBridge(ThrowingHandlerBridge ignored) {}
  }

  public static final class NonStaticMobile {
    public void registerFilePickerBridge(ValidBridge ignored) {}
  }

  public static final class OverloadedMobile {
    public static void registerFilePickerBridge(ValidBridge ignored) {}
    public static void registerFilePickerBridge(String ignored) {}
  }

  public static final class ThrowingRegistrationMobile {
    public static void registerFilePickerBridge(ValidBridge ignored) {
      throw new IllegalArgumentException("registration boom");
    }
  }

  public static void main(String[] args) {
    testLegacyApplicationDoesNotCreateServices();
    testRegistrationDelegatesBothDirections();
    testHandlerCanBeReplacedAndCleared();
    testCloseDetachesServicesAndHandler();
    testRegistrationCanRefresh();
    testInvocationFailuresKeepTheirCause();
    testInvalidContractsFailExplicitly();
    System.out.println("OptionalFilePickerBridgeTest: " + checks + " checks passed");
  }

  private static void testLegacyApplicationDoesNotCreateServices() {
    RecordingFactory factory = new RecordingFactory();
    OptionalFilePickerBridge.Registration registration =
        OptionalFilePickerBridge.register(LegacyMobile.class, factory);
    check(!registration.isAvailable());
    check(!registration.hasHandler());
    check(factory.creations == 0);
    registration.close();
  }

  private static void testRegistrationDelegatesBothDirections() {
    CountingHandler handler = new CountingHandler();
    RecordingFactory factory = registerValid(handler);
    OptionalFilePickerBridge.Registration registration = factory.registration;

    check(registration.isAvailable());
    check(registration.hasHandler());
    check(ValidMobile.registrations == 1);
    check(ValidMobile.bridge != null);
    check(factory.creations == 1);

    ValidMobile.bridge.open("audio/*");
    check(factory.services.opens == 1);
    check("audio/*".equals(factory.services.mimeType));

    factory.services.emit("/cache/picked.mp4", "");
    check(handler.calls == 1);
    check("/cache/picked.mp4".equals(handler.path));
    check("".equals(handler.message));

    Object bridge = ValidMobile.bridge;
    check(bridge.equals(bridge));
    check(!bridge.equals(handler));
    check(bridge.hashCode() == System.identityHashCode(bridge));
    check("OptionalFilePickerBridge.FilePickerBridgeProxy".equals(bridge.toString()));
  }

  private static void testHandlerCanBeReplacedAndCleared() {
    CountingHandler first = new CountingHandler();
    RecordingFactory factory = registerValid(first);
    CountingHandler second = new CountingHandler();

    factory.services.emit("first", "");
    ValidMobile.bridge.setHandler(second);
    factory.services.emit("second", "warning");
    check(first.calls == 1);
    check(second.calls == 1);
    check("second".equals(second.path));
    check("warning".equals(second.message));

    ValidMobile.bridge.setHandler(null);
    check(!factory.registration.hasHandler());
    factory.services.emit("ignored", "");
    check(second.calls == 1);
  }

  private static void testCloseDetachesServicesAndHandler() {
    CountingHandler handler = new CountingHandler();
    RecordingFactory factory = registerValid(handler);
    ValidBridge bridge = ValidMobile.bridge;

    factory.registration.close();
    factory.registration.close();
    check(!factory.registration.hasHandler());
    check(factory.services.closes == 1);
    bridge.open("video/*");
    check(factory.services.opens == 0);
    factory.services.emit("ignored", "");
    check(handler.calls == 0);
    bridge.setHandler(handler);
    check(!factory.registration.hasHandler());
  }

  private static void testRegistrationCanRefresh() {
    RecordingFactory first = registerValid(new CountingHandler());
    ValidBridge firstBridge = ValidMobile.bridge;
    RecordingFactory second = registerValid(new CountingHandler());

    check(first.registration != second.registration);
    check(firstBridge != ValidMobile.bridge);
    firstBridge.open("video/first");
    ValidMobile.bridge.open("video/second");
    check(first.services.opens == 1);
    check(second.services.opens == 1);
  }

  private static void testInvocationFailuresKeepTheirCause() {
    RecordingFactory registrationFactory = new RecordingFactory();
    IllegalStateException registration = expectIllegalState(() ->
        OptionalFilePickerBridge.register(
            ThrowingRegistrationMobile.class,
            registrationFactory));
    check(registration.getCause() instanceof IllegalArgumentException);
    check("registration boom".equals(registration.getCause().getMessage()));
    check(registrationFactory.services.closes == 1);

    ValidHandler throwingHandler = (path, message) -> {
      throw new IllegalArgumentException("handler boom");
    };
    RecordingFactory registered = registerValid(throwingHandler);
    IllegalStateException handler = expectIllegalState(() ->
        registered.services.emit("path", "message"));
    check(handler.getCause() instanceof IllegalArgumentException);
    check("handler boom".equals(handler.getCause().getMessage()));

    expectNullPointer(() -> OptionalFilePickerBridge.register(ValidMobile.class, sink -> null));
  }

  private static void testInvalidContractsFailExplicitly() {
    RecordingFactory factory = new RecordingFactory();
    expectIllegalState(() -> OptionalFilePickerBridge.register(NonInterfaceMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(
        WrongRegistrationReturnMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(ExtraBridgeMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(MissingOpenMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(
        WrongSetHandlerReturnMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(
        NonInterfaceHandlerMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(
        ThrowingSetHandlerMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(
        WrongOpenArgumentMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(
        WrongOpenReturnMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(ThrowingOpenMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(DefaultOpenMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(WrongHandlerMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(ExtraHandlerMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(
        ThrowingHandlerMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(NonStaticMobile.class, factory));
    expectIllegalState(() -> OptionalFilePickerBridge.register(OverloadedMobile.class, factory));
  }

  private static RecordingFactory registerValid(ValidHandler handler) {
    ValidMobile.bridge = null;
    ValidMobile.initialHandler = handler;
    ValidMobile.registrations = 0;
    RecordingFactory factory = new RecordingFactory();
    factory.registration = OptionalFilePickerBridge.register(ValidMobile.class, factory);
    return factory;
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

  private static void expectNullPointer(Runnable runnable) {
    try {
      runnable.run();
    } catch (NullPointerException expected) {
      checks++;
      return;
    }
    throw new AssertionError("expected NullPointerException");
  }

  private static void check(boolean condition) {
    checks++;
    if (!condition) {
      throw new AssertionError("check " + checks + " failed");
    }
  }

  private static final class RecordingFactory
      implements OptionalFilePickerBridge.ServicesFactory {
    private int creations;
    private RecordingServices services;
    private OptionalFilePickerBridge.Registration registration;

    @Override
    public OptionalFilePickerBridge.Services create(
        OptionalFilePickerBridge.ResultSink resultSink) {
      creations++;
      services = new RecordingServices(resultSink);
      return services;
    }
  }

  private static final class RecordingServices
      implements OptionalFilePickerBridge.Services {
    private final OptionalFilePickerBridge.ResultSink resultSink;
    private int opens;
    private int closes;
    private String mimeType;

    RecordingServices(OptionalFilePickerBridge.ResultSink resultSink) {
      this.resultSink = resultSink;
    }

    @Override
    public void open(String value) {
      opens++;
      mimeType = value;
    }

    @Override
    public void close() {
      closes++;
    }

    void emit(String path, String message) {
      resultSink.onResult(path, message);
    }
  }

  private static final class CountingHandler implements ValidHandler {
    private int calls;
    private String path;
    private String message;

    @Override
    public void onResult(String value, String error) {
      calls++;
      path = value;
      message = error;
    }
  }
}
