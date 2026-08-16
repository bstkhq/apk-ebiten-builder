package @@APP_ID@@;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapts an optional gomobile file picker contract without linking generated
 * application types into the reusable Android template.
 *
 * <p>The canonical Go methods are documented in
 * {@code github.com/bstkhq/apk-ebiten-builder/bridge}. Gomobile emits only
 * types declared by the application's {@code mobile} package, so its local
 * adapter is:</p>
 *
 * <pre>{@code
 * import "github.com/bstkhq/apk-ebiten-builder/bridge"
 *
 * var picker = bridge.NewFilePickerClient()
 *
 * type FilePickerHandler interface { bridge.FilePickerHandler }
 * type FilePickerBridge interface {
 *     bridge.FilePickerOpener
 *     SetHandler(FilePickerHandler)
 * }
 * func RegisterFilePickerBridge(value FilePickerBridge) {
 *     picker.Register(value)
 *     value.SetHandler(picker)
 * }
 * }</pre>
 *
 * <p>Applications without that export keep their existing behavior. A named
 * but incompatible export is a contract error.</p>
 */
final class OptionalFilePickerBridge {
  interface Services {
    void open(String mimeType);

    void close();
  }

  interface ResultSink {
    void onResult(String path, String message);
  }

  interface ServicesFactory {
    Services create(ResultSink resultSink);
  }

  private OptionalFilePickerBridge() {}

  static Registration register(Class<?> mobileClass, ServicesFactory factory) {
    requireNonNull(mobileClass, "mobileClass");
    requireNonNull(factory, "factory");

    Method register = optionalNamedMethod(mobileClass, "registerFilePickerBridge");
    if (register == null) {
      return Registration.unavailable();
    }

    requireStatic(register);
    if (register.getReturnType() != void.class || register.getParameterCount() != 1) {
      throw incompatible(
          register,
          "expected static void registerFilePickerBridge(FilePickerBridge)");
    }

    Class<?> bridgeType = register.getParameterTypes()[0];
    if (!bridgeType.isInterface()) {
      throw incompatible(register, "FilePickerBridge parameter must be an interface");
    }

    BridgeMethods bridgeMethods = requireBridgeMethods(bridgeType);
    Class<?> handlerType = bridgeMethods.setHandler.getParameterTypes()[0];
    Method onResult = requireHandlerMethod(handlerType);
    Registration registration = new Registration(true, onResult);

    Services services = factory.create(registration::onResult);
    requireNonNull(services, "file picker services");
    registration.attach(services);

    ClassLoader loader = bridgeType.getClassLoader();
    if (loader == null) {
      loader = mobileClass.getClassLoader();
    }
    Object proxy = Proxy.newProxyInstance(
        loader,
        new Class<?>[] {bridgeType},
        new BridgeInvocationHandler(registration));

    try {
      invokeRegistration(register, proxy);
    } catch (RuntimeException e) {
      registration.close();
      throw e;
    }
    return registration;
  }

  static final class Registration {
    private final boolean available;
    private final Method onResult;
    private final Object lock = new Object();
    private Object handler;
    private Services services;
    private boolean closed;

    private Registration(boolean available, Method onResult) {
      this.available = available;
      this.onResult = onResult;
    }

    private static Registration unavailable() {
      return new Registration(false, null);
    }

    boolean isAvailable() {
      return available;
    }

    boolean hasHandler() {
      synchronized (lock) {
        return !closed && handler != null;
      }
    }

    void close() {
      final Services current;
      synchronized (lock) {
        if (closed) {
          return;
        }
        closed = true;
        handler = null;
        current = services;
        services = null;
      }
      if (current != null) {
        current.close();
      }
    }

    private void attach(Services value) {
      synchronized (lock) {
        if (closed) {
          value.close();
          return;
        }
        services = value;
      }
    }

    private void open(String mimeType) {
      final Services current;
      synchronized (lock) {
        current = closed ? null : services;
      }
      if (current != null) {
        current.open(mimeType);
      }
    }

