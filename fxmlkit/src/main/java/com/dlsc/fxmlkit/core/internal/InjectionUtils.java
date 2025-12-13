package com.dlsc.fxmlkit.core.internal;

import com.dlsc.fxmlkit.annotations.PostInject;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection utilities for dependency injection support.
 *
 * <p>This class provides framework-neutral reflection helpers to detect common DI
 * annotations and locate injectable constructors, fields, methods, and lifecycle
 * callbacks. All results are cached for performance and are thread-safe.
 *
 * <h2>Supported Annotations</h2>
 * <ul>
 *   <li><b>@Inject:</b> javax.inject.Inject, jakarta.inject.Inject, com.google.inject.Inject</li>
 *   <li><b>@Named:</b> javax.inject.Named, jakarta.inject.Named, com.google.inject.name.Named</li>
 *   <li><b>@Singleton:</b> javax.inject.Singleton, jakarta.inject.Singleton, com.google.inject.Singleton</li>
 *   <li><b>@PostInject:</b> com.dlsc.fxmlkit.annotations.PostInject</li>
 * </ul>
 *
 * <h2>Constructor Selection Algorithm</h2>
 * <p>When selecting a constructor for dependency injection, the following priority is used:
 * <ol>
 *   <li>Constructor annotated with @Inject (highest priority)</li>
 *   <li>No-argument constructor (default choice)</li>
 *   <li>Constructor with fewest parameters (fallback)</li>
 * </ol>
 *
 * <h2>Lifecycle Method Ordering</h2>
 * <p>@PostInject methods are invoked in a specific order:
 * <ol>
 *   <li>Superclass methods before subclass methods (parent-first)</li>
 *   <li>Within each class, methods sorted alphabetically by name</li>
 * </ol>
 *
 * <h2>Caching Strategy</h2>
 * <p>All reflection results are cached using ConcurrentHashMap:
 * <ul>
 *   <li>Injectable fields per class</li>
 *   <li>Injectable methods per class</li>
 *   <li>PostInject methods per class</li>
 *   <li>Chosen constructor per class</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All methods are thread-safe. Caches use ConcurrentHashMap and returned
 * collections are immutable. Safe for use in concurrent environments.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Check if field has @Inject
 * Field field = MyClass.class.getDeclaredField("userService");
 * if (InjectionUtils.isInject(field)) {
 *     // Inject dependency
 * }
 *
 * // Find all injectable fields
 * List<Field> fields = InjectionUtils.findInjectFields(MyClass.class);
 * for (Field f : fields) {
 *     f.setAccessible(true);
 *     f.set(instance, getDependency(f.getType()));
 * }
 *
 * // Choose best constructor
 * Constructor<MyClass> ctor = InjectionUtils.chooseConstructor(MyClass.class);
 * MyClass instance = ctor.newInstance(args);
 *
 * // Invoke lifecycle methods
 * List<Method> postMethods = InjectionUtils.findPostMethods(MyClass.class);
 * for (Method m : postMethods) {
 *     m.invoke(instance);
 * }
 * }</pre>
 */
public final class InjectionUtils {

    // Fully qualified class names for @Inject annotation variants
    private static final String JX_INJECT = "javax.inject.Inject";
    private static final String JK_INJECT = "jakarta.inject.Inject";
    private static final String GUICE_INJECT = "com.google.inject.Inject";

    // Fully qualified class names for @Named annotation variants
    private static final String JX_NAMED = "javax.inject.Named";
    private static final String JK_NAMED = "jakarta.inject.Named";
    private static final String GUICE_NAMED = "com.google.inject.name.Named";

    // Fully qualified class names for @Singleton annotation variants
    private static final String JX_SINGLETON = "javax.inject.Singleton";
    private static final String JK_SINGLETON = "jakarta.inject.Singleton";
    private static final String GUICE_SINGLETON = "com.google.inject.Singleton";

    /**
     * Cache for injectable fields per class.
     */
    private static final Map<Class<?>, List<Field>> CACHE_INJECT_FIELDS = new ConcurrentHashMap<>();

    /**
     * Cache for injectable methods per class.
     */
    private static final Map<Class<?>, List<Method>> CACHE_INJECT_METHODS = new ConcurrentHashMap<>();

    /**
     * Cache for @PostInject methods per class.
     */
    private static final Map<Class<?>, List<Method>> CACHE_POST_METHODS = new ConcurrentHashMap<>();

    /**
     * Cache for chosen constructor per class.
     */
    private static final Map<Class<?>, Constructor<?>> CACHE_CTOR = new ConcurrentHashMap<>();

