package com.dlsc.fxmlkit.fxml;

import com.dlsc.fxmlkit.core.DiAdapter;
import com.dlsc.fxmlkit.core.LiteDiAdapter;
import com.dlsc.fxmlkit.hotreload.HotReloadManager;
import com.dlsc.fxmlkit.hotreload.GlobalCssMonitor;
import com.dlsc.fxmlkit.hotreload.StylesheetUriConverter;
import com.dlsc.fxmlkit.policy.FxmlInjectionPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central configuration facade for FxmlKit framework.
 *
 * <p>Provides global configuration for the three-tier progressive design of FxmlKit.
 *
 * <p>Hot reload can be enabled via {@code enableDevelopmentMode()} or controlled
 * independently via {@code setFxmlHotReloadEnabled}, {@code setCssHotReloadEnabled},
 * and {@code setGlobalCssMonitoring}.
 *
 * <p>CSS monitoring modes:
 * <ul>
 *   <li>Component-based (default): monitors CSS files matching FXML names</li>
 *   <li>Global: monitors all stylesheets including Scene-level and shared styles</li>
 * </ul>
 *
 * @see FxmlKitLoader
 * @see FxmlViewProvider
 * @see DiAdapter
 * @see HotReloadManager
 * @see GlobalCssMonitor
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

    static {
        configureDefaultLogging();
    }

    private FxmlKit() {
    }

    // ========== Development Mode ==========

    /**
     * Enables development mode with FXML and CSS hot reload.
     *
     * <p>Equivalent to calling {@code setFxmlHotReloadEnabled(true)} and
     * {@code setCssHotReloadEnabled(true)}.
     *
     * <p>Does not enable global CSS monitoring. See {@link #setGlobalCssMonitoring(boolean)}.
     *
     * @see #setFxmlHotReloadEnabled(boolean)
     * @see #setCssHotReloadEnabled(boolean)
     */
    public static void enableDevelopmentMode() {
        setFxmlHotReloadEnabled(true);
        setCssHotReloadEnabled(true);
    }

    /**
     * Enables development mode with FXML, CSS, and global CSS monitoring.
     *
     * <p>Equivalent to calling {@code setFxmlHotReloadEnabled(true)},
     * {@code setCssHotReloadEnabled(true)}, and {@code setGlobalCssMonitoring(true)}.
     *
     * @see #enableDevelopmentMode()
     * @see #setGlobalCssMonitoring(boolean)
     */
    public static void enableFullDevelopmentMode() {
        setFxmlHotReloadEnabled(true);
        setCssHotReloadEnabled(true);
        setGlobalCssMonitoring(true);
    }

    /**
     * Disables development mode and releases hot reload resources.
     *
     * @see #setFxmlHotReloadEnabled(boolean)
     * @see #setCssHotReloadEnabled(boolean)
     * @see #setGlobalCssMonitoring(boolean)
     */
    public static void disableDevelopmentMode() {
        setFxmlHotReloadEnabled(false);
        setCssHotReloadEnabled(false);
        setGlobalCssMonitoring(false);
    }

    /**
     * Enables or disables FXML hot reload.
     *
     * <p>When enabled, monitors .fxml files for changes and automatically reloads
     * affected views. Full reload loses runtime state (user input, scroll position).
     * Changes propagate through fx:include dependencies.
     *
     * <p>Uses WatchService for file monitoring with 500ms debouncing. Monitors both
     * source and target directories, automatically syncing changes.
     *
     * @param enabled true to enable FXML hot reload, false to disable
     * @see #isFxmlHotReloadEnabled()
     * @see #setCssHotReloadEnabled(boolean)
     */
    public static void setFxmlHotReloadEnabled(boolean enabled) {
        HotReloadManager.getInstance().setFxmlHotReloadEnabled(enabled);
    }

    /**
     * Returns whether FXML hot reload is currently enabled.
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
     * <p>When enabled, monitors .css and .bss files for changes and refreshes
     * stylesheets without full view reload. Runtime state is preserved.
     *
     * <p>By default, only monitors stylesheets with the same name as registered
     * FXML files (e.g., UserView.fxml → UserView.css). Scene-level and shared
     * stylesheets are monitored when global CSS monitoring is enabled.
     *
     * @param enabled true to enable CSS hot reload, false to disable
     * @see #isCssHotReloadEnabled()
     * @see #setGlobalCssMonitoring(boolean)
     */
    public static void setCssHotReloadEnabled(boolean enabled) {
        HotReloadManager.getInstance().setCssHotReloadEnabled(enabled);
    }

    /**
     * Returns whether CSS hot reload is currently enabled.
     *
     * @return true if CSS hot reload is enabled
     * @see #setCssHotReloadEnabled(boolean)
     */
    public static boolean isCssHotReloadEnabled() {
        return HotReloadManager.getInstance().isCssHotReloadEnabled();
    }

    // ========== Global CSS Monitoring ==========

    /**
     * Enables or disables global CSS monitoring.
     *
     * <p>When enabled, monitors all stylesheets across the scene graph:
     * <ul>
     *   <li>Scene-level stylesheets</li>
     *   <li>All Parent node stylesheets</li>
     *   <li>Dynamically added stylesheets</li>
     * </ul>
     *
     * <p>When disabled (default), only monitors CSS files matching FXML names.
     *
     * <p>Has no effect unless CSS hot reload is also enabled.
     *
     * @param enabled true to enable global CSS monitoring
     * @see #isGlobalCssMonitoring()
     * @see #setCssHotReloadEnabled(boolean)
     */
    public static void setGlobalCssMonitoring(boolean enabled) {
        HotReloadManager.getInstance().setGlobalCssMonitoring(enabled);
    }

    /**
     * Returns whether global CSS monitoring is currently enabled.
     *
     * @return true if global CSS monitoring is enabled
     * @see #setGlobalCssMonitoring(boolean)
     */
    public static boolean isGlobalCssMonitoring() {
        return HotReloadManager.getInstance().isGlobalCssMonitoring();
    }

    // ========== Logging Configuration ==========

    /**
     * Sets the logging level for all FxmlKit components.
     *
     * <p>This affects all loggers under the {@code com.dlsc.fxmlkit} package
     * and their associated handlers.
     *
     * <p>Default level is {@link Level#WARNING}.
     *
     * @param level the logging level (must not be null)
     * @throws NullPointerException if level is null
     */
    public static void setLogLevel(Level level) {
        globalLogLevel = Objects.requireNonNull(level, "Log level cannot be null");
        applyLogLevel(level);
    }

    /**
     * Returns the current global logging level.
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
     * Returns the current global dependency injection adapter.
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
     * Returns whether automatic stylesheet attachment is enabled.
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
     * @throws NullPointerException if policy is null
     */
    public static void setFxmlInjectionPolicy(FxmlInjectionPolicy policy) {
        fxmlInjectionPolicy = Objects.requireNonNull(policy, "Policy cannot be null");
    }

    /**
     * Returns the current FXML node injection policy.
     *
     * @return the current policy
     */
    public static FxmlInjectionPolicy getFxmlInjectionPolicy() {
        return fxmlInjectionPolicy;
    }

    // ========== Package Prefix Configuration ==========

    /**
     * Returns the list of package prefixes to skip during injection.
     *
     * @return mutable list of skip package prefixes
     */
    public static List<String> getSkipPackagePrefixes() {
        return SKIP_PACKAGE_PREFIXES;
    }

    // ========== Node Type Configuration ==========

    /**
     * Returns the set of node types to exclude from injection.
     *
     * @return mutable thread-safe set of excluded node types
     */
    public static Set<Class<?>> getExcludeNodeTypes() {
        return EXCLUDE_NODE_TYPES;
    }

    /**
     * Returns the set of node types to include for injection.
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
        applyLogLevel(Level.WARNING);
    }

    // ========== Internal Methods ==========

    /**
     * Configures default logging when FxmlKit class is loaded.
     */
    private static void configureDefaultLogging() {
        try {
            applyLogLevel(Level.WARNING);
        } catch (Exception e) {
            System.err.println("Warning: Failed to configure FxmlKit logging: " + e.getMessage());
        }
    }

    /**
     * Applies the log level to FxmlKit loggers and their handlers.
     */
    private static void applyLogLevel(Level level) {
        Logger rootLogger = Logger.getLogger(ROOT_PACKAGE_NAME);
        rootLogger.setLevel(level);

        configureHandlers(rootLogger, level);

        if (rootLogger.getHandlers().length == 0 && rootLogger.getParent() != null) {
            configureHandlers(rootLogger.getParent(), level);
        }

        configureComponentLogger(FxmlKitLoader.class, level);
        configureComponentLogger(FxmlViewProvider.class, level);
        configureComponentLogger(LiteDiAdapter.class, level);
        configureComponentLogger(HotReloadManager.class, level);
        configureComponentLogger(GlobalCssMonitor.class, level);
        configureComponentLogger(StylesheetUriConverter.class, level);
    }

    /**
     * Configures all handlers for a logger to use the specified level.
     */
    private static void configureHandlers(Logger logger, Level level) {
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(level);
        }
    }

    /**
     * Configures logger for a specific component class.
     */
    private static void configureComponentLogger(Class<?> componentClass, Level level) {
        Logger logger = Logger.getLogger(componentClass.getName());
        logger.setLevel(level);
    }
}