package com.dlsc.fxmlkit.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight dependency injection implementation.
 *
 * <p>This is a simple, reflection-based DI adapter suitable for desktop applications
 * and JPro multi-user scenarios. Provides type-based instance binding, constructor
 * injection with dependency resolution, and field/method injection via @Inject.
 * Thread-safe via ConcurrentHashMap with idempotent member injection.
 *
 * <p>Follows the DiAdapter two-phase contract: {@code getInstance} performs constructor
 * injection only, {@code injectMembers} performs field and method injection (idempotent
 * per object).
 *
 * <p>Recognizes @Inject from javax.inject, jakarta.inject, and com.google.inject.
 *
 * <p>Limitations: no scope mechanism (all bindings are singleton-style), no qualifier
 * support (only type-based binding), method injection idempotency depends on method design.
 *
 * <p>Desktop mode (Tier 2):
 * <pre>{@code
 * LiteDiAdapter injector = new LiteDiAdapter();
 * injector.bindInstance(UserService.class, new UserService());
 * FxmlKit.setDiAdapter(injector);
 *
 * MainView view = new MainView();
 * }</pre>
 *
 * <p>JPro multi-user mode (Tier 3):
 * <pre>{@code
 * LiteDiAdapter injector = new LiteDiAdapter();
 * injector.bindInstance(User.class, currentUser);
 * injector.bindInstance(DiAdapter.class, injector);
 *
 * public class MainView extends FxmlView<MainController> {
 *     @Inject
 *     public MainView(DiAdapter diAdapter) {
 *         super(diAdapter);
 *     }
 * }
 *
 * MainView view = injector.getInstance(MainView.class);
 * }</pre>
 *
 * <p>Thread-safe: instance registry uses ConcurrentHashMap, injection tracking uses
 * identity-based concurrent set, static reflection caches are thread-safe.
 *
 * @see BaseDiAdapter
 * @see DiAdapter
 */
public class LiteDiAdapter extends BaseDiAdapter {

    /**
     * Registry for manually bound singletons.
     */
    private final Map<Class<?>, Object> instances = new ConcurrentHashMap<>();

    /**
     * Tracks objects that have already been member-injected (for idempotency).
     * Uses identity-based comparison to ensure same object instance.
     */
    private final Set<Object> injectedObjects = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Tracks types currently being created in each thread to detect circular dependencies.
     * Uses LinkedHashSet to preserve insertion order for error messages.
     */
    private final ThreadLocal<Set<Class<?>>> creationStack = ThreadLocal.withInitial(LinkedHashSet::new);

    /**
     * Binds a specific instance to a type (singleton-style binding).
     *
     * <p>This method registers a pre-created instance that will be returned
     * whenever {@link #getInstance(Class)} is called with the specified type.
     *
     * @param type     the class type to bind (must not be null)
     * @param instance the instance to bind (must not be null)
     * @param <T>      the type parameter
     * @throws NullPointerException if either argument is null
     */
    public <T> void bindInstance(Class<T> type, T instance) {
        instances.put(
            Objects.requireNonNull(type, "Type cannot be null"),
            Objects.requireNonNull(instance, "Instance cannot be null")
        );
    }

    /**
     * Checks whether a type has a bound instance.
     *
     * @param type the class type to check
     * @return true if an instance is bound for this type
     */
    public boolean isBound(Class<?> type) {
        return instances.containsKey(type);
    }

    /**
     * Creates an instance of the specified type.
     *
     * <p>If a bound instance exists, returns it immediately. Otherwise, creates
     * a new instance using constructor injection.
     *
     * <p>Note: This method only performs constructor injection. Field and
     * method injection must be done via {@link #injectMembers(Object)}.
     *
     * @param <T>  the type parameter
     * @param type the class to instantiate
     * @return the instance (never null)
     * @throws RuntimeException if instance creation fails
     */
    @Override
    protected <T> T doGetInstance(Class<T> type) {
        // Check for bound instance first
        @SuppressWarnings("unchecked")
        T boundInstance = (T) instances.get(type);
        if (boundInstance != null) {
            return boundInstance;
        }

        // Create new instance using constructor injection
        return createInstance(type);
    }

