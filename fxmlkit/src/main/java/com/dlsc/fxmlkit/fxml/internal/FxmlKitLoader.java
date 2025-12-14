package com.dlsc.fxmlkit.fxml.internal;

import com.dlsc.fxmlkit.fxml.FxmlKit;
import com.dlsc.fxmlkit.fxml.FxmlView;
import com.dlsc.fxmlkit.fxml.FxmlViewProvider;
import javafx.fxml.FXMLLoader;
import javafx.fxml.LoadListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.util.Callback;
import com.dlsc.fxmlkit.annotations.FxmlObject;
import com.dlsc.fxmlkit.annotations.SkipInjection;
import com.dlsc.fxmlkit.di.DiAdapter;
import com.dlsc.fxmlkit.di.internal.InjectionUtils;
import com.dlsc.fxmlkit.hotreload.HotReloadManager;
import com.dlsc.fxmlkit.di.FxmlInjectionPolicy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Internal FXML loader implementation for FxmlKit.
 *
 * <p>This class provides the core FXML loading logic with integrated dependency injection
 * and lifecycle management. It uses JavaFX's {@link LoadListener} to intercept all objects
 * created during FXML parsing and applies dependency injection according to configured policies.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Automatic dependency injection for controllers and FXML-created objects</li>
 *   <li>Smart FXML file resolution (supports ClassName.fxml and ClassName without View suffix)</li>
 *   <li>Automatic stylesheet attachment (.bss and .css)</li>
 *   <li>Configurable node injection policies</li>
 *   <li>@PostInject lifecycle callback support</li>
 *   <li>Thread-safe URL caching for performance</li>
 *   <li>Per-load state management (no static pollution)</li>
 *   <li>Development mode: load FXML directly from source directory</li>
 * </ul>
 *
 * <h2>FXML File Resolution</h2>
 * <p>Attempts to resolve FXML files in the following order:
 * <ol>
 *   <li>{@code ClassName.fxml} (e.g., {@code UserView.fxml})</li>
 *   <li>{@code ClassNameWithoutViewSuffix.fxml} (e.g., {@code User.fxml} for {@code UserView})</li>
 * </ol>
 *
 * <h2>Dependency Injection Flow</h2>
 * <ol>
 *   <li>FXML file is loaded using {@link FXMLLoader}</li>
 *   <li>{@link LoadListener} collects all created objects</li>
 *   <li>Controller is injected in controllerFactory (unconditionally)</li>
 *   <li>Other objects are injected based on {@link FxmlInjectionPolicy}</li>
 *   <li>@PostInject methods are invoked in parent-first order</li>
 * </ol>
 *
 * <h2>Injection Strategy</h2>
 * <ul>
 *   <li><b>Controllers:</b> Identity-based deduplication to prevent duplicate @PostInject calls</li>
 *   <li><b>Non-controllers:</b> Direct injection, relying on DI framework idempotency</li>
 *   <li><b>Idempotency:</b> Trusts DI framework's built-in idempotency for member injection</li>
 * </ul>
 *
 * <h2>Stylesheet Attachment</h2>
 * <p>When {@link FxmlKit#isAutoAttachStyles()} is enabled, automatically looks for:
 * <ol>
 *   <li>{@code *.bss} (binary stylesheet - higher priority)</li>
 *   <li>{@code *.css} (text stylesheet - fallback)</li>
 * </ol>
 *
 * <h2>Performance Optimizations</h2>
 * <ul>
 *   <li>FXML URL resolution results are cached globally</li>
 *   <li>Reflection results are cached by {@link InjectionUtils}</li>
 *   <li>Identity-based sets for object tracking (no equals/hashCode overhead)</li>
 *   <li>Early filtering via configurable skip package prefixes</li>
 *   <li>Unconditional injection relying on framework idempotency</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>The URL cache is thread-safe using {@link ConcurrentHashMap}. Each load operation
 * maintains its own context (no shared mutable state), making concurrent loads safe.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Simple load
 * LiteDiAdapter injector = new LiteDiAdapter();
 * Parent root = FxmlKitLoader.load(injector, UserView.class);
 *
 * // Load with controller and resources
 * ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages");
 * LoadResult<UserController> result = FxmlKitLoader.loadWithController(
 *     injector, UserView.class, bundle
 * );
 * Parent root = result.view();
 * UserController controller = result.controller();
 * }</pre>
 *
 * <p><b>Note:</b> This class is internal to FxmlKit. Users should interact with
 * {@link FxmlViewProvider} or {@link FxmlKit} facade instead of calling this class directly.
 *
 * @see FxmlViewProvider
 * @see FxmlKit
 * @see DiAdapter
 */
