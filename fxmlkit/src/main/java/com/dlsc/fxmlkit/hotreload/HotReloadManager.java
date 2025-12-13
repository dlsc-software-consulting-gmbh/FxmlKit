package com.dlsc.fxmlkit.hotreload;

import com.dlsc.fxmlkit.fxml.FxmlDependencyAnalyzer;
import javafx.application.Platform;
import javafx.util.Callback;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central manager for FXML and CSS hot reload functionality (Simplified Version).
 *
 * <p>This singleton class coordinates file monitoring, dependency tracking, and view reloading
 * during development. It provides zero-configuration hot reload support for FxmlKit views.
 *
 * <h2>Key Design Changes (v2)</h2>
 * <ul>
 *   <li><b>Direct source loading</b> - In development mode, FXML/CSS are loaded directly
 *       from source directories, eliminating the need for target synchronization</li>
 *   <li><b>URI-based conversion</b> - Uses {@code Callback<String, Path>} for simple
 *       string-based URI conversion instead of complex project structure inference</li>
 *   <li><b>Stateless architecture</b> - No cached project root or monitored roots tracking</li>
 *   <li><b>Per-file monitoring</b> - Only monitors files that are actually used</li>
 * </ul>
 *
 * <h2>Supported File Types</h2>
 * <ul>
 *   <li>{@code .fxml} - Full reload (loses runtime state)</li>
 *   <li>{@code .css}, {@code .bss} - Stylesheet refresh (preserves runtime state)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Enable hot reload at application startup (before creating views!)
 * FxmlKit.enableDevelopmentMode();
 *
 * // Components auto-register when created
 * MainView view = new MainView();  // Auto-registered, loads from source
 *
 * // Optional: Add custom converter for non-standard layouts
 * FxmlKit.addSourcePathConverter(uri -> {
 *     if (uri.contains("custom-output")) {
 *         return Path.of(uri.replace("custom-output", "custom-source"));
 *     }
 *     return null;
 * });
 * }</pre>
 *
 * <h2>Architecture</h2>
 * <pre>
 * Development Mode Enabled
 *     ↓
 * FXML/CSS loaded directly from source (via FxmlKitLoader)
 *     ↓
 * FileWatcher monitors source files
 *     ↓
 * File Change Detected
 *     ↓
 * Debounce (200ms)
 *     ↓
 * Dependency Analysis (BFS for fx:include)
 *     ↓
 * Platform.runLater()
 *     ↓
 * Component.reload() on JavaFX Thread
 * </pre>
 *
 * @see HotReloadable
 * @see SourcePathConverters
 * @see FileWatcher
 */
public final class HotReloadManager {

    private static final Logger logger = Logger.getLogger(HotReloadManager.class.getName());

    /**
     * Singleton instance.
     */
    private static final HotReloadManager INSTANCE = new HotReloadManager();

    /**
     * Source path converters (tried in order, first match wins).
     */
    private final List<Callback<String, Path>> converters = new CopyOnWriteArrayList<>();

    /**
     * File watcher for source file monitoring.
     */
    private final FileWatcher fileWatcher = new FileWatcher();

    /**
     * Global CSS monitor (lazy initialized to avoid circular dependency).
     */
    private GlobalCssMonitor cssMonitor;

    /**
     * Maps resource paths to registered components.
     * Key: classpath-relative path (e.g., "com/example/View.fxml")
     * Value: List of weak references to components using that FXML
     */
    private final Map<String, List<WeakReference<HotReloadable>>> componentsByPath = new ConcurrentHashMap<>();

    /**
     * Reverse dependency graph: child FXML → set of parent FXMLs.
     * Used to propagate changes through fx:include hierarchies.
     */
    private final Map<String, Set<String>> dependencyGraph = new ConcurrentHashMap<>();

    /**
     * Tracks FXMLs that have been analyzed for dependencies.
     * Prevents redundant DOM parsing when the same FXML is used by multiple components.
     */
    private final Set<String> analyzedFxmls = ConcurrentHashMap.newKeySet();

    /**
     * Maps CSS/BSS paths to FXML paths that use them.
     */
    private final Map<String, Set<String>> stylesheetToFxml = new ConcurrentHashMap<>();

    /**
     * Whether FXML hot reload is enabled.
     */
    private volatile boolean fxmlHotReloadEnabled = false;

    /**
     * Whether CSS hot reload is enabled.
     */
    private volatile boolean cssHotReloadEnabled = false;

    private HotReloadManager() {
        // Initialize with default converters
        converters.addAll(SourcePathConverters.DEFAULTS);
    }

    /**
     * Returns the singleton instance.
     *
     * @return the HotReloadManager instance
     */
    public static HotReloadManager getInstance() {
        return INSTANCE;
    }

