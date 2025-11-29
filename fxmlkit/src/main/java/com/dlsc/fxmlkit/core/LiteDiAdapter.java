package com.dlsc.fxmlkit.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight dependency injection implementation.
 *
 * <p>This is a simple, reflection-based DI adapter suitable for desktop applications
 * and JPro multi-user scenarios.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Type-based instance binding</li>
 *   <li>Constructor injection with dependency resolution</li>
 *   <li>Field and method injection via @Inject</li>
 *   <li>Thread-safe via ConcurrentHashMap</li>
 *   <li>Idempotent member injection</li>
 * </ul>
 *
 * <h2>Injection Contract</h2>
 * <p>This implementation follows the DiAdapter two-phase contract:
 * <ol>
 *   <li><b>getInstance():</b> Constructor injection only (no field/method injection)</li>
 *   <li><b>injectMembers():</b> Field and method injection, idempotent per object</li>
 * </ol>
 *
 * <h2>Annotation Support</h2>
 * <p>Recognizes @Inject from multiple frameworks:
 * <ul>
 *   <li>javax.inject.Inject</li>
 *   <li>jakarta.inject.Inject</li>
 *   <li>com.google.inject.Inject</li>
 * </ul>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>No circular dependency support</li>
 *   <li>No scope mechanism (all bindings are singleton-style)</li>
 *   <li>No qualifier support (only type-based binding)</li>
 *   <li>Method injection idempotency depends on method design</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Desktop Mode (Tier 2)</h3>
 * <pre>{@code
 * // One-time setup
 * LiteDiAdapter injector = new LiteDiAdapter();
 * injector.bindInstance(UserService.class, new UserService());
 * injector.bindInstance(ConfigService.class, new ConfigService());
 * FxmlKit.setDiAdapter(injector);
 *
 * // Then use normally
 * MainView view = new MainView();
 * Parent root = view;  // Ready to use
 * }</pre>
 *
 * <h3>JPro Multi-User Mode (Tier 3)</h3>
 * <pre>{@code
 * // Each user gets their own injector
 * LiteDiAdapter injector = new LiteDiAdapter();
 * injector.bindInstance(User.class, currentUser);
 * injector.bindInstance(UserService.class, new UserService(currentUser));
 * injector.bindInstance(DiAdapter.class, injector);  // Bind self for injection
 *
 * // User view with constructor injection
 * public class MainView extends FxmlView<MainController> {
 *     @Inject
 *     public MainView(DiAdapter diAdapter) {
 *         super(diAdapter);
 *     }
 * }
 *
 * // Create view with user-specific dependencies
 * MainView view = injector.getInstance(MainView.class);
 * Parent root = view;  // Ready to use
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 *   <li>Instance registry is thread-safe (ConcurrentHashMap)</li>
 *   <li>Injection tracking is thread-safe (identity-based concurrent set)</li>
 *   <li>Static reflection caches are thread-safe</li>
 * </ul>
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
     * <p><b>Note:</b> This method only performs constructor injection. Field and
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
     * <p><b>Injection Order:</b>
     * <ol>
     *   <li>Field injection (only if field is null)</li>
     *   <li>Method injection (always called)</li>
     * </ol>
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
     * <p>This method:
     * <ol>
     *   <li>Selects the appropriate constructor (prefers @Inject-annotated)</li>
     *   <li>Resolves all constructor parameters via {@link #getInstance(Class)}</li>
     *   <li>Invokes the constructor to create the instance</li>
     * </ol>
     *
     * @param <T>  the type parameter
     * @param type the class to instantiate
     * @return the new instance (never null)
     * @throws RuntimeException if creation fails
     */
    private <T> T createInstance(Class<T> type) {
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

        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + type.getName(), e);
        }
    }

    /**
     * Clears all internal state of this injector.
     *
     * <p>This includes:
     * <ul>
     *   <li>All manually bound instances</li>
     *   <li>Injected object tracking set</li>
     * </ul>
     *
     * <p><b>Note:</b> This does NOT clear static reflection caches in
     * InjectionUtils, as those are shared globally.
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