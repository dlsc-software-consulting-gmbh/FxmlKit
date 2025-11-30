package com.dlsc.fxmlkit.fxml;

import com.dlsc.fxmlkit.core.DiAdapter;
import com.dlsc.fxmlkit.core.LiteDiAdapter;
import com.dlsc.fxmlkit.policy.FxmlInjectionPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central configuration facade for FxmlKit framework.
 *
 * <p>This class provides global configuration for the three-tier progressive design
 * of FxmlKit, allowing applications to start with zero configuration and progressively
 * enable dependency injection as needed.
 *
 * <h2>Three-Tier Progressive Design</h2>
 *
 * <h3>Tier 1: Zero-Configuration Mode (No DI)</h3>
 * <p>Goal: Simplify FXML development without any framework setup.
 * <pre>{@code
 * // No configuration needed!
 * MainView view = new MainView();
 * Parent root = view.getView();  // Works immediately
 * }</pre>
 *
 * <p>Features:
 * <ul>
 *   <li>FXML loading with automatic file resolution</li>
 *   <li>CSS/BSS stylesheet auto-attachment</li>
 *   <li>Controller creation (no-arg constructor)</li>
 *   <li>No @Inject field injection (fields stay null)</li>
 *   <li>No @PostInject method calls</li>
 *   <li>No @FxmlObject processing</li>
 * </ul>
 *
 * <h3>Tier 2: Global DI Mode (Single-User Desktop)</h3>
 * <p>Goal: Enable dependency injection with one-time global configuration.
 * <pre>{@code
 * // One-time setup
 * LiteDiAdapter injector = new LiteDiAdapter();
 * injector.bindInstance(UserService.class, new UserService());
 * FxmlKit.setDiAdapter(injector);  // Enable DI globally
 *
 * // Then use normally
 * MainView view = new MainView();
 * Parent root = view.getView();  // Full DI support
 * }</pre>
 *
 * <p>Features:
 * <ul>
 *   <li>All Tier 1 features</li>
 *   <li>@Inject field injection</li>
 *   <li>@PostInject method calls</li>
 *   <li>@FxmlObject node processing</li>
 * </ul>
 *
 * <h3>Tier 3: Isolated DI Mode (JPro Multi-User)</h3>
 * <p>Goal: Per-user isolated DI containers for complete data separation.
 * <pre>{@code
 * // Each user gets independent Injector
 * Injector injector = Guice.createInjector(new UserModule(user));
 * MainView view = injector.getInstance(MainView.class);
 * Parent root = view.getView();  // User-specific data
 * }</pre>
 *
 * <p>Features:
 * <ul>
 *   <li>All Tier 2 features</li>
 *   <li>Per-instance DiAdapter (automatic injection)</li>
 *   <li>Complete user data isolation</li>
 *   <li>No cross-contamination between users</li>
 * </ul>
 *
 * <h2>Default Configuration</h2>
 * <ul>
 *   <li>DI Adapter: null (Tier 1: zero-config mode)</li>
 *   <li>Logging: WARNING (production mode)</li>
 *   <li>Stylesheets: Auto-attach enabled</li>
 *   <li>Node Injection: EXPLICIT_ONLY</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All configuration methods are thread-safe. However, it is recommended to
 * complete all configuration during application startup before creating views.
 *
 * @see FxmlKitLoader
 * @see FxmlViewProvider
 * @see DiAdapter
 */
public final class FxmlKit {

    private static final String ROOT_PACKAGE_NAME = "com.dlsc.fxmlkit";

    private static volatile Level globalLogLevel = Level.WARNING;
    private static volatile DiAdapter globalDiAdapter = null;
    private static volatile boolean autoAttachStyles = true;
    private static volatile FxmlInjectionPolicy fxmlInjectionPolicy = FxmlInjectionPolicy.EXPLICIT_ONLY;

    private static final Set<Class<?>> INCLUDE_NODE_TYPES = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> EXCLUDE_NODE_TYPES = ConcurrentHashMap.newKeySet();

    /**
     * Default package prefixes to skip during injection (performance optimization).
     *
     * <p>These packages typically contain JDK/JavaFX internal classes that don't
     * need dependency injection. Skipping them improves performance by avoiding
     * unnecessary reflection checks.
     */
    private static final List<String> DEFAULT_SKIP_PACKAGE_PREFIXES = List.of(
            "java.",
            "javax.",
            "javafx.",
            "jdk.",
            "sun.",
            "com.sun."
    );

    /**
     * Package prefixes to skip during injection.
     *
     * <p>This list is mutable and can be modified directly if needed.
     * For example, if your package is {@code java.study.day01}, you can:
     *
     * <pre>{@code
     * FxmlKit.getSkipPackagePrefixes().remove("java.");
     * FxmlKit.getSkipPackagePrefixes().add("java.lang.");
     * FxmlKit.getSkipPackagePrefixes().add("java.util.");
     * FxmlKit.getSkipPackagePrefixes().add("java.io.");
     * // ... add other JDK packages as needed
     * }</pre>
     *
     * <p>This is a rare edge case - most users will never need to modify this list.
     */
    private static final List<String> SKIP_PACKAGE_PREFIXES = new ArrayList<>(DEFAULT_SKIP_PACKAGE_PREFIXES);

