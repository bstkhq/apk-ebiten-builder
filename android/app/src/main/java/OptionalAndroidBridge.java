package @@APP_ID@@;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapts the optional gomobile AndroidBridge interface without linking its
 * generated Java type.
 *
 * <p>Applications built before AndroidBridge continue through the legacy
 * {@code setAndroidID(long)} and optional {@code setTimezone(String)} exports.
 * A named export with an incompatible signature is always a contract error.</p>
 */
final class OptionalAndroidBridge {
  interface Services {
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

  private static final MethodSpec[] BRIDGE_METHODS = {
    new MethodSpec("androidID", String.class, true),
    new MethodSpec("manufacturer", String.class, false),
    new MethodSpec("model", String.class, false),
    new MethodSpec("packageName", String.class, false),
    new MethodSpec("versionName", String.class, true),
    new MethodSpec("versionCode", long.class, true),
    new MethodSpec("androidVersion", String.class, false),
    new MethodSpec("sdkInt", int.class, false),
    new MethodSpec("timeZone", String.class, true),
    new MethodSpec("locales", String.class, true),
    new MethodSpec("filesDir", String.class, true),
    new MethodSpec("noBackupFilesDir", String.class, true),
    new MethodSpec("cacheDir", String.class, true),
    new MethodSpec("batteryLevel", double.class, true),
    new MethodSpec("batteryPlugged", boolean.class, true),
    new MethodSpec("interactive", boolean.class, true),
    new MethodSpec("powerSaveMode", boolean.class, true),
    new MethodSpec("networkTransports", String.class, true),
    new MethodSpec("networkMetered", boolean.class, true),
    new MethodSpec("localIPAddresses", String.class, true),
    new MethodSpec("restartApp", void.class, true)
  };

  private OptionalAndroidBridge() {}

  static boolean register(Class<?> mobileClass, Services services) {
    requireNonNull(mobileClass, "mobileClass");
    requireNonNull(services, "services");

    Method register = optionalNamedMethod(mobileClass, "registerAndroidBridge");
    if (register == null) {
      return false;
    }

    requireStatic(register);
    if (register.getReturnType() != void.class || register.getParameterCount() != 1) {
      throw incompatible(
          register,
          "expected static void registerAndroidBridge(AndroidBridge)");
    }

    Class<?> bridgeType = register.getParameterTypes()[0];
    if (!bridgeType.isInterface()) {
      throw incompatible(register, "AndroidBridge parameter must be an interface");
    }
    validateBridgeInterface(bridgeType);

    ClassLoader loader = bridgeType.getClassLoader();
    if (loader == null) {
      loader = mobileClass.getClassLoader();
    }
    Object proxy = Proxy.newProxyInstance(
        loader,
        new Class<?>[] {bridgeType},
        new BridgeInvocationHandler(services));

    invokeStatic(register, proxy, "AndroidBridge registration");
    return true;
  }

  /** Applies the API retained for applications that predate AndroidBridge. */
  static LegacyValues configureLegacy(
      Class<?> mobileClass,
      String androidId,
      String timeZone) {
    requireNonNull(mobileClass, "mobileClass");
    requireNonNull(androidId, "androidId");
    requireNonNull(timeZone, "timeZone");

    long signedAndroidId;
    try {
      signedAndroidId = Long.parseUnsignedLong(androidId, 16) & Long.MAX_VALUE;
    } catch (NumberFormatException e) {
      throw new IllegalStateException(
          "Android ID is not an unsigned 64-bit hexadecimal value: " + androidId,
          e);
    }

    Method setAndroidId = requiredNamedMethod(mobileClass, "setAndroidID");
    requireStatic(setAndroidId);
    if (setAndroidId.getReturnType() != void.class
        || setAndroidId.getParameterCount() != 1
        || setAndroidId.getParameterTypes()[0] != long.class) {
      throw incompatible(setAndroidId, "expected static void setAndroidID(long)");
    }

    Method setTimeZone = optionalNamedMethod(mobileClass, "setTimezone");
    boolean timeZoneApplied = setTimeZone != null;
    if (setTimeZone != null) {
      requireStatic(setTimeZone);
      if (setTimeZone.getReturnType() != void.class
          || setTimeZone.getParameterCount() != 1
          || setTimeZone.getParameterTypes()[0] != String.class) {
        throw incompatible(setTimeZone, "expected static void setTimezone(String)");
      }
    }

    // Validate the complete legacy contract before changing Go process state.
    invokeStatic(setAndroidId, signedAndroidId, "legacy setAndroidID");
    if (setTimeZone != null) {
      invokeStatic(setTimeZone, timeZone, "legacy setTimezone");
    }

    return new LegacyValues(signedAndroidId, timeZoneApplied);
  }

  static final class LegacyValues {
    final long androidId;
    final boolean timeZoneApplied;

    LegacyValues(long androidId, boolean timeZoneApplied) {
      this.androidId = androidId;
      this.timeZoneApplied = timeZoneApplied;
    }
  }