public final class FxmlKitLoader {

    private static final Logger logger = Logger.getLogger(FxmlKitLoader.class.getName());

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private FxmlKitLoader() {
    }

    /**
     * Global cache for resolved FXML URLs.
     *
     * <p>Thread-safe cache mapping owner classes to their FXML file URLs.
     * This cache is shared globally as URL resolution is deterministic and
     * does not involve user-specific data.
     *
     * <p>Cache is never invalidated, assuming FXML file locations don't change
     * during application runtime.
     */
    private static final Map<Class<?>, URL> URL_CACHE = new ConcurrentHashMap<>();

    /**
     * Clears the FXML URL cache.
     *
     * <p>Useful in testing scenarios or when FXML files may have been moved
     * or redeployed. In normal application usage, this method is rarely needed.
     *
     * <p><b>Thread Safety:</b> Safe to call from any thread.
     */
    public static void clearCache() {
        URL_CACHE.clear();
        logger.log(Level.FINE, "FXML URL cache cleared");
    }

    /**
     * Resolves the actual URL to use for FXML loading.
     *
     * <p>In development mode (hot reload enabled), this method attempts to
     * resolve the FXML file from the source directory instead of the build
     * output directory. This enables:
     * <ul>
     *   <li>Direct editing of source files without sync</li>
     *   <li>Automatic resolution of relative paths (fx:include, stylesheets)</li>
     *   <li>Immediate reflection of changes on hot reload</li>
     * </ul>
     *
     * <p>Safety: If the source file doesn't exist (e.g., in production JAR),
     * this method falls back to the original classpath URL.
     *
     * @param classpathUrl the URL from classpath (may point to target/classes or JAR)
     * @return the actual URL to use (source file in dev mode, original otherwise)
     */
    static URL resolveActualUrl(URL classpathUrl) {
        if (classpathUrl == null) {
            return null;
        }

        // If hot reload is not enabled, use classpath URL directly
        if (!HotReloadManager.getInstance().isEnabled()) {
            return classpathUrl;
        }

        // Try to convert to source path
        String runtimeUri = classpathUrl.toExternalForm();
        Path sourcePath = HotReloadManager.getInstance().toSourcePath(runtimeUri);

        // If source exists, use it
        if (sourcePath != null && Files.exists(sourcePath)) {
            try {
                URL sourceUrl = sourcePath.toUri().toURL();
                logger.log(Level.FINE, "Using source file: {0}", sourcePath);
                return sourceUrl;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to create source URL, using classpath: {0}",
                        e.getMessage());
            }
        } else {
            // Development mode is enabled but source file not found
            // This is expected in production (JAR/exe), but worth logging in case of misconfiguration
            logger.log(Level.FINE, "Source file not found (normal in production), using classpath: {0}",
                    classpathUrl);
        }