    private FxmlKit() {
    }

    // Logging Configuration

    /**
     * Enables development mode with verbose logging.
     */
    public static void enableDevelopmentMode() {
        setLogLevel(Level.FINE);
    }

    /**
     * Enables production mode with minimal logging (default).
     */
    public static void enableProductionMode() {
        setLogLevel(Level.WARNING);
    }

    /**
     * Enables silent mode with no logging.
     */
    public static void enableSilentMode() {
        setLogLevel(Level.OFF);
    }

    /**
     * Sets the logging level for all FxmlKit components.
     *
     * @param level the logging level (must not be null)
     */
    public static void setLogLevel(Level level) {
        globalLogLevel = Objects.requireNonNull(level, "Log level cannot be null");

        Logger rootLogger = Logger.getLogger(ROOT_PACKAGE_NAME);
        rootLogger.setLevel(level);

        Logger.getLogger(FxmlKitLoader.class.getName()).setLevel(level);
        Logger.getLogger(FxmlViewProvider.class.getName()).setLevel(level);
        Logger.getLogger(LiteDiAdapter.class.getName()).setLevel(level);
    }

    /**
     * Gets the current global logging level.
     *
     * @return the current logging level
     */
    public static Level getLogLevel() {
        return globalLogLevel;
    }

    // DI Adapter Configuration

    /**
     * Sets the global dependency injection adapter.
     *
     * <p>Three-Tier Usage:
     * <ul>
     *   <li>Tier 1: Don't call this (or pass null) for zero-config mode</li>
     *   <li>Tier 2: Set once globally for shared DI across all views</li>
     *   <li>Tier 3: Use injector.getInstance(View.class) for isolated DI</li>
     * </ul>
     *
     * @param adapter the DI adapter to use, or null for zero-config mode
     */
    public static void setDiAdapter(DiAdapter adapter) {
        globalDiAdapter = adapter;
    }

    /**
     * Gets the current global dependency injection adapter.
     *
     * <p>This method is used internally by {@link FxmlViewProvider} to select
     * the DI adapter in Tier 2 (Global DI Mode).
     *
     * <p><b>Important:</b> Can return null (Tier 1: zero-config mode).
     *
     * <h3>Three-Tier Usage</h3>
     * <ul>
     *   <li><b>Tier 1:</b> Returns null (zero-config mode)</li>
     *   <li><b>Tier 2:</b> Returns the global DiAdapter set via {@link #setDiAdapter(DiAdapter)}</li>
     *   <li><b>Tier 3:</b> Serves as fallback when instance-level adapter is not set</li>
     * </ul>
     *
     * @return the current DI adapter, or null if none configured
     */
    public static DiAdapter getDiAdapter() {
        return globalDiAdapter;
    }

    /**
     * Checks if FxmlKit is in zero-configuration mode (Tier 1).
     *
     * <p>In zero-config mode:
     * <ul>
     *   <li>FXML files load normally</li>
     *   <li>CSS/BSS files attach automatically</li>
     *   <li>Controllers created with no-arg constructor</li>
     *   <li>@Inject fields are NOT injected (remain null)</li>
     *   <li>@PostInject methods are NOT called</li>
     *   <li>@FxmlObject nodes are NOT processed</li>
     * </ul>
     *
     * @return true if no DI adapter configured
     */
    public static boolean isZeroConfigMode() {
        return globalDiAdapter == null;
    }

    /**
     * Explicitly enables zero-configuration mode (Tier 1).
     *
     * <p>Clears any configured DiAdapter, causing views to load without
     * dependency injection.
     */
    public static void enableZeroConfigMode() {
        globalDiAdapter = null;
    }

    // Stylesheet Configuration

    /**
     * Enables automatic stylesheet attachment.
     */
    public static void enableAutoAttachStyles() {
        autoAttachStyles = true;
    }

    /**
     * Disables automatic stylesheet attachment.
     */
    public static void disableAutoAttachStyles() {
        autoAttachStyles = false;
    }

    /**
     * Sets whether stylesheets should be automatically attached.
     *
     * @param enabled true to enable auto-attach, false to disable
     */
    public static void setAutoAttachStyles(boolean enabled) {
        autoAttachStyles = enabled;
    }

    /**
     * Checks if automatic stylesheet attachment is enabled.
     *
     * @return true if auto-attach is enabled
     */
    public static boolean isAutoAttachStyles() {
        return autoAttachStyles;
    }

    // Node Injection Policy

