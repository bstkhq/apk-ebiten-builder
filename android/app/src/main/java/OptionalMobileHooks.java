package @@APP_ID@@;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapts optional gomobile exports without linking their generated Java types.
 *
 * <p>Existing applications do not generate PlatformBridge or onBackPressed, so
 * the Android template must discover both contracts at runtime. A method with
 * the expected name but an incompatible signature is treated as a build/runtime
 * contract error instead of being silently ignored.</p>
 */
final class OptionalMobileHooks {
  interface PlatformServices {
    String noBackupFilesDir();

    String localeTags();

    void restartApp();
  }

  interface BackHandler {
    boolean onBackPressed();
  }

  private OptionalMobileHooks() {}

  static boolean registerPlatformBridge(
      Class<?> mobileClass,
      PlatformServices services) {
    if (mobileClass == null) {
      throw new NullPointerException("mobileClass");
    }
    if (services == null) {
      throw new NullPointerException("services");
    }

    Method register = optionalNamedMethod(mobileClass, "registerPlatformBridge");
    if (register == null) {
      return false;
    }

    requireStatic(register);
    if (register.getReturnType() != void.class || register.getParameterCount() != 1) {
      throw incompatible(register, "expected static void registerPlatformBridge(Interface)");
    }

    Class<?> bridgeType = register.getParameterTypes()[0];
    if (!bridgeType.isInterface()) {
      throw incompatible(register, "bridge parameter must be an interface");
    }
    validateBridgeInterface(bridgeType);

    ClassLoader loader = bridgeType.getClassLoader();
    if (loader == null) {
      loader = mobileClass.getClassLoader();
    }
    Object proxy = Proxy.newProxyInstance(
        loader,
        new Class<?>[] {bridgeType},
        new PlatformInvocationHandler(services));

    try {
      register.invoke(null, proxy);
      return true;
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("cannot access optional gomobile platform bridge", e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException(
          "optional gomobile platform bridge registration failed",
          e.getCause());
    }
  }

  static BackHandler backHandler(Class<?> mobileClass) {
    if (mobileClass == null) {
      throw new NullPointerException("mobileClass");
    }

    Method hook = optionalNamedMethod(mobileClass, "onBackPressed");
    if (hook == null) {
      return null;
    }

    requireStatic(hook);
    if (hook.getReturnType() != boolean.class || hook.getParameterCount() != 0) {
      throw incompatible(hook, "expected static boolean onBackPressed()");
    }

    return () -> {
      try {
        return (Boolean) hook.invoke(null);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("cannot access optional gomobile Back hook", e);
      } catch (InvocationTargetException e) {
        throw new IllegalStateException("optional gomobile Back hook failed", e.getCause());
      }
    };
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
          "optional gomobile hook " + owner.getName() + "." + name
              + " is overloaded; expected exactly one public method");
    }
    return named.get(0);
  }

  private static void requireStatic(Method method) {
    if (!Modifier.isStatic(method.getModifiers())) {
      throw incompatible(method, "hook must be static");
    }
  }

  private static void validateBridgeInterface(Class<?> bridgeType) {
    Method[] methods = bridgeType.getMethods();
    if (methods.length != 3) {
      throw new IllegalStateException(
          "optional gomobile bridge " + bridgeType.getName()
              + " must expose exactly noBackupFilesDir, localeTags and restartApp");
    }

    requireBridgeMethod(bridgeType, "noBackupFilesDir", String.class);
    requireBridgeMethod(bridgeType, "localeTags", String.class);
    requireBridgeMethod(bridgeType, "restartApp", void.class);
  }

  private static void requireBridgeMethod(
      Class<?> bridgeType,
      String name,
      Class<?> returnType) {
    final Method method;
    try {
      method = bridgeType.getMethod(name);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          "optional gomobile bridge " + bridgeType.getName()
              + " is missing " + name + "()",
          e);
    }

    if (method.getParameterCount() != 0 || method.getReturnType() != returnType) {
      throw incompatible(
          method,
          "expected " + returnType.getTypeName() + " " + name + "()");
    }
  }

  private static IllegalStateException incompatible(Method method, String expectation) {
    return new IllegalStateException(
        "incompatible optional gomobile hook " + method.toGenericString()
            + ": " + expectation);
  }

  private static final class PlatformInvocationHandler implements InvocationHandler {
    private final PlatformServices services;

    PlatformInvocationHandler(PlatformServices services) {
      this.services = services;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      if (method.getDeclaringClass() == Object.class) {
        switch (method.getName()) {
          case "equals":
            return proxy == args[0];
          case "hashCode":
            return System.identityHashCode(proxy);
          case "toString":
            return "OptionalMobileHooks.PlatformBridgeProxy";
          default:
            throw new IllegalStateException("unsupported Object method " + method.getName());
        }
      }

      switch (method.getName()) {
        case "noBackupFilesDir":
          return services.noBackupFilesDir();
        case "localeTags":
          return services.localeTags();
        case "restartApp":
          services.restartApp();
          return null;
        default:
          throw new IllegalStateException(
              "unsupported optional gomobile bridge method " + method.getName());
      }
    }
  }
}