  private static void validateBridgeInterface(Class<?> bridgeType) {
    Method[] methods = bridgeType.getMethods();
    if (methods.length != BRIDGE_METHODS.length) {
      throw new IllegalStateException(
          "optional gomobile AndroidBridge " + bridgeType.getName()
              + " must expose exactly " + BRIDGE_METHODS.length + " methods");
    }

    for (MethodSpec spec : BRIDGE_METHODS) {
      final Method method;
      try {
        method = bridgeType.getMethod(spec.name);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(
            "optional gomobile AndroidBridge " + bridgeType.getName()
                + " is missing " + spec.name + "()",
            e);
      }

      if (method.getParameterCount() != 0 || method.getReturnType() != spec.returnType) {
        throw incompatible(
            method,
            "expected " + spec.returnType.getName() + " " + spec.name + "()");
      }
      if (Modifier.isStatic(method.getModifiers()) || !Modifier.isAbstract(method.getModifiers())) {
        throw incompatible(method, "AndroidBridge methods must be abstract instance methods");
      }

      Class<?>[] exceptions = method.getExceptionTypes();
      if (spec.returnsError) {
        if (exceptions.length != 1 || exceptions[0] != Exception.class) {
          throw incompatible(method, "Go error result must map to throws Exception");
        }
      } else if (exceptions.length != 0) {
        throw incompatible(method, "method without a Go error result must not throw");
      }
    }
  }

  private static Method optionalNamedMethod(Class<?> owner, String name) {
    List<Method> named = new ArrayList<>();
    for (Method method : owner.getMethods()) {
      if (method.getName().equals(name)) {
        named.add(method);
      }
    }

    if (named.isEmpty()) {
      return null;
    }
    if (named.size() != 1) {
      throw new IllegalStateException(
          "gomobile export " + owner.getName() + "." + name
              + " is overloaded; expected exactly one public method");
    }
    return named.get(0);
  }

  private static Method requiredNamedMethod(Class<?> owner, String name) {
    Method method = optionalNamedMethod(owner, name);
    if (method == null) {
      throw new IllegalStateException(
          "legacy gomobile application is missing public static void "
              + owner.getName() + "." + name + "(long)");
    }
    return method;
  }

  private static void requireStatic(Method method) {
    if (!Modifier.isStatic(method.getModifiers())) {
      throw incompatible(method, "export must be static");
    }
  }

  private static void invokeStatic(Method method, Object argument, String operation) {
    try {
      method.invoke(null, argument);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("cannot access " + operation, e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException(operation + " failed", e.getCause());
    }
  }

  private static void requireNonNull(Object value, String name) {
    if (value == null) {
      throw new NullPointerException(name);
    }
  }

  private static IllegalStateException incompatible(Method method, String expectation) {
    return new IllegalStateException(
        "incompatible gomobile export " + method.toGenericString() + ": " + expectation);
  }

  private static final class MethodSpec {
    final String name;
    final Class<?> returnType;
    final boolean returnsError;

    MethodSpec(String name, Class<?> returnType, boolean returnsError) {
      this.name = name;
      this.returnType = returnType;
      this.returnsError = returnsError;
    }
  }

  private static final class BridgeInvocationHandler implements InvocationHandler {
    private final Services services;

    BridgeInvocationHandler(Services services) {
      this.services = services;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
      if (method.getDeclaringClass() == Object.class) {
        switch (method.getName()) {
          case "equals":
            return proxy == args[0];
          case "hashCode":
            return System.identityHashCode(proxy);
          case "toString":
            return "OptionalAndroidBridge.AndroidBridgeProxy";
          default:
            throw new IllegalStateException("unsupported Object method " + method.getName());
        }
      }

      switch (method.getName()) {
        case "androidID":
          return services.androidID();
        case "manufacturer":
          return services.manufacturer();
        case "model":
          return services.model();
        case "packageName":
          return services.packageName();
        case "versionName":
          return services.versionName();
        case "versionCode":
          return services.versionCode();
        case "androidVersion":
          return services.androidVersion();
        case "sdkInt":
          return services.sdkInt();
        case "timeZone":
          return services.timeZone();
        case "locales":
          return services.locales();
        case "filesDir":
          return services.filesDir();
        case "noBackupFilesDir":
          return services.noBackupFilesDir();
        case "cacheDir":
          return services.cacheDir();
        case "batteryLevel":
          return services.batteryLevel();
        case "batteryPlugged":
          return services.batteryPlugged();
        case "interactive":
          return services.interactive();
        case "powerSaveMode":
          return services.powerSaveMode();
        case "networkTransports":
          return services.networkTransports();
        case "networkMetered":
          return services.networkMetered();
        case "localIPAddresses":
          return services.localIPAddresses();
        case "restartApp":
          services.restartApp();
          return null;
        default:
          throw new IllegalStateException(
              "unsupported AndroidBridge method " + method.getName());
      }
    }
  }
}