    /**
     * Sets the node injection policy.
     *
     * <p>Policies:
     * <ul>
     *   <li>EXPLICIT_ONLY - Only @FxmlObject nodes (default)</li>
     *   <li>AUTO - Auto-detect nodes with @Inject</li>
     *   <li>DISABLED - Only inject controllers</li>
     * </ul>
     *
     * <p><b>Note:</b> In zero-config mode (Tier 1), this policy is ignored
     * as no dependency injection occurs.
     *
     * @param policy the policy to use (must not be null)
     */
    public static void setFxmlInjectionPolicy(FxmlInjectionPolicy policy) {
        fxmlInjectionPolicy = Objects.requireNonNull(policy, "Policy cannot be null");
    }

    /**
     * Gets the current node injection policy.
     *
     * @return the current policy
     */
    public static FxmlInjectionPolicy getNodeInjectionPolicy() {
        return fxmlInjectionPolicy;
    }

    /**
     * Gets the list of package prefixes to skip during injection.
     *
     * <p>Classes in these packages are skipped for performance optimization,
     * avoiding unnecessary reflection checks on JDK/JavaFX internal classes.
     *
     * <p>The returned list is mutable. Modify directly if needed (rare case):
     *
     * <pre>{@code
     * // If your package is java.study.day01:
     * FxmlKit.getSkipPackagePrefixes().remove("java.");
     * FxmlKit.getSkipPackagePrefixes().add("java.lang.");
     * FxmlKit.getSkipPackagePrefixes().add("java.util.");
     * }</pre>
     *
     * @return the mutable list of skip package prefixes
     */
    public static List<String> getSkipPackagePrefixes() {
        return SKIP_PACKAGE_PREFIXES;
    }

    // Include/Exclude Node Types

    /**
     * Adds a node type to the injection include list.
     *
     * <p>Nodes of this type will always receive dependency injection,
     * regardless of policy (except DISABLED) or @FxmlObject annotation.
     *
     * @param nodeType the node type to include (must not be null)
     */
    public static void includeNodeType(Class<?> nodeType) {
        INCLUDE_NODE_TYPES.add(Objects.requireNonNull(nodeType, "Node type cannot be null"));
    }

    /**
     * Removes a node type from the injection include list.
     *
     * @param nodeType the node type to remove
     */
    public static void removeIncludeNodeType(Class<?> nodeType) {
        INCLUDE_NODE_TYPES.remove(nodeType);
    }

    /**
     * Checks if a node type is in the include list.
     *
     * @param nodeType the node type to check
     * @return true if the type is included
     */
    public static boolean isIncludedNodeType(Class<?> nodeType) {
        return INCLUDE_NODE_TYPES.contains(nodeType);
    }

    /**
     * Gets all included node types (unmodifiable).
     *
     * @return unmodifiable set of included node types
     */
    public static Set<Class<?>> getIncludeNodeTypes() {
        return Set.copyOf(INCLUDE_NODE_TYPES);
    }

    /**
     * Adds a node type to the injection exclude list.
     *
     * <p>Nodes of this type will never receive dependency injection,
     * even if they have @FxmlObject or are in the include list.
     *
     * @param nodeType the node type to exclude (must not be null)
     */
    public static void excludeNodeType(Class<?> nodeType) {
        EXCLUDE_NODE_TYPES.add(Objects.requireNonNull(nodeType, "Node type cannot be null"));
    }

    /**
     * Removes a node type from the injection exclude list.
     *
     * @param nodeType the node type to remove
     */
    public static void removeExcludeNodeType(Class<?> nodeType) {
        EXCLUDE_NODE_TYPES.remove(nodeType);
    }

    /**
     * Checks if a node type is in the exclude list.
     *
     * @param nodeType the node type to check
     * @return true if the type is excluded
     */
    public static boolean isExcludedNodeType(Class<?> nodeType) {
        return EXCLUDE_NODE_TYPES.contains(nodeType);
    }

    /**
     * Gets all excluded node types (unmodifiable).
     *
     * @return unmodifiable set of excluded node types
     */
    public static Set<Class<?>> getExcludeNodeTypes() {
        return Set.copyOf(EXCLUDE_NODE_TYPES);
    }

    // Reset

    /**
     * Resets all FxmlKit configuration to defaults.
     *
     * <p>Restores:
     * <ul>
     *   <li>DI adapter to null (zero-config mode)</li>
     *   <li>Log level to WARNING</li>
     *   <li>Auto-attach styles to true</li>
     *   <li>Node policy to EXPLICIT_ONLY</li>
     *   <li>Include/exclude lists to empty</li>
     *   <li>Skip package prefixes to defaults</li>
     * </ul>
     *
     * <p>Use case: Testing, or resetting between user sessions.
     */
    public static void resetAll() {
        globalDiAdapter = null;
        globalLogLevel = Level.WARNING;
        autoAttachStyles = true;
        fxmlInjectionPolicy = FxmlInjectionPolicy.EXPLICIT_ONLY;
        INCLUDE_NODE_TYPES.clear();
        EXCLUDE_NODE_TYPES.clear();
        SKIP_PACKAGE_PREFIXES.clear();
        SKIP_PACKAGE_PREFIXES.addAll(DEFAULT_SKIP_PACKAGE_PREFIXES);
    }
}