    private InjectionUtils() {
    }

    /**
     * Checks if an element has a specific annotation by fully qualified class name.
     *
     * <p>This method allows checking for annotations without compile-time dependencies
     * on the annotation classes. Useful for supporting multiple DI frameworks.
     *
     * @param e    the annotated element to check
     * @param fqcn the fully qualified class name of the annotation
     * @return true if the element has the annotation, false otherwise
     */
    private static boolean hasAnnotation(AnnotatedElement e, String fqcn) {
        for (Annotation a : e.getAnnotations()) {
            if (a.annotationType().getName().equals(fqcn)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an element is annotated with any variant of @Inject.
     *
     * <p>Supports:
     * <ul>
     *   <li>javax.inject.Inject</li>
     *   <li>jakarta.inject.Inject</li>
     *   <li>com.google.inject.Inject</li>
     * </ul>
     *
     * @param e the annotated element (field, method, constructor, etc.)
     * @return true if annotated with any @Inject variant
     */
    public static boolean isInject(AnnotatedElement e) {
        return hasAnnotation(e, JX_INJECT) || hasAnnotation(e, JK_INJECT) || hasAnnotation(e, GUICE_INJECT);
    }

    /**
     * Checks if an element is annotated with any variant of @Named.
     *
     * <p>Supports:
     * <ul>
     *   <li>javax.inject.Named</li>
     *   <li>jakarta.inject.Named</li>
     *   <li>com.google.inject.name.Named</li>
     * </ul>
     *
     * @param e the annotated element
     * @return true if annotated with any @Named variant
     */
    public static boolean isNamed(AnnotatedElement e) {
        return hasAnnotation(e, JX_NAMED) || hasAnnotation(e, JK_NAMED) || hasAnnotation(e, GUICE_NAMED);
    }

    /**
     * Extracts the value from a @Named annotation.
     *
     * <p>Uses reflection to call the {@code value()} method of the annotation.
     *
     * @param e the annotated element
     * @return Optional containing the name value, or empty if not present
     */
    public static Optional<String> getNamedValue(AnnotatedElement e) {
        for (Annotation a : e.getAnnotations()) {
            String n = a.annotationType().getName();
            if (n.equals(JX_NAMED) || n.equals(JK_NAMED) || n.equals(GUICE_NAMED)) {
                try {
                    Method m = a.annotationType().getMethod("value");
                    return Optional.ofNullable((String) m.invoke(a));
                } catch (Exception ignore) {
                    // Ignore reflection failures
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if an element is annotated with @PostInject.
     *
     * <p><b>Note:</b> Only supports FxmlKit's custom {@link PostInject} annotation.
     * Standard @PostConstruct from javax. Annotation is NOT supported here.
     * DI frameworks (Guice, Spring, etc.) should handle @PostConstruct themselves.
     *
     * @param e the annotated element
     * @return true if annotated with @PostInject
     */
    public static boolean isPostLifecycle(AnnotatedElement e) {
        // Only supports @PostInject (FxmlKit's custom annotation)
        // @PostConstruct should be handled by DI frameworks, not FxmlKit
        return e.isAnnotationPresent(PostInject.class);
    }

    /**
     * Checks if a class is annotated with any variant of @Singleton.
     *
     * <p>Supports:
     * <ul>
     *   <li>javax.inject.Singleton</li>
     *   <li>jakarta.inject.Singleton</li>
     *   <li>com.google.inject.Singleton</li>
     * </ul>
     *
     * @param type the class to check
     * @return true if annotated with any @Singleton variant
     */
    public static boolean isSingletonAnnotated(Class<?> type) {
        for (Annotation a : type.getAnnotations()) {
            String n = a.annotationType().getName();
            if (n.equals(JX_SINGLETON) || n.equals(JK_SINGLETON) || n.equals(GUICE_SINGLETON)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Chooses the best constructor for dependency injection.
     *
     * <p>Selection algorithm (in priority order):
     * <ol>
     *   <li>Constructor annotated with @Inject</li>
     *   <li>No-argument constructor</li>
     *   <li>Constructor with the fewest parameters</li>
     * </ol>
     *
     * <p>Results are cached per class for performance.
     * The chosen constructor is made accessible via {@link Constructor#setAccessible(boolean)}.
     *
     * @param <T>  the type parameter
     * @param type the class to find constructor for
     * @return the chosen constructor (never null)
     * @throws IllegalStateException if no suitable constructor found
     */
    @SuppressWarnings("unchecked")
    public static <T> Constructor<T> chooseConstructor(Class<T> type) {
        return (Constructor<T>) CACHE_CTOR.computeIfAbsent(type, t -> {
            Constructor<?>[] all = t.getDeclaredConstructors();

            // Priority 1: Constructor with @Inject
            for (Constructor<?> c : all) {
                if (isInject(c)) {
                    c.setAccessible(true);
                    return c;
                }
            }

            // Priority 2: No-arg constructor
            try {
                Constructor<?> c = t.getDeclaredConstructor();
                c.setAccessible(true);
                return c;
            } catch (NoSuchMethodException ignore) {
                // Ignore and proceed to next priority
            }

            // Priority 3: Constructor with the fewest parameters
            Constructor<?> min = Arrays.stream(all)
                    .min(Comparator.comparingInt(Constructor::getParameterCount))
                    .orElseThrow(() -> new IllegalStateException("No constructor for " + t.getName()));
            min.setAccessible(true);
            return min;
        });
    }

    /**
     * Finds all fields annotated with @Inject in a class hierarchy.
     *
     * <p>Searches through the entire class hierarchy from the given type up to
     * (but not including) {@link Object}. Fields from superclasses are included.
     *
     * <p>Results are cached per class. Returned list is unmodifiable.
     * All fields are made accessible via {@link Field#setAccessible(boolean)}.
     *
     * @param type the class to scan
     * @return unmodifiable list of injectable fields (maybe empty, never null)
     */
    public static List<Field> findInjectFields(Class<?> type) {
        return CACHE_INJECT_FIELDS.computeIfAbsent(type, t -> {
            List<Field> out = new ArrayList<>();

            // Traverse class hierarchy
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (isInject(f)) {
                        f.setAccessible(true);
                        out.add(f);
                    }
                }
            }
            return Collections.unmodifiableList(out);
        });
    }

    /**
     * Finds all methods annotated with @Inject in a class hierarchy.
     *
     * <p>Searches through the entire class hierarchy from the given type up to
     * (but not including) {@link Object}. Methods from superclasses are included.
     *
     * <p>Results are cached per class. Returned list is unmodifiable.
     * All methods are made accessible via {@link Method#setAccessible(boolean)}.
     *
     * @param type the class to scan
     * @return unmodifiable list of injectable methods (maybe empty, never null)
     */
    public static List<Method> findInjectMethods(Class<?> type) {
        return CACHE_INJECT_METHODS.computeIfAbsent(type, t -> {
            List<Method> out = new ArrayList<>();
            // Traverse class hierarchy
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (isInject(m)) {
                        m.setAccessible(true);
                        out.add(m);
                    }
                }
            }
            return Collections.unmodifiableList(out);
        });
    }

    /**
     * Finds all @PostInject lifecycle methods in a class hierarchy.
     *
     * <p>Methods are returned in a specific order to ensure correct execution:
     * <ol>
     *   <li>Superclass methods before subclass methods (parent-first order)</li>
     *   <li>Within each class, methods are sorted alphabetically by name for stability</li>
     * </ol>
     *
     * <p>Only parameterless methods are included.
     * Results are cached per class. Returned list is unmodifiable.
     * All methods are made accessible via {@link Method#setAccessible(boolean)}.
     *
     * <p><b>Example inheritance order:</b>
     * <pre>{@code
     * class Parent {
     *     @PostInject void initParent() { } // Called 1st
     * }
     * class Child extends Parent {
     *     @PostInject void initChild() { }  // Called 2nd
     * }
     * }</pre>
     *
     * @param type the class to scan
     * @return unmodifiable list of lifecycle methods in execution order (may be empty, never null)
     */
    public static List<Method> findPostMethods(Class<?> type) {
        return CACHE_POST_METHODS.computeIfAbsent(type, t -> {
            // Build inheritance chain: Object <- ... <- Super <- This
            List<Class<?>> chain = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                chain.add(c);
            }
            // Parent classes first
            Collections.reverse(chain);

            List<Method> out = new ArrayList<>();
            // Process each class in the hierarchy
            for (Class<?> c : chain) {
                List<Method> layer = new ArrayList<>();
                for (Method m : c.getDeclaredMethods()) {
                    // Only include parameterless @PostInject methods
                    if (isPostLifecycle(m) && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        layer.add(m);
                    }
                }
                // Sort methods in this class by name for stable ordering
                layer.sort(Comparator.comparing(Method::getName));
                out.addAll(layer);
            }
            return Collections.unmodifiableList(out);
        });
    }
}