    /**
     * Injects dependencies into an existing object.
     *
     * <p>This method performs field and method injection for all @Inject-annotated
     * members. It is idempotent - calling it multiple times on the same object
     * has no additional effect.
     *
     * <p>Injection order: field injection (only if field is null), then method
     * injection (always called).
     *
     * @param target the object to inject (never null)
     * @throws RuntimeException if injection fails
     */
    @Override
    protected void doInjectMembers(Object target) {
        // Idempotent check - skip if already injected
        if (injectedObjects.contains(target)) {
            return;
        }

        Class<?> type = target.getClass();

        try {
            // Step 1: Field injection
            List<Field> injectFields = InjectionUtils.findInjectFields(type);
            for (Field field : injectFields) {
                field.setAccessible(true);

                // Only inject if field is null (avoids overwriting existing values)
                if (field.get(target) == null) {
                    Object value = getInstance(field.getType());
                    field.set(target, value);
                }
            }

            // Step 2: Method injection  (invoked once due to idempotency check above)
            List<Method> injectMethods = InjectionUtils.findInjectMethods(type);
            for (Method method : injectMethods) {
                method.setAccessible(true);

                // Resolve all method parameters
                Class<?>[] paramTypes = method.getParameterTypes();
                Object[] args = new Object[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    args[i] = getInstance(paramTypes[i]);
                }

                // Invoke the injection method
                method.invoke(target, args);
            }

            // Mark as injected to ensure idempotency
            injectedObjects.add(target);

        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to inject members into " + target.getClass().getName(), e);
        }
    }

    /**
     * Creates a new instance using constructor injection.
     *
     * <p>Selects the appropriate constructor (prefers @Inject-annotated), resolves
     * all constructor parameters via {@link #getInstance(Class)}, then invokes the
     * constructor to create the instance.
     *
     * <p>Circular dependencies are detected and reported with a clear error message.
     *
     * @param <T>  the type parameter
     * @param type the class to instantiate
     * @return the new instance (never null)
     * @throws RuntimeException if creation fails or circular dependency detected
     */
    private <T> T createInstance(Class<T> type) {
        Set<Class<?>> stack = creationStack.get();

        // Check for circular dependency
        if (!stack.add(type)) {
            String cycle = formatCycle(stack, type);
            throw new IllegalStateException("Circular dependency detected: " + cycle);
        }

        try {
            Constructor<T> constructor = InjectionUtils.chooseConstructor(type);
            constructor.setAccessible(true);

            // Resolve constructor parameters
            Class<?>[] paramTypes = constructor.getParameterTypes();
            Object[] args = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                args[i] = getInstance(paramTypes[i]);
            }

            // Create instance
            return constructor.newInstance(args);

        } catch (IllegalStateException e) {
            // Re-throw circular dependency errors as-is
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + type.getName(), e);
        } finally {
            stack.remove(type);
            if (stack.isEmpty()) {
                creationStack.remove();
            }
        }
    }

    /**
     * Formats circular dependency chain for error message.
     */
    private String formatCycle(Set<Class<?>> stack, Class<?> type) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> cls : stack) {
            sb.append(cls.getSimpleName()).append(" -> ");
        }
        sb.append(type.getSimpleName());
        return sb.toString();
    }

    /**
     * Clears all internal state of this injector.
     *
     * <p>This includes all manually bound instances and injected object tracking set.
     *
     * <p>Note: This does NOT clear static reflection caches in InjectionUtils, as
     * those are shared globally.
     *
     * <p>Typically used for testing or resetting injector state.
     */
    public void clear() {
        instances.clear();
        injectedObjects.clear();
    }

    /**
     * Returns the number of manually bound instances.
     *
     * @return the number of entries in the instance registry
     */
    public int getInstanceCount() {
        return instances.size();
    }

    /**
     * Returns the number of objects that have been member-injected.
     *
     * @return the number of injected objects
     */
    public int getInjectedCount() {
        return injectedObjects.size();
    }

    @Override
    public String toString() {
        return "LiteDiAdapter{instances=" + instances.size() + ", injected=" + injectedObjects.size() + "}";
    }
}