    private void setHandler(Object value) {
      if (value != null && !onResult.getDeclaringClass().isInstance(value)) {
        throw new IllegalStateException(
            "FilePickerBridge received incompatible handler " + value.getClass().getName());
      }
      synchronized (lock) {
        if (!closed) {
          handler = value;
        }
      }
    }

    private void onResult(String path, String message) {
      requireNonNull(path, "path");
      requireNonNull(message, "message");
      final Object current;
      synchronized (lock) {
        current = closed ? null : handler;
      }
      if (current == null) {
        return;
      }
      try {
        onResult.invoke(current, path, message);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("cannot access Go FilePickerHandler.onResult", e);
      } catch (InvocationTargetException e) {
        throw new IllegalStateException(
            "Go FilePickerHandler.onResult failed",
            e.getCause());
      }
    }
  }

  private static BridgeMethods requireBridgeMethods(Class<?> bridgeType) {
    Method[] methods = bridgeType.getMethods();
    if (methods.length != 2) {
      throw new IllegalStateException(
          "optional gomobile FilePickerBridge " + bridgeType.getName()
              + " must expose exactly setHandler(FilePickerHandler) and open(String)");
    }

    Method setHandler = null;
    Method open = null;
    for (Method method : methods) {
      if (method.getName().equals("setHandler")) {
        setHandler = method;
      } else if (method.getName().equals("open")) {
        open = method;
      }
    }

    if (setHandler == null) {
      throw new IllegalStateException(
          "optional gomobile FilePickerBridge " + bridgeType.getName()
              + " is missing setHandler(FilePickerHandler)");
    }
    if (setHandler.getReturnType() != void.class
        || setHandler.getParameterCount() != 1
        || !setHandler.getParameterTypes()[0].isInterface()) {
      throw incompatible(setHandler, "expected abstract void setHandler(FilePickerHandler)");
    }
    requireAbstractInstance(setHandler, "FilePickerBridge methods must be abstract instance methods");
    requireNoCheckedExceptions(setHandler, "FilePickerBridge.setHandler must not throw");

    if (open == null) {
      throw new IllegalStateException(
          "optional gomobile FilePickerBridge " + bridgeType.getName()
              + " is missing open(String)");
    }
    if (open.getReturnType() != void.class
        || open.getParameterCount() != 1
        || open.getParameterTypes()[0] != String.class) {
      throw incompatible(open, "expected abstract void open(String)");
    }
    requireAbstractInstance(open, "FilePickerBridge methods must be abstract instance methods");
    requireNoCheckedExceptions(open, "FilePickerBridge.open must not throw");
    return new BridgeMethods(setHandler);
  }

  private static Method requireHandlerMethod(Class<?> handlerType) {
    Method[] methods = handlerType.getMethods();
    if (methods.length != 1) {
      throw new IllegalStateException(
          "optional gomobile FilePickerHandler " + handlerType.getName()
              + " must expose exactly onResult(String, String)");
    }

    Method method = methods[0];
    if (!method.getName().equals("onResult")
        || method.getReturnType() != void.class
        || method.getParameterCount() != 2
        || method.getParameterTypes()[0] != String.class
        || method.getParameterTypes()[1] != String.class) {
      throw incompatible(method, "expected abstract void onResult(String, String)");
    }
    requireAbstractInstance(method, "FilePickerHandler methods must be abstract instance methods");
    requireNoCheckedExceptions(method, "FilePickerHandler.onResult must not throw");
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
      throw new IllegalStateException("cannot access FilePickerBridge registration", e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException("FilePickerBridge registration failed", e.getCause());
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

  private static final class BridgeMethods {
    final Method setHandler;

    BridgeMethods(Method setHandler) {
      this.setHandler = setHandler;
    }
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
            return "OptionalFilePickerBridge.FilePickerBridgeProxy";
          default:
            throw new IllegalStateException("unsupported Object method " + method.getName());
        }
      }

      switch (method.getName()) {
        case "setHandler":
          registration.setHandler(args[0]);
          return null;
        case "open":
          registration.open((String) args[0]);
          return null;
        default:
          throw new IllegalStateException(
              "unsupported FilePickerBridge method " + method.getName());
      }
    }
  }
}
