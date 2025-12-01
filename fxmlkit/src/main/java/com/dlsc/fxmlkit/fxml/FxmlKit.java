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
 * // Optional: Enable debug logging
 * FxmlKit.setLogLevel(Level.FINE);
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

    /**
     * Sets the logging level for all FxmlKit components.
     *
     * <p>Common levels:
     * <ul>
     *   <li>{@link Level#WARNING} - Production (default, minimal logging)</li>
     *   <li>{@link Level#FINE} - Development/debugging (verbose logging)</li>
     *   <li>{@link Level#OFF} - Silent mode (no logging)</li>
     *   <li>{@link Level#INFO} - General information</li>
     *   <li>{@link Level#SEVERE} - Only critical errors</li>
     * </ul>
     *
     * <p>Examples:
     * <pre>{@code
     * // Production (default)
     * FxmlKit.setLogLevel(Level.WARNING);
     *
     * // Development/debugging
     * FxmlKit.setLogLevel(Level.FINE);
     *
     * // Silent mode
     * FxmlKit.setLogLevel(Level.OFF);
     * }</pre>
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
     * <p>Default is {@link Level#WARNING}.
     *
     * @return the current logging level
     */
    public static Level getLogLevel() {
        return globalLogLevel;
    }

    /**
     * Sets the global dependency injection adapter.
     *
     * <p>Three-Tier Usage:
     * <ul>
     *   <li><b>Tier 1 (Zero-Config):</b> Don't call this method (or pass null) - no DI</li>
     *   <li><b>Tier 2 (Global DI):</b> Set once globally for shared DI across all views</li>
     *   <li><b>Tier 3 (Isolated DI):</b> Use injector.getInstance(View.class) for per-user DI</li>
     * </ul>
     *
     * <p>Examples:
     * <pre>{@code
     * // Tier 1: Zero-config mode (no DI)
     * FxmlKit.setDiAdapter(null);  // Or don't call this method at all
     *
     * // Tier 2: Global DI mode
     * LiteDiAdapter adapter = new LiteDiAdapter();
     * adapter.bindInstance(UserService.class, new UserService());
     * FxmlKit.setDiAdapter(adapter);
     * }</pre>
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
     * <p>Example:
     * <pre>{@code
     * // Check if in zero-config mode
     * if (FxmlKit.getDiAdapter() == null) {
     *     System.out.println("Zero-config mode - no DI");
     * }
     * }</pre>
     *
     * @return the current DI adapter, or null if none configured
     */
    public static DiAdapter getDiAdapter() {
        return globalDiAdapter;
    }

    /**
     * Sets whether stylesheets should be automatically attached.
     *
     * <p>When enabled (default), FxmlKit automatically attaches CSS/BSS files
     * that match the view class name.
     *
     * <p>Examples:
     * <pre>{@code
     * // Enable auto-attach (default)
     * FxmlKit.setAutoAttachStyles(true);
     *
     * // Disable auto-attach
     * FxmlKit.setAutoAttachStyles(false);
     * }</pre>
     *
     * @param enabled true to enable auto-attach, false to disable
     */
    public static void setAutoAttachStyles(boolean enabled) {
        autoAttachStyles = enabled;
    }

    /**
     * Checks if automatic stylesheet attachment is enabled.
     *
     * <p>Default is {@code true}.
     *
     * @return true if auto-attach is enabled
     */
    public static boolean isAutoAttachStyles() {
        return autoAttachStyles;
    }

    /**
     * Sets the FXML node injection policy.
     *
     * <p>Available policies:
     * <ul>
     *   <li><b>EXPLICIT_ONLY</b> - Only inject nodes marked with @FxmlObject (default)</li>
     *   <li><b>AUTO</b> - Auto-detect nodes with @Inject annotations</li>
     *   <li><b>DISABLED</b> - Only inject controllers, skip all nodes</li>
     * </ul>
     *
     * <p><b>Note:</b> In zero-config mode (Tier 1), this policy is ignored
     * as no dependency injection occurs.
     *
     * <p>Examples:
     * <pre>{@code
     * // Default: Only @FxmlObject nodes
     * FxmlKit.setFxmlInjectionPolicy(FxmlInjectionPolicy.EXPLICIT_ONLY);
     *
     * // Auto-detect: Any node with @Inject
     * FxmlKit.setFxmlInjectionPolicy(FxmlInjectionPolicy.AUTO);
     *
     * // Disabled: Only controllers
     * FxmlKit.setFxmlInjectionPolicy(FxmlInjectionPolicy.DISABLED);
     * }</pre>
     *
     * @param policy the policy to use (must not be null)
     */
    public static void setFxmlInjectionPolicy(FxmlInjectionPolicy policy) {
        fxmlInjectionPolicy = Objects.requireNonNull(policy, "Policy cannot be null");
    }

    /**
     * Gets the current FXML node injection policy.
     *
     * <p>Default is {@link FxmlInjectionPolicy#EXPLICIT_ONLY}.
     *
     * @return the current policy
     */
    public static FxmlInjectionPolicy getFxmlInjectionPolicy() {
        return fxmlInjectionPolicy;
    }

    /**
     * Gets the list of package prefixes to skip during injection.
     *
     * <p>Classes in these packages are skipped for performance optimization,
     * avoiding unnecessary reflection checks on JDK/JavaFX internal classes.
     *
     * <p><b>Default prefixes:</b>
     * <ul>
     *   <li>java.*</li>
     *   <li>javax.*</li>
     *   <li>javafx.*</li>
     *   <li>jdk.*</li>
     *   <li>sun.*</li>
     *   <li>com.sun.*</li>
     * </ul>
     *
     * <p>The returned list is mutable. Modify directly if needed (rare case):
     * <pre>{@code
     * // Example: If your package is java.study.day01
     * FxmlKit.getSkipPackagePrefixes().remove("java.");
     * FxmlKit.getSkipPackagePrefixes().add("java.lang.");
     * FxmlKit.getSkipPackagePrefixes().add("java.util.");
     * FxmlKit.getSkipPackagePrefixes().add("java.io.");
     * }</pre>
     *
     * @return mutable list of skip package prefixes
     */
    public static List<String> getSkipPackagePrefixes() {
        return SKIP_PACKAGE_PREFIXES;
    }

    /**
     * Gets the set of node types to exclude from injection.
     *
     * <p>Nodes of these types will never receive dependency injection,
     * even if they have @FxmlObject or are in the include list.
     *
     * <p>The returned set is mutable and thread-safe. Modify directly:
     * <pre>{@code
     * // Add exclusions
     * FxmlKit.getExcludeNodeTypes().add(Button.class);
     * FxmlKit.getExcludeNodeTypes().add(Label.class);
     *
     * // Remove exclusion
     * FxmlKit.getExcludeNodeTypes().remove(Button.class);
     *
     * // Check exclusion
     * if (FxmlKit.getExcludeNodeTypes().contains(Button.class)) {
     *     // Button is excluded
     * }
     *
     * // Bulk operations
     * FxmlKit.getExcludeNodeTypes().addAll(List.of(Button.class, Label.class));
     * FxmlKit.getExcludeNodeTypes().clear();
     * }</pre>
     *
     * @return mutable thread-safe set of excluded node types
     */
    public static Set<Class<?>> getExcludeNodeTypes() {
        return EXCLUDE_NODE_TYPES;
    }

    /**
     * Gets the set of node types to include for injection.
     *
     * <p>Nodes of these types will always receive dependency injection,
     * regardless of policy (except DISABLED) or @FxmlObject annotation.
     *
     * <p>The returned set is mutable and thread-safe. Modify directly:
     * <pre>{@code
     * // Add inclusions
     * FxmlKit.getIncludeNodeTypes().add(MyCustomNode.class);
     * FxmlKit.getIncludeNodeTypes().add(AnotherNode.class);
     *
     * // Remove inclusion
     * FxmlKit.getIncludeNodeTypes().remove(MyCustomNode.class);
     *
     * // Check inclusion
     * if (FxmlKit.getIncludeNodeTypes().contains(MyCustomNode.class)) {
     *     // MyCustomNode is included
     * }
     * }</pre>
     *
     * @return mutable thread-safe set of included node types
     */
    public static Set<Class<?>> getIncludeNodeTypes() {
        return INCLUDE_NODE_TYPES;
    }

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
     * <p>Use cases:
     * <ul>
     *   <li>Testing (reset between test cases)</li>
     *   <li>Resetting between user sessions</li>
     *   <li>Debugging (return to known state)</li>
     * </ul>
     *
     * <p>Example:
     * <pre>{@code
     * @AfterEach
     * void tearDown() {
     *     FxmlKit.resetAll();  // Clean slate for next test
     * }
     * }</pre>
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