    /**
     * Adds a custom source path converter with highest priority.
     *
     * <p>Custom converters are tried before built-in converters.
     *
     * @param converter the converter to add
     */
    public void addConverter(Callback<String, Path> converter) {
        if (converter != null) {
            converters.add(0, converter);  // Add at front for highest priority
        }
    }

    /**
     * Enables or disables FXML hot reload.
     *
     * @param enabled true to enable
     */
    public synchronized void setFxmlHotReloadEnabled(boolean enabled) {
        if (this.fxmlHotReloadEnabled == enabled) {
            return;
        }
        this.fxmlHotReloadEnabled = enabled;
        logger.log(Level.FINE, "FXML hot reload {0}", enabled ? "enabled" : "disabled");
        updateState();
    }

    /**
     * Enables or disables CSS hot reload.
     *
     * @param enabled true to enable
     */
    public synchronized void setCssHotReloadEnabled(boolean enabled) {
        if (this.cssHotReloadEnabled == enabled) {
            return;
        }
        this.cssHotReloadEnabled = enabled;
        logger.log(Level.FINE, "CSS hot reload {0}", enabled ? "enabled" : "disabled");
        updateState();
    }

    /**
     * Returns whether FXML hot reload is enabled.
     */
    public boolean isFxmlHotReloadEnabled() {
        return fxmlHotReloadEnabled;
    }

    /**
     * Returns whether CSS hot reload is enabled.
     */
    public boolean isCssHotReloadEnabled() {
        return cssHotReloadEnabled;
    }

    /**
     * Returns whether any hot reload feature is enabled.
     */
    public boolean isEnabled() {
        return fxmlHotReloadEnabled || cssHotReloadEnabled;
    }

    /**
     * Returns the GlobalCssMonitor instance (lazy initialized).
     */
    public GlobalCssMonitor getGlobalCssMonitor() {
        if (cssMonitor == null) {
            cssMonitor = new GlobalCssMonitor();
        }
        return cssMonitor;
    }

    /**
     * Converts a runtime URI to a source file path.
     *
     * <p>Tries all registered converters in order.
     *
     * @param runtimeUri the runtime URI
     * @return the source path, or null if no converter matched
     */
    public Path toSourcePath(String runtimeUri) {
        if (runtimeUri == null) {
            return null;
        }
        // Strip query string
        int queryIndex = runtimeUri.indexOf('?');
        if (queryIndex >= 0) {
            runtimeUri = runtimeUri.substring(0, queryIndex);
        }
        for (Callback<String, Path> converter : converters) {
            Path result = converter.call(runtimeUri);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * Resolves a URI to a watchable file path.
     *
     * <p>This method handles two cases:
     * <ol>
     *   <li>URI from target directory → converted to source path</li>
     *   <li>URI already pointing to source → used directly</li>
     * </ol>
     *
     * <p>This is needed because when FXML is loaded from source, its relative
     * references (like {@code @style.css}) also resolve to source paths,
     * which won't match any converter pattern.
     *
     * @param uri the resource URI
     * @return the watchable file path, or null if not resolvable
     */
    private Path resolveWatchablePath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }

        // Strip query string (for cache-busting timestamps)
        int queryIndex = uri.indexOf('?');
        if (queryIndex >= 0) {
            uri = uri.substring(0, queryIndex);
        }

        // 1. Try converting via converters (handles target -> source)
        Path sourcePath = toSourcePath(uri);
        if (sourcePath != null) {
            return sourcePath;
        }

        // 2. Check if it's already a valid file:// path (handles source -> source)
        if (uri.startsWith("file:")) {
            try {
                Path directPath = Path.of(URI.create(uri));
                if (Files.exists(directPath)) {
                    logger.log(Level.FINE, "Using direct path: {0}", directPath);
                    return directPath;
                }
            } catch (Exception ignored) {
                // Invalid URI format
            }
        }

        // 3. Handle classpath-style URIs (e.g., /com/example/style.css)
        if (!uri.contains(":")) {
            Path classpathPath = resolveClasspathUri(uri);
            if (classpathPath != null) {
                return classpathPath;
            }
        }

        return null;
    }

