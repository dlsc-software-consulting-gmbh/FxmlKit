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
 * <p>FxmlKit supports automatic hot reload for FXML and CSS files during development.
 *
 * <h3>Quick Start (Development Mode)</h3>
 * <pre>{@code
 * // Enable all development features with one call
 * FxmlKit.enableDevelopmentMode();
 *
 * // Views automatically register for hot reload
 * MainView view = new MainView();
 * }</pre>
 *
 * <h3>Fine-Grained Control</h3>
 * <pre>{@code
 * // Control FXML and CSS hot reload independently
 * FxmlKit.setFxmlHotReloadEnabled(true);
 * FxmlKit.setCssHotReloadEnabled(true);
 *
 * // Or use CSSFX for CSS hot reload
 * FxmlKit.setFxmlHotReloadEnabled(true);
 * FxmlKit.setCssHotReloadEnabled(false);  // Disable built-in CSS handling
 * CSSFX.start();                          // Use CSSFX instead
 * }</pre>
 *
 * <h3>What Can Be Hot-Reloaded</h3>
 * <table border="1">
 *   <tr><th>File Type</th><th>Support</th><th>Notes</th></tr>
 *   <tr><td>.fxml</td><td>Yes</td><td>Full reload, loses runtime state</td></tr>
 *   <tr><td>.css, .bss</td><td>Yes</td><td>Stylesheet refresh, preserves state</td></tr>
 *   <tr><td>.properties</td><td>No</td><td>Java ResourceBundle caching limitation</td></tr>
 *   <tr><td>.png, .jpg, etc.</td><td>No</td><td>JavaFX Image caching limitation</td></tr>
 * </table>
 *
 * <h2>Three-Tier Progressive Design</h2>
 *
 * <h3>Tier 1: Zero-Configuration Mode (No DI)</h3>
 * <pre>{@code
 * // No configuration needed!
 * MainView view = new MainView();
 * stage.setScene(new Scene(view));
 * }</pre>
 *
 * <h3>Tier 2: Global DI Mode (Single-User Desktop)</h3>
 * <pre>{@code
 * // One-time setup
 * LiteDiAdapter di = new LiteDiAdapter();
 * di.bindInstance(UserService.class, new UserService());
 * FxmlKit.setDiAdapter(di);
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

    // ========== Development Mode ==========

    /**
     * Enables development mode with all hot reload features.
     *
     * <p>This is a convenience method that enables both FXML and CSS hot reload.
     * Equivalent to:
     * <pre>{@code
     * FxmlKit.setFxmlHotReloadEnabled(true);
     * FxmlKit.setCssHotReloadEnabled(true);
     * }</pre>
     *
     * <p>Call this during application startup in development mode:
     * <pre>{@code
     * public class MyApp extends Application {
     *     @Override
     *     public void start(Stage stage) {
     *         if (isDevelopmentMode()) {
     *             FxmlKit.enableDevelopmentMode();
     *         }
     *         // ...
     *     }
     *
     *     private boolean isDevelopmentMode() {
     *         return Boolean.getBoolean("fxmlkit.dev") ||
     *                "development".equalsIgnoreCase(System.getProperty("env"));
     *     }
     * }
     * }</pre>
     *
     * @see #setFxmlHotReloadEnabled(boolean)
     * @see #setCssHotReloadEnabled(boolean)
     */
    public static void enableDevelopmentMode() {
        setFxmlHotReloadEnabled(true);
        setCssHotReloadEnabled(true);
    }

    /**
     * Disables development mode and releases all hot reload resources.
     *
     * <p>This is a convenience method that disables both FXML and CSS hot reload.
     * Call this at application shutdown to release resources.
     *
     * @see #setFxmlHotReloadEnabled(boolean)
     * @see #setCssHotReloadEnabled(boolean)
     */
    public static void disableDevelopmentMode() {
        setFxmlHotReloadEnabled(false);
        setCssHotReloadEnabled(false);
    }

    // ========== FXML Hot Reload ==========

    /**
     * Enables or disables FXML hot reload.
     *
     * <p>When enabled, FxmlKit monitors .fxml files for changes and automatically
     * reloads affected views. This triggers a full view reload, which means all
     * runtime state (user input, scroll position, etc.) will be lost.
     *
     * <p>FXML hot reload also handles fx:include dependencies - when a child FXML
     * changes, all parent views that include it will also be reloaded.
     *
     * <h3>Technical Details</h3>
     * <ul>
     *   <li>Uses native file system WatchService for minimal CPU overhead</li>
     *   <li>Monitors both source (src/main/resources) and target (target/classes) directories</li>
     *   <li>Includes 500ms debouncing to prevent duplicate reloads</li>
     *   <li>Automatically syncs source changes to target directory</li>
     * </ul>
     *
     * @param enabled true to enable FXML hot reload, false to disable
     * @see #isFxmlHotReloadEnabled()
     * @see #setCssHotReloadEnabled(boolean)
     */
    public static void setFxmlHotReloadEnabled(boolean enabled) {
        HotReloadManager.getInstance().setFxmlHotReloadEnabled(enabled);
    }

    /**
     * Checks if FXML hot reload is currently enabled.
     *
     * @return true if FXML hot reload is enabled
     * @see #setFxmlHotReloadEnabled(boolean)
     */
    public static boolean isFxmlHotReloadEnabled() {
        return HotReloadManager.getInstance().isFxmlHotReloadEnabled();
    }

    // ========== CSS Hot Reload ==========

    /**
     * Enables or disables CSS/BSS hot reload.
     *
     * <p>When enabled, FxmlKit monitors .css and .bss files for changes and
     * refreshes stylesheets without a full view reload. This preserves all
     * runtime state, making it ideal for rapid style iteration.
     *
     * <p>CSS hot reload only affects the FXML that directly uses the stylesheet
     * (via same-name convention). It does not propagate to parent FXMLs that
     * include the affected view via fx:include.
     *
     * <h3>Using CSSFX Instead</h3>
     * <p>For advanced CSS scenarios (shared stylesheets, Scene-level styles),
     * you can use CSSFX instead of the built-in CSS hot reload:
     * <pre>{@code
     * FxmlKit.setFxmlHotReloadEnabled(true);
     * FxmlKit.setCssHotReloadEnabled(false);  // Disable built-in CSS handling
     * CSSFX.start();                          // Use CSSFX instead
     * }</pre>
     *
     * @param enabled true to enable CSS hot reload, false to disable
     * @see #isCssHotReloadEnabled()
     * @see #setFxmlHotReloadEnabled(boolean)
     */
    public static void setCssHotReloadEnabled(boolean enabled) {
        HotReloadManager.getInstance().setCssHotReloadEnabled(enabled);
    }

    /**
     * Checks if CSS hot reload is currently enabled.
     *
     * @return true if CSS hot reload is enabled
     * @see #setCssHotReloadEnabled(boolean)
     */
    public static boolean isCssHotReloadEnabled() {
        return HotReloadManager.getInstance().isCssHotReloadEnabled();
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