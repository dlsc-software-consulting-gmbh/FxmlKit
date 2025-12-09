package com.dlsc.fxmlkit.fxml;

import com.dlsc.fxmlkit.core.DiAdapter;
import com.dlsc.fxmlkit.core.LiteDiAdapter;
import com.dlsc.fxmlkit.hotreload.HotReloadManager;
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
 * <h2>Hot Reload Support</h2>
 * <p>FxmlKit supports automatic FXML hot reload during development:
 * <pre>{@code
 * // Enable at application startup (development mode only)
 * if (isDevelopmentMode()) {
 *     FxmlKit.enableHotReload();
 * }
 *
 * // Views automatically register for hot reload
 * MainView view = new MainView();
 *
 * // Disable at shutdown
 * FxmlKit.disableHotReload();
 * }</pre>
 *
 * <h2>Three-Tier Progressive Design</h2>
 *
 * <h3>Tier 1: Zero-Configuration Mode (No DI)</h3>
 * <pre>{@code
 * // No configuration needed!
 * MainView view = new MainView();
 * Parent root = view;  // Works immediately
 * }</pre>
 *
 * <h3>Tier 2: Global DI Mode (Single-User Desktop)</h3>
 * <pre>{@code
 * // One-time setup
 * LiteDiAdapter injector = new LiteDiAdapter();
 * injector.bindInstance(UserService.class, new UserService());
 * FxmlKit.setDiAdapter(injector);
 *
 * MainView view = new MainView();  // Full DI support
 * }</pre>
 *
 * <h3>Tier 3: Isolated DI Mode (JPro Multi-User)</h3>
 * <pre>{@code
 * Injector injector = Guice.createInjector(new UserModule(user));
 * MainView view = injector.getInstance(MainView.class);
 * }</pre>
 *
 * @see FxmlKitLoader
 * @see FxmlViewProvider
 * @see DiAdapter
 * @see HotReloadManager
 */
public final class FxmlKit {

    private static final String ROOT_PACKAGE_NAME = "com.dlsc.fxmlkit";

    private static volatile Level globalLogLevel = Level.WARNING;
    private static volatile DiAdapter globalDiAdapter = null;
    private static volatile boolean autoAttachStyles = true;
    private static volatile FxmlInjectionPolicy fxmlInjectionPolicy = FxmlInjectionPolicy.EXPLICIT_ONLY;

    private static final Set<Class<?>> INCLUDE_NODE_TYPES = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> EXCLUDE_NODE_TYPES = ConcurrentHashMap.newKeySet();

    private static final List<String> DEFAULT_SKIP_PACKAGE_PREFIXES = List.of(
            "java.",
            "javax.",
            "javafx.",
            "jdk.",
            "sun.",
            "com.sun."
    );

    private static final List<String> SKIP_PACKAGE_PREFIXES = new ArrayList<>(DEFAULT_SKIP_PACKAGE_PREFIXES);

    private FxmlKit() {
    }

    // ========== Hot Reload API ==========

    /**
     * Enables hot reload functionality for development.
     *
     * <p>When enabled, FxmlKit automatically monitors FXML files for changes and
     * reloads views when modifications are detected. This is intended for development
     * use only and should be disabled in production.
     *
     * <p>Example usage:
     * <pre>{@code
     * public class MyApp extends Application {
     *     public void start(Stage stage) {
     *         if (isDevelopmentMode()) {
     *             FxmlKit.enableHotReload();
     *         }
     *         MainView view = new MainView();
     *         stage.setScene(new Scene(view));
     *         stage.show();
     *     }
     *
     *     public void stop() {
     *         FxmlKit.disableHotReload();
     *     }
     *
     *     private boolean isDevelopmentMode() {
     *         return Boolean.getBoolean("fxmlkit.hotreload") ||
     *                "development".equalsIgnoreCase(System.getProperty("env"));
     *     }
     * }
     * }</pre>
     *
     * @see #disableHotReload()
     * @see #isHotReloadEnabled()
     */
    public static void enableHotReload() {
        HotReloadManager.getInstance().enable();
    }

    /**
     * Disables hot reload and releases associated resources.
     *
     * @see #enableHotReload()
     */
    public static void disableHotReload() {
        HotReloadManager.getInstance().disable();
    }

    /**
     * Checks if hot reload is currently enabled.
     *
     * @return true if hot reload is enabled
     */
    public static boolean isHotReloadEnabled() {
        return HotReloadManager.getInstance().isEnabled();
    }

    // ========== Logging Configuration ==========

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
        Logger.getLogger(HotReloadManager.class.getName()).setLevel(level);
    }

    /**
     * Gets the current global logging level.
     *
     * @return the current logging level
     */
    public static Level getLogLevel() {
        return globalLogLevel;
    }

    // ========== Dependency Injection Configuration ==========

    /**
     * Sets the global dependency injection adapter.
     *
     * @param adapter the DI adapter to use, or null for zero-config mode
     */
    public static void setDiAdapter(DiAdapter adapter) {
        globalDiAdapter = adapter;
    }

    /**
     * Gets the current global dependency injection adapter.
     *
     * @return the current DI adapter, or null if none configured
     */
    public static DiAdapter getDiAdapter() {
        return globalDiAdapter;
    }

    // ========== Stylesheet Configuration ==========

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

    // ========== Injection Policy Configuration ==========

    /**
     * Sets the FXML node injection policy.
     *
     * @param policy the policy to use (must not be null)
     */
    public static void setFxmlInjectionPolicy(FxmlInjectionPolicy policy) {
        fxmlInjectionPolicy = Objects.requireNonNull(policy, "Policy cannot be null");
    }

    /**
     * Gets the current FXML node injection policy.
     *
     * @return the current policy
     */
    public static FxmlInjectionPolicy getFxmlInjectionPolicy() {
        return fxmlInjectionPolicy;
    }

    // ========== Package Prefix Configuration ==========

    /**
     * Gets the list of package prefixes to skip during injection.
     *
     * @return mutable list of skip package prefixes
     */
    public static List<String> getSkipPackagePrefixes() {
        return SKIP_PACKAGE_PREFIXES;
    }

    // ========== Node Type Configuration ==========

    /**
     * Gets the set of node types to exclude from injection.
     *
     * @return mutable thread-safe set of excluded node types
     */
    public static Set<Class<?>> getExcludeNodeTypes() {
        return EXCLUDE_NODE_TYPES;
    }

    /**
     * Gets the set of node types to include for injection.
     *
     * @return mutable thread-safe set of included node types
     */
    public static Set<Class<?>> getIncludeNodeTypes() {
        return INCLUDE_NODE_TYPES;
    }

    // ========== Reset Configuration ==========

    /**
     * Resets all FxmlKit configuration to defaults.
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
        HotReloadManager.getInstance().reset();
    }
}