package @@APP_ID@@;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adapts an optional gomobile {@code BackBridge} without linking generated
 * application types into the reusable Android template.
 *
 * <p>The Go contract is:</p>
 *
 * <pre>{@code
 * type BackHandler interface { OnBack() bool }
 * type BackBridge interface { SetHandler(BackHandler) }
 * func RegisterBackBridge(BackBridge)
 * }</pre>
 *
 * <p>Applications without that complete export retain the Activity's existing
 * Back behavior. A named but incompatible export is a contract error.</p>
 */
final class OptionalBackBridge {
  private OptionalBackBridge() {}

  static Registration register(Class<?> mobileClass) {
    requireNonNull(mobileClass, "mobileClass");

    Method register = optionalNamedMethod(mobileClass, "registerBackBridge");
    if (register == null) {
      return Registration.unavailable();
    }

    requireStatic(register);
    if (register.getReturnType() != void.class || register.getParameterCount() != 1) {
      throw incompatible(register, "expected static void registerBackBridge(BackBridge)");
    }

    Class<?> bridgeType = register.getParameterTypes()[0];
    if (!bridgeType.isInterface()) {
      throw incompatible(register, "BackBridge parameter must be an interface");
    }

    Method setHandler = requireOnlyBridgeMethod(bridgeType);
    Class<?> handlerType = setHandler.getParameterTypes()[0];
    Method onBack = requireHandlerMethod(handlerType);
    Registration registration = new Registration(true, onBack);

    ClassLoader loader = bridgeType.getClassLoader();
    if (loader == null) {
      loader = mobileClass.getClassLoader();
    }
    Object proxy = Proxy.newProxyInstance(
        loader,
        new Class<?>[] {bridgeType},
        new BridgeInvocationHandler(registration));

    invokeRegistration(register, proxy);
    return registration;
  }

  static final class Registration {
    private final boolean available;
    private final Method onBack;
    private final AtomicReference<Object> handler = new AtomicReference<>();

    private Registration(boolean available, Method onBack) {
      this.available = available;
      this.onBack = onBack;
    }

    private static Registration unavailable() {
      return new Registration(false, null);
    }

    boolean isAvailable() {
      return available;
    }

    boolean hasHandler() {
      return handler.get() != null;
    }

    boolean onBack() {
      Object current = handler.get();
      if (current == null) {
        return false;
      }
      try {
        return (Boolean) onBack.invoke(current);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("cannot access Go BackHandler.onBack", e);
      } catch (InvocationTargetException e) {
        throw new IllegalStateException("Go BackHandler.onBack failed", e.getCause());
      }
    }

    private void setHandler(Object value) {
      if (value != null && !onBack.getDeclaringClass().isInstance(value)) {
        throw new IllegalStateException(
            "BackBridge received incompatible handler " + value.getClass().getName());
      }
      handler.set(value);
    }
  }

  private static Method requireOnlyBridgeMethod(Class<?> bridgeType) {
    Method[] methods = bridgeType.getMethods();
    if (methods.length != 1) {
      throw new IllegalStateException(
          "optional gomobile BackBridge " + bridgeType.getName()
              + " must expose exactly setHandler(BackHandler)");
    }

    Method method = methods[0];
    if (!method.getName().equals("setHandler")
        || method.getReturnType() != void.class
        || method.getParameterCount() != 1
        || !method.getParameterTypes()[0].isInterface()) {
      throw incompatible(method, "expected abstract void setHandler(BackHandler)");
    }
    requireAbstractInstance(method, "BackBridge methods must be abstract instance methods");
    requireNoCheckedExceptions(method, "BackBridge.setHandler must not throw");
    return method;
  }

  private static Method requireHandlerMethod(Class<?> handlerType) {
    Method[] methods = handlerType.getMethods();
    if (methods.length != 1) {
      throw new IllegalStateException(
          "optional gomobile BackHandler " + handlerType.getName()
              + " must expose exactly onBack()");
    }

    Method method = methods[0];
    if (!method.getName().equals("onBack")
        || method.getReturnType() != boolean.class
        || method.getParameterCount() != 0) {
      throw incompatible(method, "expected abstract boolean onBack()");
    }
    requireAbstractInstance(method, "BackHandler methods must be abstract instance methods");
    requireNoCheckedExceptions(method, "BackHandler.onBack must not throw");
    return method;
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

  private static void requireStatic(Method method) {
    if (!Modifier.isStatic(method.getModifiers())) {
      throw incompatible(method, "export must be static");
    }
  }

  private static void requireAbstractInstance(Method method, String expectation) {
    if (Modifier.isStatic(method.getModifiers()) || !Modifier.isAbstract(method.getModifiers())) {
      throw incompatible(method, expectation);
    }
  }

  private static void requireNoCheckedExceptions(Method method, String expectation) {
    if (method.getExceptionTypes().length != 0) {
      throw incompatible(method, expectation);
    }
  }

  private static void invokeRegistration(Method register, Object proxy) {
    try {
      register.invoke(null, proxy);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("cannot access BackBridge registration", e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException("BackBridge registration failed", e.getCause());
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

  private static final class BridgeInvocationHandler implements InvocationHandler {
    private final Registration registration;

    BridgeInvocationHandler(Registration registration) {
      this.registration = registration;
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
            return "OptionalBackBridge.BackBridgeProxy";
          default:
            throw new IllegalStateException("unsupported Object method " + method.getName());
        }
      }

      if (method.getName().equals("setHandler")) {
        registration.setHandler(args[0]);
        return null;
      }
      throw new IllegalStateException("unsupported BackBridge method " + method.getName());
    }
  }
}