    /**
     * Resolves a classpath URI to a source file path.
     */
    private Path resolveClasspathUri(String classpathUri) {
        String resourcePath = classpathUri.startsWith("/")
                ? classpathUri.substring(1)
                : classpathUri;

        // Try to get the actual URL via ClassLoader
        URL url = null;
        ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
        if (contextCL != null) {
            url = contextCL.getResource(resourcePath);
        }
        if (url == null) {
            url = HotReloadManager.class.getClassLoader().getResource(resourcePath);
        }

        if (url == null || !"file".equals(url.getProtocol())) {
            return null;
        }

        // Convert to source path
        String fileUri = url.toExternalForm();
        Path sourcePath = toSourcePath(fileUri);
        if (sourcePath != null) {
            logger.log(Level.FINE, "Resolved classpath URI: {0} -> {1}",
                    new Object[]{classpathUri, sourcePath});
            return sourcePath;
        }

        // Try direct path if converters don't match
        try {
            Path directPath = Path.of(url.toURI());
            if (Files.exists(directPath)) {
                return directPath;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    /**
     * Monitors a stylesheet for changes.
     *
     * @param uri       the stylesheet URI
     * @param onChanged callback when the stylesheet changes
     */
    public void monitorStylesheet(String uri, Runnable onChanged) {
        if (!cssHotReloadEnabled) {
            return;
        }

        Path path = resolveWatchablePath(uri);
        if (path != null) {
            fileWatcher.watch(path, () -> Platform.runLater(onChanged));
        }
    }

    /**
     * Registers a component for hot reload monitoring.
     *
     * @param component the component to register
     */
    public void register(HotReloadable component) {
        if (!isEnabled()) {
            return;
        }

        String resourcePath = component.getFxmlResourcePath();
        if (resourcePath == null || resourcePath.isEmpty()) {
            logger.log(Level.WARNING, "Cannot register component with null/empty resource path: {0}",
                    component.getClass().getName());
            return;
        }

        // Add to component registry
        componentsByPath.computeIfAbsent(resourcePath, k -> new CopyOnWriteArrayList<>())
                .add(new WeakReference<>(component));

        logger.log(Level.FINE, "Registered component: {0} -> {1}",
                new Object[]{component.getClass().getSimpleName(), resourcePath});

        // Build dependency graph for fx:include
        buildDependencyGraph(component);

        // Build stylesheet mapping
        buildStylesheetMapping(component);

        // Set up file monitoring
        if (fxmlHotReloadEnabled) {
            URL fxmlUrl = component.getFxmlUrl();
            if (fxmlUrl != null) {
                Path sourcePath = resolveWatchablePath(fxmlUrl.toExternalForm());
                if (sourcePath != null) {
                    fileWatcher.watch(sourcePath, () -> reloadComponents(resourcePath));
                }
            }
        }
    }

    /**
     * Updates internal state based on configuration.
     */
    private void updateState() {
        if (isEnabled()) {
            fileWatcher.start();
            if (cssHotReloadEnabled) {
                getGlobalCssMonitor().startMonitoring();
            }
        } else {
            fileWatcher.stop();
            if (cssMonitor != null) {
                cssMonitor.stopMonitoring();
            }
        }
    }

    /**
     * Reloads all components affected by a change.
     */
    private void reloadComponents(String resourcePath) {
        // Re-analyze dependencies if this is a direct component FXML
        // (fx:include structure may have changed since last analysis)
        reAnalyzeDependenciesIfNeeded(resourcePath);

        // Find all affected paths (including parents via fx:include)
        Set<String> affectedPaths = findAffectedPaths(resourcePath);

        for (String path : affectedPaths) {
            List<WeakReference<HotReloadable>> refs = componentsByPath.get(path);
            if (refs == null) {
                continue;
            }

            // Clean up dead references
            refs.removeIf(ref -> ref.get() == null);

            for (WeakReference<HotReloadable> ref : refs) {
                HotReloadable component = ref.get();
                if (component != null) {
                    Platform.runLater(() -> {
                        try {
                            component.reload();
                            logger.log(Level.INFO, "Reloaded: {0}", path);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Reload failed: " + path, e);
                        }
                    });
                }
            }
        }
    }

    /**
     * Re-analyzes fx:include dependencies if the changed file is a registered component's FXML.
     *
     * <p>This handles the case where a user adds or removes fx:include elements at runtime.
     * Without re-analysis, newly added fx:include children would not be monitored.
     *
     * @param resourcePath the changed FXML resource path
     */
    private void reAnalyzeDependenciesIfNeeded(String resourcePath) {
        // Only re-analyze if this is a direct component FXML (not a child fx:include)
        List<WeakReference<HotReloadable>> refs = componentsByPath.get(resourcePath);
        if (refs == null || refs.isEmpty()) {
            return;
        }

        // Remove from cache to allow re-analysis
        analyzedFxmls.remove(resourcePath);

        // Re-analyze using the first available component
        for (WeakReference<HotReloadable> ref : refs) {
            HotReloadable component = ref.get();
            if (component != null) {
                logger.log(Level.FINE, "Re-analyzing fx:include dependencies for: {0}", resourcePath);
                buildDependencyGraph(component);
                break; // Only need to analyze once per FXML
            }
        }
    }

    /**
     * Builds the fx:include dependency graph for a component.
     */
    private void buildDependencyGraph(HotReloadable component) {
        URL fxmlUrl = component.getFxmlUrl();
        if (fxmlUrl == null) {
            return;
        }

        String parentPath = component.getFxmlResourcePath();

        // Skip if already analyzed (avoids redundant DOM parsing)
        if (!analyzedFxmls.add(parentPath)) {
            logger.log(Level.FINEST, "Dependencies already analyzed for: {0}", parentPath);
            return;
        }

        try {
            // Convert to source URL for analysis (target file may be outdated)
            URL urlToAnalyze = fxmlUrl;
            if (fxmlHotReloadEnabled) {
                Path sourcePath = toSourcePath(fxmlUrl.toExternalForm());
                if (sourcePath != null && Files.exists(sourcePath)) {
                    urlToAnalyze = sourcePath.toUri().toURL();
                    logger.log(Level.FINE, "Analyzing source file for dependencies: {0}", sourcePath);
                }
            }

            // FxmlDependencyAnalyzer returns Set<URI>
            Set<URI> allFxmls = FxmlDependencyAnalyzer.findAllIncludedFxmls(urlToAnalyze);

            for (URI includedUri : allFxmls) {
                String includedPath = uriToResourcePath(includedUri);
                if (includedPath != null && !includedPath.equals(parentPath)) {
                    // Add reverse dependency: child → parent
                    dependencyGraph.computeIfAbsent(includedPath, k -> ConcurrentHashMap.newKeySet())
                            .add(parentPath);

                    // Set up monitoring for the included FXML
                    if (fxmlHotReloadEnabled) {
                        Path childSourcePath = resolveWatchablePath(includedUri.toString());
                        if (childSourcePath != null) {
                            fileWatcher.watch(childSourcePath, () -> reloadComponents(includedPath));
                        }
                    }

                    logger.log(Level.FINEST, "Dependency: {0} -> {1}",
                            new Object[]{includedPath, parentPath});
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to analyze dependencies for: {0}", parentPath);
        }
    }

    /**
     * Builds the stylesheet → FXML mapping for a component.
     */
    private void buildStylesheetMapping(HotReloadable component) {
        String fxmlPath = component.getFxmlResourcePath();
        if (fxmlPath == null) {
            return;
        }

        // Map same-name stylesheets (auto-attached)
        String baseName = getBaseName(fxmlPath);
        String parentDir = getParentPath(fxmlPath);
        String cssPath = (parentDir != null ? parentDir + "/" : "") + baseName + ".css";
        String bssPath = (parentDir != null ? parentDir + "/" : "") + baseName + ".bss";

        stylesheetToFxml.computeIfAbsent(cssPath, k -> ConcurrentHashMap.newKeySet()).add(fxmlPath);
        stylesheetToFxml.computeIfAbsent(bssPath, k -> ConcurrentHashMap.newKeySet()).add(fxmlPath);

        logger.log(Level.FINEST, "Stylesheet mapping: {0}, {1} -> {2}",
                new Object[]{cssPath, bssPath, fxmlPath});
    }

    /**
     * Finds all resource paths affected by a change using BFS.
     */
    private Set<String> findAffectedPaths(String changedPath) {
        Set<String> affected = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();

        affected.add(changedPath);
        queue.offer(changedPath);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> parents = dependencyGraph.get(current);

            if (parents != null) {
                for (String parent : parents) {
                    if (affected.add(parent)) {
                        queue.offer(parent);
                    }
                }
            }
        }

        if (affected.size() > 1) {
            logger.log(Level.FINE, "Affected paths ({0}): {1}",
                    new Object[]{affected.size(), affected});
        }

        return affected;
    }

    /**
     * Converts a URI to a classpath-relative resource path.
     */
    private String uriToResourcePath(URI uri) {
        String path = uri.getPath();
        if (path == null) {
            return null;
        }

        // Handle JAR URLs
        int bangIndex = path.indexOf("!/");
        if (bangIndex >= 0) {
            return path.substring(bangIndex + 2);
        }

        // Extract resource path from file path
        return SourcePathConverters.extractResourcePath(path);
    }

    /**
     * Gets the base name (without extension) from a path.
     */
    private String getBaseName(String path) {
        int lastSlash = path.lastIndexOf('/');
        String fileName = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot > 0) ? fileName.substring(0, lastDot) : fileName;
    }

    /**
     * Gets the parent path from a resource path.
     */
    private String getParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        return (lastSlash > 0) ? path.substring(0, lastSlash) : null;
    }

    /**
     * Resets all internal state (primarily for testing).
     */
    public synchronized void reset() {
        fxmlHotReloadEnabled = false;
        cssHotReloadEnabled = false;
        fileWatcher.stop();
        if (cssMonitor != null) {
            cssMonitor.stopMonitoring();
        }
        converters.clear();
        converters.addAll(SourcePathConverters.DEFAULTS);
        componentsByPath.clear();
        dependencyGraph.clear();
        analyzedFxmls.clear();
        stylesheetToFxml.clear();
    }
}