        // Fallback to classpath URL
        return classpathUrl;
    }

    /**
     * Loads an FXML file and returns the root node.
     *
     * <p>This is the simplest loading method. FXML file is resolved based on
     * the owner class name, controller is created via dependency injection,
     * and all objects are injected according to the configured policy.
     *
     * @param di         the dependency injection adapter (must not be null)
     * @param ownerClass the class used for FXML file resolution (usually a View class)
     * @return the loaded root node (never null)
     * @throws UncheckedIOException     if FXML loading fails
     * @throws IllegalArgumentException if FXML file cannot be resolved
     * @see #load(DiAdapter, Class, ResourceBundle)
     */
    public static Parent load(DiAdapter di, Class<?> ownerClass) {
        return load(di, ownerClass, null);
    }

    /**
     * Loads an FXML file with resource bundle support.
     *
     * <p>Performs the following steps:
     * <ol>
     *   <li>Resolves FXML URL from owner class (with caching)</li>
     *   <li>In development mode, redirects to source file if available</li>
     *   <li>Creates {@link FXMLLoader} with DI-based controller factory</li>
     *   <li>Collects all FXML-created objects via {@link LoadListener}</li>
     *   <li>Injects dependencies into controller (priority) and other objects</li>
     *   <li>Invokes @PostInject lifecycle methods</li>
     *   <li>Attaches stylesheets if auto-attach is enabled</li>
     * </ol>
     *
     * <p><b>Controller Creation:</b> The controller is created by the DI adapter
     * when requested by FXMLLoader, allowing constructor injection.
     *
     * <p><b>Node Injection:</b> Whether non-controller nodes are injected depends
     * on the configured {@link FxmlInjectionPolicy}.
     *
     * <p><b>Injection Strategy:</b> Controllers receive identity-based deduplication,
     * while non-controller objects are injected unconditionally (trusting DI idempotency).
     *
     * @param di         the dependency injection adapter (must not be null)
     * @param ownerClass the class used for FXML file resolution (must not be null)
     * @param resources  optional resource bundle for internationalization (may be null)
     * @return the loaded root node (never null)
     * @throws UncheckedIOException     if FXML file cannot be read
     * @throws IllegalArgumentException if FXML file cannot be resolved
     * @throws RuntimeException         if dependency injection or lifecycle methods fail
     */
    public static Parent load(DiAdapter di, Class<?> ownerClass, ResourceBundle resources) {
        URL classpathUrl = URL_CACHE.computeIfAbsent(ownerClass, FxmlPathResolver::resolveFxmlUrl);
        URL url = resolveActualUrl(classpathUrl);

        Set<Object> trackedControllers = Collections.newSetFromMap(new IdentityHashMap<>());

        try {
            // Wrap controllerFactory to track ALL controllers (including from fx:include)
            Callback<Class<?>, Object> trackingFactory = type -> {
                // Phase 1: Create instance (constructor injection only)
                Object controller = di.getInstance(type);

                // Phase 2: Inject members BEFORE returning to JavaFX
                // Unconditional injection - trust DI framework's idempotency
                // This ensures Initializable.initialize() sees injected fields
                di.injectMembers(controller);

                trackedControllers.add(controller);
                logger.log(Level.FINE, "Controller created and injected: {0}",
                        controller.getClass().getSimpleName());
                return controller;
            };

            FXMLLoader loader = new FXMLLoader(url);
            loader.setControllerFactory(trackingFactory);
            loader.setClassLoader(ownerClass.getClassLoader());
            if (resources != null) {
                loader.setResources(resources);
            }

            // Use LoadListener to collect all FXML-created objects
            LoadContext context = new LoadContext();
            loader.setLoadListener(context);

            Parent root = loader.load();

            // Inject dependencies into all collected objects
            if (FxmlKit.getFxmlInjectionPolicy() != FxmlInjectionPolicy.DISABLED) {
                injectAll(di, loader, context.getAllObjects(), trackedControllers);
            } else {
                // When policy is DISABLED, only process controllers
                processControllersOnly(di, trackedControllers);
            }

            // Auto-attach stylesheets for root FXML only
            // (fx:include children should declare their own stylesheets in FXML)
            if (FxmlKit.isAutoAttachStyles()) {
                FxmlPathResolver.autoAttachStylesheets(root, url);
            }

            return root;

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load FXML for " + ownerClass.getName() + " from URL: " + url, e);
        }
    }

    /**
     * Loads an FXML file and returns both the root node and controller.
     *
     * <p>Convenience method that returns a {@link LoadResult} record containing
     * both the view and controller, saving an extra {@code getController()} call.
     *
     * @param <T>        the controller type
     * @param di         the dependency injection adapter
     * @param ownerClass the class used for FXML file resolution
     * @return a LoadResult containing the view and controller
     * @throws UncheckedIOException if FXML loading fails
     * @see #loadWithController(DiAdapter, Class, ResourceBundle)
     */
    @SuppressWarnings("unchecked")
    public static <T> LoadResult<T> loadWithController(DiAdapter di, Class<?> ownerClass) {
        return loadWithController(di, ownerClass, null);
    }

    /**
     * Loads an FXML file with resource bundle and returns both view and controller.
     *
     * <p>This method is identical to {@link #load(DiAdapter, Class, ResourceBundle)}
     * but returns the controller as well for convenience.
     *
     * @param <T>        the controller type
     * @param di         the dependency injection adapter (must not be null)
     * @param ownerClass the class used for FXML file resolution (must not be null)
     * @param resources  optional resource bundle (may be null)
     * @return a LoadResult containing both view and controller
     * @throws UncheckedIOException if FXML loading fails
     * @throws ClassCastException   if controller type doesn't match T
     */
    @SuppressWarnings("unchecked")
    public static <T> LoadResult<T> loadWithController(DiAdapter di, Class<?> ownerClass, ResourceBundle resources) {
        URL classpathUrl = URL_CACHE.computeIfAbsent(ownerClass, FxmlPathResolver::resolveFxmlUrl);
        URL url = resolveActualUrl(classpathUrl);

        Set<Object> trackedControllers = Collections.newSetFromMap(new IdentityHashMap<>());

        try {
            // Wrap controllerFactory to track ALL controllers
            Callback<Class<?>, Object> trackingFactory = type -> {
                // Phase 1: Create instance (constructor injection only)
                Object controller = di.getInstance(type);

                // Phase 2: Inject members BEFORE returning to JavaFX
                // Unconditional injection - trust DI framework's idempotency
                di.injectMembers(controller);

                trackedControllers.add(controller);
                logger.log(Level.FINE, "Controller created and injected: {0}",
                        controller.getClass().getSimpleName());
                return controller;
            };

            FXMLLoader loader = new FXMLLoader(url);
            loader.setControllerFactory(trackingFactory);
            loader.setClassLoader(ownerClass.getClassLoader());
            if (resources != null) {
                loader.setResources(resources);
            }

            // Use LoadListener to collect all FXML-created objects
            LoadContext context = new LoadContext();
            loader.setLoadListener(context);

            Parent root = loader.load();
            T controller = (T) loader.getController();

            // Inject dependencies into all collected objects
            if (FxmlKit.getFxmlInjectionPolicy() != FxmlInjectionPolicy.DISABLED) {
                injectAll(di, loader, context.getAllObjects(), trackedControllers);
            } else {
                // When policy is DISABLED, only process controllers
                processControllersOnly(di, trackedControllers);
            }

            // Auto-attach stylesheets for root FXML only
            // (fx:include children should declare their own stylesheets in FXML)
            if (FxmlKit.isAutoAttachStyles()) {
                FxmlPathResolver.autoAttachStylesheets(root, url);
            }

            return new LoadResult<>(root, controller);

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to load FXML with controller for " + ownerClass.getName() + " from URL: " + url, e);
        }
    }

    /**
     * Injects dependencies into all collected objects with optimized strategy.
     *
     * <p><b>Injection Strategy:</b>
     * <ol>
     *   <li><b>Controllers:</b> Identity-based deduplication (prevents duplicate @PostInject)</li>
     *   <li><b>Non-controllers:</b> Direct injection (trusts DI framework idempotency)</li>
     * </ol>
     *
     * <p><b>Injection Order:</b>
     * <ol>
     *   <li>Top-level controller (highest priority)</li>
     *   <li>Other FXML-created objects (filtered by policy)</li>
     *   <li>All tracked controllers (including fx:include, deduplicated)</li>
     * </ol>
     *
     * <p>For each object:
     * <ol>
     *   <li>Call {@link DiAdapter#injectMembers(Object)} unconditionally</li>
     *   <li>Invoke @PostInject lifecycle methods (once per instance)</li>
     * </ol>
     *
     * @param di                 the DI adapter to use for injection
     * @param loader             the FXMLLoader (for accessing controller)
     * @param allObjects         all objects collected by LoadListener
     * @param trackedControllers all controllers tracked by the wrapped factory
     */
    private static void injectAll(DiAdapter di, FXMLLoader loader, Set<Object> allObjects,
                                  Set<Object> trackedControllers) {
        FxmlInjectionPolicy policy = FxmlKit.getFxmlInjectionPolicy();
        Set<Class<?>> includeTypes = FxmlKit.getIncludeNodeTypes();
        Set<Class<?>> excludeTypes = FxmlKit.getExcludeNodeTypes();

        // Per-load identity-based sets (no static state pollution)
        Set<Object> injectedControllers = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> postInjected = Collections.newSetFromMap(new IdentityHashMap<>());

        // 1. Process top-level controller first (highest priority)
        Object controller = loader.getController();
        if (controller != null && injectedControllers.add(controller)) {
            logger.log(Level.FINE, "Injecting top-level controller: {0}",
                    controller.getClass().getSimpleName());

            // Unconditional injection (may have been injected in factory, but idempotent)
            di.injectMembers(controller);
            invokePostInject(controller, postInjected);
        }

        // 2. Process all LoadListener-collected objects
        logger.log(Level.FINE, "LoadListener collected {0} objects from FXML", allObjects.size());

        int injectedCount = 0;
        for (Object obj : allObjects) {
            // Skip if already processed as controller
            if (obj == controller) {
                continue;
            }

            // Check if this object should be injected
            if (shouldInjectAny(obj, policy, includeTypes, excludeTypes)) {
                logger.log(Level.FINE, "Injecting non-controller object: {0}",
                        obj.getClass().getSimpleName());

                // Unconditional injection for non-controllers (trust idempotency)
                di.injectMembers(obj);
                invokePostInject(obj, postInjected);
                injectedCount++;
            }
        }

        logger.log(Level.FINE,
                "Injected {0} non-controller objects out of {1} collected",
                new Object[]{injectedCount, allObjects.size()});

        // 3. Process ALL tracked controllers (including fx:include controllers)
        // Identity-based deduplication ensures each controller is processed only once
        if (!trackedControllers.isEmpty()) {
            logger.log(Level.FINE,
                    "Processing {0} tracked controllers for @PostInject",
                    trackedControllers.size());

            for (Object ctrl : trackedControllers) {
                // Inject only if not already processed (identity-based check)
                if (injectedControllers.add(ctrl)) {
                    logger.log(Level.FINE,
                            "Injecting tracked controller: {0}",
                            ctrl.getClass().getSimpleName());
                    // Unconditional injection (trust idempotency)
                    di.injectMembers(ctrl);
                }

                // Invoke @PostInject only once per controller
                if (!postInjected.contains(ctrl)) {
                    invokePostInject(ctrl, postInjected);
                }
            }
        }
    }

    /**
     * Processes controllers when node injection policy is DISABLED.
     *
     * <p>This method handles the special case where only controllers should
     * be processed, skipping all other FXML-created objects.
     *
     * @param di                 the DI adapter to use for injection
     * @param trackedControllers all controllers tracked by the factory
     */
    private static void processControllersOnly(DiAdapter di, Set<Object> trackedControllers) {
        Set<Object> postInjected = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Object ctrl : trackedControllers) {
            if (!postInjected.contains(ctrl)) {
                logger.log(Level.FINE,
                        "Processing controller (DISABLED policy): {0}",
                        ctrl.getClass().getSimpleName());

                // Unconditional injection (trust idempotency)
                di.injectMembers(ctrl);
                invokePostInject(ctrl, postInjected);
            }
        }
    }

    /**
     * Checks if a class should be skipped based on package prefix.
     *
     * <p>This is a performance optimization to avoid unnecessary reflection
     * checks on JDK/JavaFX internal classes.
     *
     * <p>The skip list is configurable via {@link FxmlKit#getSkipPackagePrefixes()}.
     *
     * @param cls the class to check
     * @return true if the class should be skipped
     */
    private static boolean shouldSkipByPackage(Class<?> cls) {
        String className = cls.getName();
        List<String> prefixes = FxmlKit.getSkipPackagePrefixes();
        for (String prefix : prefixes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if an object should receive dependency injection.
     *
     * <p>Decision algorithm:
     * <ol>
     *   <li>Filter out null objects</li>
     *   <li>Filter out classes matching skip package prefixes (performance)</li>
     *   <li>Check exclude list and {@code @SkipInjection}</li>
     *   <li>Check include list and {@code @FxmlObject}</li>
     *   <li>Apply policy-based decision</li>
     * </ol>
     *
     * <p><b>Policy Behavior:</b>
     * <ul>
     *   <li>{@code EXPLICIT_ONLY}: Only inject if in include list or has {@code @FxmlObject}</li>
     *   <li>{@code AUTO}: Inject if object has any injection points (fields/methods/constructor)</li>
     *   <li>{@code DISABLED}: Never inject (return false)</li>
     * </ul>
     *
     * @param obj          the object to check
     * @param policy       the current node injection policy
     * @param includeTypes types to always include
     * @param excludeTypes types to always exclude
     * @return true if the object should be injected
     */
    private static boolean shouldInjectAny(Object obj, FxmlInjectionPolicy policy,
                                           Set<Class<?>> includeTypes, Set<Class<?>> excludeTypes) {
        if (obj == null) {
            return false;
        }

        Class<?> cls = obj.getClass();

        // Filter by configurable skip package prefixes
        if (shouldSkipByPackage(cls)) {
            return false;
        }

        // Check exclude list
        if (excludeTypes.stream().anyMatch(x -> x.isAssignableFrom(cls))) {
            return false;
        }
        if (cls.isAnnotationPresent(SkipInjection.class)) {
            return false;
        }

        // Check include list
        if (includeTypes.stream().anyMatch(x -> x.isAssignableFrom(cls))) {
            return true;
        }
        if (cls.isAnnotationPresent(FxmlObject.class)) {
            return true;
        }

        // Apply policy-based decision
        // Intentional: traditional if-else for backward compatibility.
        if (policy == FxmlInjectionPolicy.EXPLICIT_ONLY) {
            return false;
        } else if (policy == FxmlInjectionPolicy.AUTO) {
            return hasAnyInjectPoint(cls);
        } else { // DISABLED
            return false;
        }
    }

    /**
     * Invokes @PostInject lifecycle methods on an object.
     *
     * <p>Methods are invoked in parent-first order (superclass before subclass)
     * as determined by {@link InjectionUtils#findPostMethods(Class)}.
     *
     * <p>Duplicate invocation is prevented using an identity-based set.
     * This is critical because the same object may appear multiple times in
     * the LoadListener collection.
     *
     * <p><b>Method Requirements:</b>
     * <ul>
     *   <li>Annotated with @PostInject</li>
     *   <li>Zero parameters</li>
     *   <li>Any return type (ignored)</li>
     * </ul>
     *
     * @param instance     the object to invoke lifecycle methods on
     * @param postInjected set tracking already-processed objects
     * @throws RuntimeException if any @PostInject method throws an exception
     */
    private static void invokePostInject(Object instance, Set<Object> postInjected) {
        if (instance == null) {
            return;
        }

        // Prevent duplicate invocation
        if (postInjected.contains(instance)) {
            logger.log(Level.FINEST,
                    "@PostInject already called for: {0}",
                    instance.getClass().getSimpleName());
            return;
        }

        try {
            List<Method> postMethods = InjectionUtils.findPostMethods(instance.getClass());

            if (!postMethods.isEmpty()) {
                logger.log(Level.FINE,
                        "Calling @PostInject for: {0}",
                        instance.getClass().getSimpleName());
            }

            for (Method method : postMethods) {
                if (method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    method.invoke(instance);

                    logger.log(Level.FINER,
                            "Called @PostInject method: {0}.{1}()",
                            new Object[]{instance.getClass().getSimpleName(), method.getName()});
                }
            }

            // Mark as processed
            postInjected.add(instance);

        } catch (Exception e) {
            throw new RuntimeException(
                    "@PostInject invocation failed for " + instance.getClass().getName(), e);
        }
    }

    /**
     * Checks if a class has any dependency injection points.
     *
     * <p>A class is considered to have injection points if it has:
     * <ul>
     *   <li>Fields annotated with @Inject</li>
     *   <li>Methods annotated with @Inject</li>
     *   <li>Constructor annotated with @Inject</li>
     *   <li>Constructor with parameters (implies need for injection)</li>
     * </ul>
     *
     * <p>This method is used by the {@code AUTO} injection policy to determine
     * which objects should receive dependency injection.
     *
     * @param cls the class to check
     * @return true if the class has any injection points
     */
    private static boolean hasAnyInjectPoint(Class<?> cls) {
        // Check for injectable fields
        if (!InjectionUtils.findInjectFields(cls).isEmpty()) {
            return true;
        }

        // Check for injectable methods
        if (!InjectionUtils.findInjectMethods(cls).isEmpty()) {
            return true;
        }

        // Check constructor
        try {
            Constructor<?> constructor = InjectionUtils.chooseConstructor(cls);
            if (InjectionUtils.isInject(constructor)) {
                return true;
            }
            return constructor.getParameterCount() > 0;
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * Creates a basic FXMLLoader for Tier 1 (zero-configuration) mode.
     *
     * <p>This loader is configured with:
     * <ul>
     *   <li>The class loader from {@code ownerClass}</li>
     *   <li>An optional {@link ResourceBundle} for internationalization</li>
     *   <li>A simple controller factory using no-arg constructors</li>
     * </ul>
     *
     * <p><b>No Dependency Injection:</b> This loader does NOT perform any
     * dependency injection. Controllers are instantiated via their no-argument
     * constructor. For DI support, use {@link #loadWithController} instead.
     *
     * <p><b>Package-Private:</b> This is an internal utility method for use by
     * {@link FxmlView} and {@link FxmlViewProvider}.
     *
     * @param fxmlUrl    the URL of the FXML file (must not be null)
     * @param ownerClass the class loading the FXML (must not be null)
     * @param resources  optional ResourceBundle for i18n (maybe null)
     * @return a configured FXMLLoader ready to call {@link FXMLLoader#load()}
     * @see #loadWithController(DiAdapter, Class, ResourceBundle)
     */
    public static FXMLLoader createBasicLoader(URL fxmlUrl, Class<?> ownerClass, ResourceBundle resources) {
        URL actualUrl = resolveActualUrl(fxmlUrl);
        FXMLLoader loader = new FXMLLoader(actualUrl);
        loader.setClassLoader(ownerClass.getClassLoader());

        if (resources != null) {
            loader.setResources(resources);
        }

        // Simple controller factory - just instantiate, no injection
        loader.setControllerFactory(type -> {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to create controller: " + type.getName() +
                                ". In zero-config mode, controllers must have a no-arg constructor.", e);
            }
        });

        return loader;
    }

    /**
     * Result of loading an FXML file with controller.
     *
     * <p>This class encapsulates both the root view node and the controller instance,
     * providing convenient access to both without requiring a separate {@code getController()}
     * call.
     *
     * <p>Example usage:
     * <pre>{@code
     * LoadResult<UserController> result = FxmlKitLoader.loadWithController(
     *     injector, UserView.class
     * );
     *
     * Parent view = result.getView();
     * UserController controller = result.getController();
     *
     * controller.initialize(userData);
     * stage.setScene(new Scene(view));
     * }</pre>
     *
     * @param <T> the controller type
     */
    public static class LoadResult<T> {
        // Instead of using a record, we use a regular class for compatibility with older versions.

        /**
         * The root node of the loaded FXML
         */
        private final Parent view;
        /**
         * the controller instance (may be null if FXML has no controller)
         */
        private final T controller;

        public LoadResult(Parent view, T controller) {
            this.view = view;
            this.controller = controller;
        }

        public Parent getView() {
            return view;
        }

        public T getController() {
            return controller;
        }
    }

    /**
     * Internal context for a single FXML load operation.
     *
     * <p>This class implements {@link LoadListener} to intercept all objects created
     * during FXML parsing. It maintains per-load state without polluting static fields,
     * making concurrent loads safe.
     *
     * <p>Two sets are maintained:
     * <ul>
     *   <li>{@code allObjects}: All objects created by FXML (for injection)</li>
     *   <li>{@code postInjected}: Objects that have had @PostInject invoked (for deduplication)</li>
     * </ul>
     *
     * <p><b>Identity-Based Sets:</b> Uses {@link IdentityHashMap} backing to ensure
     * object identity comparison (not equals/hashCode), which is critical for correctly
     * tracking object instances.
     *
     * <p><b>Thread Safety:</b> Not thread-safe, but doesn't need to be as each
     * FXMLLoader operates on a single thread.
     */
    /**
     * Context for tracking FXML loading state and collecting created objects.
     *
     * <p>This class also handles automatic stylesheet attachment for fx:include elements:
     * <ol>
     *   <li>Before loading, analyzes the FXML to find all fx:include declarations</li>
     *   <li>During loading, tracks fx:include root nodes in order</li>
     *   <li>After loading, attaches stylesheets to each fx:include root node</li>
     * </ol>
     */
    private static class LoadContext implements LoadListener {

        /**
         * Flag to indicate the next endElement call is for a fx:include result.
         * This is set to true by {@link #beginIncludeElement()} and consumed by
         * {@link #endElement(Object)}.
         */
        private boolean nextIsFromInclude;

        /**
         * All objects created during FXML parsing.
         * Uses identity-based set for accurate object tracking.
         */
        private final Set<Object> allObjects = Collections.newSetFromMap(new IdentityHashMap<>());

        /**
         * Objects that have already had @PostInject methods invoked.
         * Uses identity-based set to prevent duplicate invocation.
         */
        private final Set<Object> postInjected = Collections.newSetFromMap(new IdentityHashMap<>());

        /**
         * Returns all objects collected during FXML parsing.
         *
         * @return the set of all created objects (never null, may be empty)
         */
        public Set<Object> getAllObjects() {
            return allObjects;
        }

        /**
         * Returns the set of objects that have had @PostInject invoked.
         *
         * @return the set of post-injected objects (never null, may be empty)
         */
        public Set<Object> getPostInjected() {
            return postInjected;
        }

        /**
         * Called when an FXML element has been fully parsed and instantiated.
         *
         * <p>If the element is the result of {@code <fx:include>} (indicated by
         * the flag set in {@link #beginIncludeElement()}), recursively collects
         * all child nodes to ensure proper dependency injection.
         *
         * @param value the newly created object
         */
        @Override
        public void endElement(Object value) {
            if (value != null) {
                allObjects.add(value);

                if (nextIsFromInclude) {
                    // Only recursively collect children if this is from fx:include
                    if (value instanceof Parent) {
                        // Intentional: traditional instanceof for backward compatibility.
                        Parent parent = (Parent) value;
                        logger.log(Level.FINE, "Recursively collecting nested nodes from <fx:include>: {0}",
                                value.getClass().getSimpleName());
                        collectNestedNodes(parent);
                    } else {
                        logger.log(Level.FINE, "fx:include result is not a Parent: {0}, skipping recursive collection",
                                value.getClass().getSimpleName());
                    }
                    // Reset flag
                    nextIsFromInclude = false;
                }
            }
        }

        /**
         * Recursively collects all nodes from a parent.
         *
         * <p>This method is only called for nodes created by {@code <fx:include>},
         * ensuring that objects created by nested FXMLLoaders are properly collected
         * for dependency injection.
         *
         * @param parent the parent node to collect children from
         */
        private void collectNestedNodes(Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                // Add child if not already collected
                if (!allObjects.contains(child)) {
                    allObjects.add(child);

                    logger.log(Level.FINEST,
                            "Collected nested node from fx:include: {0}",
                            child.getClass().getSimpleName());
                }

                // Recursively process if this child is also a Parent
                if (child instanceof Parent) {
                    collectNestedNodes((Parent) child);
                }
            }
        }

        // Unused LoadListener methods (empty implementations)

        @Override
        public void readImportProcessingInstruction(String target) {
        }

        @Override
        public void readLanguageProcessingInstruction(String language) {
        }

        @Override
        public void readComment(String comment) {
        }

        @Override
        public void beginInstanceDeclarationElement(Class<?> type) {
        }

        @Override
        public void beginUnknownTypeElement(String name) {
        }

        /**
         * Called when {@code <fx:include>} is encountered in FXML.
         *
         * <p>Sets a flag so that the next {@link #endElement(Object)} call knows
         * it's processing the result of a fx:include, and should recursively
         * collect all nested nodes.
         */
        @Override
        public void beginIncludeElement() {
            logger.log(Level.FINE, "Encountered <fx:include> - next endElement will be processed recursively");
            nextIsFromInclude = true;
        }

        @Override
        public void beginReferenceElement() {
        }

        @Override
        public void beginCopyElement() {
        }

        @Override
        public void beginRootElement() {
        }

        @Override
        public void beginPropertyElement(String name, Class<?> sourceType) {
        }

        @Override
        public void beginUnknownStaticPropertyElement(String name) {
        }

        @Override
        public void beginScriptElement() {
        }

        @Override
        public void beginDefineElement() {
        }

        @Override
        public void readInternalAttribute(String name, String value) {
        }

        @Override
        public void readPropertyAttribute(String name, Class<?> sourceType, String value) {
        }

        @Override
        public void readUnknownStaticPropertyAttribute(String name, String value) {
        }

        @Override
        public void readEventHandlerAttribute(String name, String value) {
        }
    }
}