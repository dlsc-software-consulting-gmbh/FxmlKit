package com.dlsc.fxmlkit.fxml;

import com.dlsc.fxmlkit.core.DiAdapter;
import com.dlsc.fxmlkit.core.LiteDiAdapter;
import com.dlsc.fxmlkit.hotreload.HotReloadManager;
import com.dlsc.fxmlkit.hotreload.GlobalCssMonitor;
import com.dlsc.fxmlkit.hotreload.StylesheetUriConverter;
import com.dlsc.fxmlkit.policy.FxmlInjectionPolicy;
import javafx.beans.property.StringProperty;

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
 * <p>Provides global configuration for dependency injection, hot reload,
 * and stylesheet management.
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

    static {
        configureDefaultLogging();
    }

    private FxmlKit() {
    }

    /**
     * Enables development mode with FXML and CSS hot reload.
     *
     * <p>CSS hot reload covers all stylesheet types including User Agent Stylesheets.
     *
     * <p><b>Note:</b> Control {@code getUserAgentStylesheet()} hot reload is
     * disabled by default due to style priority changes. Enable separately via
     * {@link #setControlUAHotReloadEnabled(boolean)} if needed.
     *
     * @see #setFxmlHotReloadEnabled(boolean)
     * @see #setCssHotReloadEnabled(boolean)
     * @see #setControlUAHotReloadEnabled(boolean)
     */
    public static void enableDevelopmentMode() {
        setFxmlHotReloadEnabled(true);
        setCssHotReloadEnabled(true);
    }

    /**
     * Disables development mode and releases hot reload resources.
     */
    public static void disableDevelopmentMode() {
        setFxmlHotReloadEnabled(false);
        setCssHotReloadEnabled(false);
    }

    /**
     * Enables or disables FXML hot reload.
     *
     * @param enabled true to enable, false to disable
     */
    public static void setFxmlHotReloadEnabled(boolean enabled) {
        HotReloadManager.getInstance().setFxmlHotReloadEnabled(enabled);
    }

    /**
     * Returns whether FXML hot reload is enabled.
     *
     * @return true if enabled
     */
    public static boolean isFxmlHotReloadEnabled() {
        return HotReloadManager.getInstance().isFxmlHotReloadEnabled();
    }

    /**
     * Enables or disables CSS hot reload.
     *
     * <p>When enabled, monitors all stylesheets for changes:
     * <ul>
     *   <li>Scene and Parent stylesheets</li>
     *   <li>User Agent Stylesheets (Application, Scene, SubScene)</li>
     * </ul>
     *
     * <p><b>Note:</b> Control {@code getUserAgentStylesheet()} hot reload
     * requires separate enablement via {@link #setControlUAHotReloadEnabled(boolean)}.
     *
     * @param enabled true to enable, false to disable
     */
    public static void setCssHotReloadEnabled(boolean enabled) {
        HotReloadManager.getInstance().setCssHotReloadEnabled(enabled);
    }

    /**
     * Returns whether CSS hot reload is enabled.
     *
     * @return true if enabled
     */
    public static boolean isCssHotReloadEnabled() {
        return HotReloadManager.getInstance().isCssHotReloadEnabled();
    }

    /**
     * Enables or disables hot reload for Control {@code getUserAgentStylesheet()}.
     *
     * <p><b>Warning:</b> Enabling this feature changes CSS priority semantics!
     *
     * <p>To support hot reload, custom control User Agent Stylesheets are "promoted"
     * to the control's {@code getStylesheets()} list. This causes them to become
     * author-level stylesheets instead of UA-level stylesheets, which may alter
     * style cascade behavior:
     * <ul>
     *   <li>Original UA stylesheet: lowest priority, easily overridden</li>
     *   <li>Promoted stylesheet: author-level priority, may override user styles</li>
     * </ul>
     *
     * <p><b>Recommendation:</b> Only enable during development for controls that
     * need hot reload. Keep disabled in production or when style priority matters.
     *
     * <p>Default: disabled
     *
     * @param enabled true to enable (with priority change warning), false to disable
     * @see #isControlUAHotReloadEnabled()
     */
    public static void setControlUAHotReloadEnabled(boolean enabled) {
        HotReloadManager.getInstance().getGlobalCssMonitor().setControlUAHotReloadEnabled(enabled);
    }

    /**
     * Returns whether Control {@code getUserAgentStylesheet()} hot reload is enabled.
     *
     * @return true if enabled
     * @see #setControlUAHotReloadEnabled(boolean)
     */
    public static boolean isControlUAHotReloadEnabled() {
        return HotReloadManager.getInstance().getGlobalCssMonitor().isControlUAHotReloadEnabled();
    }

    /**
     * Sets the debounce time for hot reload events.
     *
     * <p>The debounce window prevents duplicate reloads when editors or IDEs
     * trigger multiple file system events for a single save operation. Some editors
     * (especially on certain operating systems) may trigger 2-3 events per save.
     *
     * <p>Adjust this value based on your development environment:
     * <ul>
     *   <li>If experiencing duplicate reloads: increase to 1000-2000ms</li>
     *   <li>If reload feels sluggish: decrease (minimum ~200ms recommended)</li>
     * </ul>
     *
     * <p>Default: 500ms
     *
     * <pre>{@code
     * // Example: increase debounce for slower systems
     * FxmlKit.setHotReloadDebounceMillis(1500);
     * }</pre>
     *
     * @param millis the debounce time in milliseconds (must be positive)
     * @throws IllegalArgumentException if millis is not positive
     * @see #getHotReloadDebounceMillis()
     */
    public static void setHotReloadDebounceMillis(long millis) {
        HotReloadManager.getInstance().setDebounceMillis(millis);
    }

    /**
     * Returns the current debounce time for hot reload events.
     *
     * @return the debounce time in milliseconds
     * @see #setHotReloadDebounceMillis(long)
     */
    public static long getHotReloadDebounceMillis() {
        return HotReloadManager.getInstance().getDebounceMillis();
    }

    /**
     * Returns the Application-level User Agent Stylesheet property.
     *
     * <p>Use this instead of {@link javafx.application.Application#setUserAgentStylesheet(String)}
     * to enable CSS hot reload and reactive binding.
     *
     * <pre>{@code
     * // Direct setting
     * FxmlKit.setApplicationUserAgentStylesheet("/styles/dark.css");
     *
     * // Bind to theme selector
     * FxmlKit.applicationUserAgentStylesheetProperty()
     *     .bind(themeComboBox.valueProperty());
     * }</pre>
     *
     * @return the Application UA stylesheet property
     */
    public static StringProperty applicationUserAgentStylesheetProperty() {
        return HotReloadManager.getInstance().getGlobalCssMonitor().applicationUserAgentStylesheetProperty();
    }

    /**
     * Sets the Application-level User Agent Stylesheet.
     *
     * @param url the stylesheet URL, or null to clear
     * @see #applicationUserAgentStylesheetProperty()
     */
    public static void setApplicationUserAgentStylesheet(String url) {
        applicationUserAgentStylesheetProperty().set(url);
    }

    /**
     * Returns the current Application-level User Agent Stylesheet.
     *
     * @return the current stylesheet URL, or null if not set
     */
    public static String getApplicationUserAgentStylesheet() {
        return applicationUserAgentStylesheetProperty().get();
    }

    /**
     * Sets the logging level for all FxmlKit components.
     *
     * @param level the logging level
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

    /**
     * Sets the global dependency injection adapter.
     *
     * @param adapter the DI adapter, or null for zero-config mode
     */
    public static void setDiAdapter(DiAdapter adapter) {
        globalDiAdapter = adapter;
    }

    /**
     * Returns the current global DI adapter.
     *
     * @return the current DI adapter, or null if none configured
     */
    public static DiAdapter getDiAdapter() {
        return globalDiAdapter;
    }

    /**
     * Sets whether stylesheets should be automatically attached.
     *
     * @param enabled true to enable auto-attach
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

    /**
     * Sets the FXML node injection policy.
     *
     * @param policy the policy
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

    /**
     * Returns the list of package prefixes to skip during injection.
     *
     * @return mutable list of skip package prefixes
     */
    public static List<String> getSkipPackagePrefixes() {
        return SKIP_PACKAGE_PREFIXES;
    }

    /**
     * Returns the set of node types to exclude from injection.
     *
     * @return mutable set of excluded node types
     */
    public static Set<Class<?>> getExcludeNodeTypes() {
        return EXCLUDE_NODE_TYPES;
    }

    /**
     * Returns the set of node types to include for injection.
     *
     * @return mutable set of included node types
     */
    public static Set<Class<?>> getIncludeNodeTypes() {
        return INCLUDE_NODE_TYPES;
    }

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