package com.dlsc.fxmlkit.hotreload;

import com.dlsc.fxmlkit.fxml.FxmlDependencyAnalyzer;
import javafx.application.Platform;
import javafx.scene.Parent;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
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
 * Central manager for FXML and CSS hot reload functionality.
 *
 * <p>This singleton coordinates file monitoring, dependency tracking, and view reloading
 * during development, providing zero-configuration hot reload support for FxmlKit views.
 *
 * <p>Monitors file changes via WatchService in both source (src/main/resources) and target
 * (target/classes or build/classes) directories. Propagates changes through fx:include
 * dependencies and refreshes CSS/BSS stylesheets. Uses 500ms debouncing to prevent
 * duplicate reloads.
 *
 * <h2>CSS Monitoring Modes</h2>
 * <ul>
 *   <li><b>Component-based (default):</b> Only monitors CSS files with same name as FXML
 *       (e.g., UserView.fxml → UserView.css)</li>
 *   <li><b>Global:</b> Monitors all stylesheets across the entire scene graph, including
 *       Scene-level styles, shared stylesheets, and dynamically added styles</li>
 * </ul>
 *
 * <p>Supported file types:
 * <ul>
 *   <li>.fxml - Full reload (loses runtime state)
 *   <li>.css, .bss - Stylesheet refresh (preserves runtime state)
 *   <li>.properties - Ignored (Java ResourceBundle caching limitation)
 *   <li>.png, .jpg, etc. - Ignored (JavaFX Image caching limitation)
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Enable at startup
 * if (isDevelopmentMode()) {
 *     FxmlKit.enableDevelopmentMode();
 *     // Optionally enable global CSS monitoring
 *     FxmlKit.setGlobalCssMonitoring(true);
 * }
 *
 * // Components auto-register when created
 * MainView view = new MainView();
 *
 * // Disable at shutdown
 * FxmlKit.disableDevelopmentMode();
 * }</pre>
 *
 * <p>Thread-safe: all public methods are thread-safe. Internal state is protected by
 * ConcurrentHashMap and synchronized blocks where necessary.
 *
 * @see HotReloadable
 * @see GlobalCssMonitor
 */
public final class HotReloadManager {

    private static final Logger logger = Logger.getLogger(HotReloadManager.class.getName());

    /**
     * Singleton instance.
     */
    private static final HotReloadManager INSTANCE = new HotReloadManager();

    /**
     * Debounce window in milliseconds.
     */
    private static final long DEBOUNCE_MILLIS = 500;

    /**
     * Whether FXML hot reload is enabled.
     */
    private volatile boolean fxmlHotReloadEnabled = false;

    /**
     * Whether CSS hot reload is enabled.
     */
    private volatile boolean cssHotReloadEnabled = false;

    /**
     * Whether global CSS monitoring is enabled.
     * When true, monitors all stylesheets across the scene graph.
     * When false, only monitors same-name CSS files (e.g., View.fxml → View.css).
     */
    private volatile boolean globalCssMonitoring = false;

    /**
     * Whether the WatchService has been initialized.
     */
    private volatile boolean watchServiceInitialized = false;

    /**
     * The WatchService for file system monitoring.
     */
    private WatchService watchService;

    /**
     * Background thread for watch loop.
     */
    private Thread watchThread;

    /**
     * Maps WatchKeys to their monitored directory paths.
     */
    private final Map<WatchKey, Path> watchKeyToPath = new ConcurrentHashMap<>();

    /**
     * Set of all monitored root directories.
     */
    private final Set<Path> monitoredRoots = ConcurrentHashMap.newKeySet();

    /**
     * Maps resource paths to their registered components.
     * Key: classpath-relative path (e.g., "com/example/View.fxml")
     * Value: List of components using that FXML
     */
    private final Map<String, List<HotReloadable>> componentsByPath = new ConcurrentHashMap<>();

    /**
     * Reverse dependency graph: child FXML -> set of parent FXMLs.
     * Used to propagate changes through fx:include hierarchies.
     */
    private final Map<String, Set<String>> dependencyGraph = new ConcurrentHashMap<>();

    /**
     * Maps CSS/BSS paths to FXML paths that use them.
     * Key: stylesheet path (e.g., "com/example/View.css")
     * Value: Set of FXML paths using this stylesheet
     */
    private final Map<String, Set<String>> stylesheetToFxml = new ConcurrentHashMap<>();

    /**
     * Tracks last reload time per resource path for debouncing.
     */
    private final Map<String, Long> lastReloadTime = new ConcurrentHashMap<>();

    /**
     * Global CSS monitor instance for scene-graph-wide stylesheet monitoring.
     */
    private final GlobalCssMonitor globalCssMonitor = new GlobalCssMonitor();

    /**
     * Cached project root for source file resolution.
     */
    private volatile Path cachedProjectRoot;

    private HotReloadManager() {
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
     * Enables or disables FXML hot reload.
     *
     * <p>When enabled, the manager starts monitoring directories when components register,
     * automatically reloads views when FXML files change, and propagates changes through
     * fx:include dependencies.
     *
     * @param enabled true to enable FXML hot reload, false to disable
     */
    public synchronized void setFxmlHotReloadEnabled(boolean enabled) {
        if (this.fxmlHotReloadEnabled == enabled) {
            return;
        }

        this.fxmlHotReloadEnabled = enabled;
        logger.log(Level.FINE, "FXML hot reload {0}", enabled ? "enabled" : "disabled");

        updateWatchServiceState();
    }

    /**
     * Returns whether FXML hot reload is enabled.
     *
     * @return true if FXML hot reload is enabled
     */
    public boolean isFxmlHotReloadEnabled() {
        return fxmlHotReloadEnabled;
    }

    /**
     * Enables or disables CSS hot reload.
     *
     * <p>When enabled, the manager monitors CSS/BSS files for changes and refreshes
     * stylesheets without full view reload, preserving runtime state (user input,
     * scroll position, etc.).
     *
     * <p>Disable this if using CSSFX for CSS hot reload.
     *
     * @param enabled true to enable CSS hot reload, false to disable
     */
    public synchronized void setCssHotReloadEnabled(boolean enabled) {
        if (this.cssHotReloadEnabled == enabled) {
            return;
        }

        this.cssHotReloadEnabled = enabled;
        logger.log(Level.FINE, "CSS hot reload {0}", enabled ? "enabled" : "disabled");

        updateWatchServiceState();
        updateGlobalCssMonitorState();
    }

    /**
     * Returns whether CSS hot reload is enabled.
     *
     * @return true if CSS hot reload is enabled
     */
    public boolean isCssHotReloadEnabled() {
        return cssHotReloadEnabled;
    }

    /**
     * Enables or disables global CSS monitoring.
     *
     * <p>When enabled, monitors all stylesheets across the entire JavaFX scene graph:
     * <ul>
     *   <li>Scene-level stylesheets</li>
     *   <li>All Parent node stylesheets</li>
     *   <li>Dynamically added stylesheets</li>
     *   <li>Shared stylesheets used by multiple nodes</li>
     * </ul>
     *
     * <p>When disabled (default), only monitors CSS files with the same name as
     * registered FXML files (e.g., UserView.fxml → UserView.css).
     *
     * <p>Global monitoring is recommended for applications that use:
     * <ul>
     *   <li>Scene-level theme stylesheets</li>
     *   <li>Shared component stylesheets</li>
     *   <li>Dynamically loaded styles</li>
     * </ul>
     *
     * @param enabled true to enable global CSS monitoring, false for component-based only
     */
    public synchronized void setGlobalCssMonitoring(boolean enabled) {
        if (this.globalCssMonitoring == enabled) {
            return;
        }

        this.globalCssMonitoring = enabled;
        logger.log(Level.FINE, "Global CSS monitoring {0}", enabled ? "enabled" : "disabled");

        updateGlobalCssMonitorState();
    }

    /**
     * Returns whether global CSS monitoring is enabled.
     *
     * @return true if global CSS monitoring is enabled
     */
    public boolean isGlobalCssMonitoring() {
        return globalCssMonitoring;
    }

    /**
     * Returns whether any hot reload feature is enabled.
     *
     * @return true if either FXML or CSS hot reload is enabled
     */
    public boolean isEnabled() {
        return fxmlHotReloadEnabled || cssHotReloadEnabled;
    }

    /**
     * Legacy method for enabling hot reload.
     *
     * @deprecated Use {@link #setFxmlHotReloadEnabled(boolean)} and
     *             {@link #setCssHotReloadEnabled(boolean)} instead.
     */
    @Deprecated
    public synchronized void enable() {
        setFxmlHotReloadEnabled(true);
        setCssHotReloadEnabled(true);
    }

    /**
     * Legacy method for disabling hot reload.
     *
     * @deprecated Use {@link #setFxmlHotReloadEnabled(boolean)} and
     *             {@link #setCssHotReloadEnabled(boolean)} instead.
     */
    @Deprecated
    public synchronized void disable() {
        setFxmlHotReloadEnabled(false);
        setCssHotReloadEnabled(false);
    }

    /**
     * Registers a component for hot reload monitoring.
     *
     * <p>When registered, the component's FXML file location is analyzed to determine
     * which directories to monitor. The component will receive reload events when its
     * FXML or any of its fx:include dependencies change.
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
                .add(component);

        logger.log(Level.FINE, "Registered component: {0} -> {1}",
                new Object[]{component.getClass().getSimpleName(), resourcePath});

        // Build dependency graph for fx:include
        buildDependencyGraph(component);

        // Build stylesheet mapping
        buildStylesheetMapping(component);

        // Initialize monitoring if needed
        initializeMonitoringFromComponent(component);
    }

    /**
     * Updates the WatchService state based on current configuration.
     */
    private void updateWatchServiceState() {
        if (isEnabled()) {
            // WatchService will be started when components register
        } else {
            // Stop WatchService if both FXML and CSS are disabled
            stopWatchService();
        }
    }

    /**
     * Updates the global CSS monitor state based on current configuration.
     */
    private void updateGlobalCssMonitorState() {
        if (cssHotReloadEnabled && globalCssMonitoring) {
            // Set project root if available
            if (cachedProjectRoot != null) {
                globalCssMonitor.setProjectRoot(cachedProjectRoot);
            }
            globalCssMonitor.startMonitoring();
        } else {
            globalCssMonitor.stopMonitoring();
        }
    }

    /**
     * Stops the WatchService and releases resources.
     */
    private synchronized void stopWatchService() {
        // Stop watch thread
        if (watchThread != null) {
            watchThread.interrupt();
            try {
                watchThread.join(1000);
                if (watchThread.isAlive()) {
                    logger.log(Level.WARNING, "Watch thread did not terminate within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            watchThread = null;
        }

        // Close watch service
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing WatchService: " + e.getMessage());
            }
            watchService = null;
        }

        watchServiceInitialized = false;
        watchKeyToPath.clear();
        monitoredRoots.clear();

        logger.log(Level.FINE, "WatchService stopped");
    }

    /**
     * Initializes directory monitoring based on a component's FXML location.
     */
    private synchronized void initializeMonitoringFromComponent(HotReloadable component) {
        URL fxmlUrl = component.getFxmlUrl();
        if (fxmlUrl == null) {
            return;
        }

        try {
            Path fxmlPath = Path.of(fxmlUrl.toURI());
            Path targetDir = findTargetRoot(fxmlPath);

            if (targetDir != null && !monitoredRoots.contains(targetDir)) {
                initializeMonitoring(targetDir);

                // Cache project root for global CSS monitor
                if (cachedProjectRoot == null) {
                    cachedProjectRoot = StylesheetUriConverter.inferProjectRoot(targetDir);
                    if (cachedProjectRoot != null && globalCssMonitor.isMonitoring()) {
                        globalCssMonitor.setProjectRoot(cachedProjectRoot);
                        logger.log(Level.FINE, "Set project root: {0}", cachedProjectRoot);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to initialize monitoring from component: {0}",
                    e.getMessage());
        }
    }

    /**
     * Finds the target/classes root directory from an FXML path.
     */
    private Path findTargetRoot(Path fxmlPath) {
        String pathStr = fxmlPath.toString();

        // Maven: find target/classes
        int idx = pathStr.indexOf("/target/classes/");
        if (idx < 0) {
            idx = pathStr.indexOf("\\target\\classes\\");
        }
        if (idx >= 0) {
            return Path.of(pathStr.substring(0, idx + "/target/classes".length()));
        }

        // Gradle: find build/classes/java/main
        idx = pathStr.indexOf("/build/classes/java/main/");
        if (idx < 0) {
            idx = pathStr.indexOf("\\build\\classes\\java\\main\\");
        }
        if (idx >= 0) {
            return Path.of(pathStr.substring(0, idx + "/build/classes/java/main".length()));
        }

        // Gradle: find build/resources/main
        idx = pathStr.indexOf("/build/resources/main/");
        if (idx < 0) {
            idx = pathStr.indexOf("\\build\\resources\\main\\");
        }
        if (idx >= 0) {
            return Path.of(pathStr.substring(0, idx + "/build/resources/main".length()));
        }

        // IntelliJ: find out/production/resources
        idx = pathStr.indexOf("/out/production/resources/");
        if (idx < 0) {
            idx = pathStr.indexOf("\\out\\production\\resources\\");
        }
        if (idx >= 0) {
            return Path.of(pathStr.substring(0, idx + "/out/production/resources".length()));
        }

        // IntelliJ: find out/production/classes
        idx = pathStr.indexOf("/out/production/classes/");
        if (idx < 0) {
            idx = pathStr.indexOf("\\out\\production\\classes\\");
        }
        if (idx >= 0) {
            return Path.of(pathStr.substring(0, idx + "/out/production/classes".length()));
        }

        return null;
    }

    /**
     * Initializes the WatchService and starts monitoring directories.
     */
    private synchronized void initializeMonitoring(Path targetDir) {
        try {
            // Create WatchService if needed
            if (watchService == null) {
                watchService = FileSystems.getDefault().newWatchService();
                startWatchThread();
            }

            // Register target directory
            registerDirectoryRecursive(targetDir);
            monitoredRoots.add(targetDir);
            logger.log(Level.INFO, "Monitoring target: {0}", targetDir);

            // Infer and register source directory
            Path sourceDir = inferSourceDirectory(targetDir);
            if (sourceDir != null && Files.exists(sourceDir) && !monitoredRoots.contains(sourceDir)) {
                registerDirectoryRecursive(sourceDir);
                monitoredRoots.add(sourceDir);
                logger.log(Level.INFO, "Monitoring source: {0}", sourceDir);
            }

            watchServiceInitialized = true;

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to initialize monitoring: {0}", e.getMessage());
        }
    }

    /**
     * Infers the source directory from the target directory.
     */
    private Path inferSourceDirectory(Path targetDir) {
        String path = targetDir.toString();

        // Maven: target/classes -> src/main/resources
        if (path.contains("/target/classes") || path.contains("\\target\\classes")) {
            String projectPath = path.replaceAll("[/\\\\]target[/\\\\]classes.*", "");
            return Path.of(projectPath, "src", "main", "resources");
        }

        // Gradle: build/classes/java/main -> src/main/resources
        if (path.contains("/build/classes/java/main") || path.contains("\\build\\classes\\java\\main")) {
            String projectPath = path.replaceAll("[/\\\\]build[/\\\\]classes[/\\\\]java[/\\\\]main.*", "");
            return Path.of(projectPath, "src", "main", "resources");
        }

        // Gradle: build/resources/main -> src/main/resources
        if (path.contains("/build/resources/main") || path.contains("\\build\\resources\\main")) {
            String projectPath = path.replaceAll("[/\\\\]build[/\\\\]resources[/\\\\]main.*", "");
            return Path.of(projectPath, "src", "main", "resources");
        }

        // IntelliJ: out/production/resources -> src/main/resources
        if (path.contains("/out/production/resources") || path.contains("\\out\\production\\resources")) {
            String projectPath = path.replaceAll("[/\\\\]out[/\\\\]production[/\\\\]resources.*", "");
            return Path.of(projectPath, "src", "main", "resources");
        }

        // IntelliJ: out/production/classes -> src/main/resources
        if (path.contains("/out/production/classes") || path.contains("\\out\\production\\classes")) {
            String projectPath = path.replaceAll("[/\\\\]out[/\\\\]production[/\\\\]classes.*", "");
            return Path.of(projectPath, "src", "main", "resources");
        }

        return null;
    }

    /**
     * Recursively registers a directory and all subdirectories for watching.
     */
    private void registerDirectoryRecursive(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path subDir, BasicFileAttributes attrs) {
                try {
                    WatchKey key = subDir.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE);
                    watchKeyToPath.put(key, subDir);
                } catch (IOException e) {
                    logger.log(Level.FINE, "Cannot watch directory: {0}", subDir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Starts the background watch thread.
     */
    private void startWatchThread() {
        watchThread = new Thread(this::watchLoop, "FxmlKit-HotReload");
        watchThread.setDaemon(true);
        watchThread.start();
        logger.log(Level.FINE, "Watch thread started");
    }

    /**
     * Main watch loop running on background thread.
     */
    private void watchLoop() {
        while (isEnabled() && !Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();
                Path watchedDir = watchKeyToPath.get(key);

                if (watchedDir == null) {
                    key.reset();
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changedFile = watchedDir.resolve(pathEvent.context());

                    // Handle directory creation
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changedFile)) {
                        try {
                            registerDirectoryRecursive(changedFile);
                        } catch (IOException e) {
                            logger.log(Level.FINE, "Cannot watch new directory: {0}", changedFile);
                        }
                        continue;
                    }

                    // Process file change
                    if (Files.isRegularFile(changedFile) || kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        processFileChange(changedFile, watchedDir, kind);
                    }
                }

                key.reset();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (isEnabled()) {
                    logger.log(Level.WARNING, "Error in watch loop: {0}", e.getMessage());
                }
            }
        }

        logger.log(Level.FINE, "Watch thread stopped");
    }

    /**
     * Processes a file change event.
     */
    private void processFileChange(Path changedFile, Path watchedDir, WatchEvent.Kind<?> kind) {
        String fileName = changedFile.getFileName().toString();
        String extension = getExtension(fileName);

        // Determine file type and check if we should process it
        boolean isFxml = "fxml".equals(extension);
        boolean isCss = "css".equals(extension) || "bss".equals(extension);

        // Skip if not a supported file type or the feature is disabled
        if (isFxml && !fxmlHotReloadEnabled) {
            return;
        }
        if (isCss && !cssHotReloadEnabled) {
            return;
        }
        if (!isFxml && !isCss) {
             // Not a supported file type
            return;
        }

        // Determine if this is a source or target change
        boolean isSourceChange = isSourceDirectory(watchedDir);

        // Calculate resource path
        String resourcePath = calculateResourcePath(changedFile, watchedDir);
        if (resourcePath == null) {
            return;
        }

        logger.log(Level.FINE, "File {0}: {1} [{2}]",
                new Object[]{kind.name(), resourcePath, isSourceChange ? "SOURCE" : "TARGET"});

        // Sync source to target if needed
        if (isSourceChange && kind != StandardWatchEventKinds.ENTRY_DELETE) {
            syncToTarget(changedFile, watchedDir);
        }

        // Debounce check
        if (!shouldReload(resourcePath)) {
            logger.log(Level.FINEST, "Debounced: {0}", resourcePath);
            return;
        }

        // Find affected components and reload
        if (isFxml) {
            processFxmlChange(resourcePath);
        } else {
            processCssChange(resourcePath);
        }
    }

    /**
     * Processes an FXML file change.
     */
    private void processFxmlChange(String resourcePath) {
        Set<String> affectedPaths = findAffectedPaths(resourcePath);

        if (affectedPaths.isEmpty()) {
            logger.log(Level.FINE, "No registered components affected by: {0}", resourcePath);
            return;
        }

        reloadComponentsFull(affectedPaths);
    }

    /**
     * Processes a CSS/BSS file change.
     */
    private void processCssChange(String resourcePath) {
        // Global CSS monitoring
        if (globalCssMonitoring && globalCssMonitor.isMonitoring()) {
            globalCssMonitor.refreshStylesheet(resourcePath);
        }

        // Component-based CSS monitoring (original behavior)
        Set<String> affectedFxmlPaths = findFxmlsUsingStylesheet(resourcePath);

        if (affectedFxmlPaths.isEmpty() && !globalCssMonitoring) {
            logger.log(Level.FINE, "No registered components affected by: {0}", resourcePath);
            return;
        }

        if (!affectedFxmlPaths.isEmpty()) {
            reloadComponentsStylesheet(affectedFxmlPaths, resourcePath);
        }
    }

    /**
     * Performs full reload for affected components.
     */
    private void reloadComponentsFull(Set<String> affectedPaths) {
        Set<HotReloadable> componentsToReload = collectComponents(affectedPaths);

        if (componentsToReload.isEmpty()) {
            return;
        }

        logger.log(Level.INFO, "Full reload: {0} component(s)", componentsToReload.size());

        Platform.runLater(() -> {
            for (HotReloadable component : componentsToReload) {
                try {
                    component.reload();
                    logger.log(Level.FINE, "Reloaded: {0}", component.getClass().getSimpleName());
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to reload {0}: {1}",
                            new Object[]{component.getClass().getSimpleName(), e.getMessage()});
                }
            }
            logger.log(Level.INFO, "Hot reload complete");
        });
    }

    /**
     * Performs stylesheet refresh for affected components.
     *
     * @param affectedPaths the affected FXML paths
     * @param cssResourcePath the CSS resource path that changed
     */
    private void reloadComponentsStylesheet(Set<String> affectedPaths, String cssResourcePath) {
        Set<HotReloadable> componentsToReload = collectComponents(affectedPaths);

        if (componentsToReload.isEmpty()) {
            return;
        }

        logger.log(Level.INFO, "Stylesheet refresh: {0} component(s)", componentsToReload.size());

        // Find source file for the CSS
        String sourceFileUri = null;
        if (cachedProjectRoot != null) {
            Path sourceFile = StylesheetUriConverter.findSourceFile(cssResourcePath, cachedProjectRoot);
            if (sourceFile != null) {
                sourceFileUri = sourceFile.toUri().toString();
            }
        }

        final String finalSourceFileUri = sourceFileUri;

        Platform.runLater(() -> {
            for (HotReloadable component : componentsToReload) {
                try {
                    Parent root = component.getRootForStyleRefresh();
                    if (root != null) {
                        refreshStylesheets(root, cssResourcePath, finalSourceFileUri);
                        logger.log(Level.FINE, "Stylesheet refreshed: {0}",
                                component.getClass().getSimpleName());
                    } else {
                        // Fallback to full reload
                        component.reload();
                        logger.log(Level.FINE, "Fallback reload: {0}",
                                component.getClass().getSimpleName());
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to refresh {0}: {1}",
                            new Object[]{component.getClass().getSimpleName(), e.getMessage()});
                }
            }
            logger.log(Level.INFO, "Hot reload complete");
        });
    }

    /**
     * Refreshes stylesheets for a Parent node and all its children.
     *
     * <p>This method converts classpath URIs to file:// URIs pointing to source files,
     * which forces JavaFX to re-read the CSS content. This is the key to making
     * CSS hot reload work reliably.
     *
     * @param root the root node to refresh
     * @param cssResourcePath the CSS resource path that changed
     * @param sourceFileUri the source file URI (may be null)
     */
    private void refreshStylesheets(Parent root, String cssResourcePath, String sourceFileUri) {
        var stylesheets = root.getStylesheets();

        for (int i = 0; i < stylesheets.size(); i++) {
            String uri = stylesheets.get(i);

            if (StylesheetUriConverter.matchesResourcePath(uri, cssResourcePath)) {
                if (sourceFileUri != null) {
                    // Replace with file:// URI pointing to source file
                    // This is the key to making hot reload work!
                    if (!sourceFileUri.equals(uri)) {
                        stylesheets.set(i, sourceFileUri);
                        logger.log(Level.FINE, "Replaced stylesheet: {0} -> {1}",
                                new Object[]{uri, sourceFileUri});
                    } else {
                        // Already using file:// URI, do in-place refresh
                        stylesheets.remove(i);
                        stylesheets.add(i, uri);
                    }
                } else {
                    // Fallback: in-place refresh (may not always work due to caching)
                    String cleanUri = StylesheetUriConverter.removeQueryString(uri);
                    stylesheets.remove(i);
                    stylesheets.add(i, cleanUri);
                }
            }
        }

        // Recursively refresh children
        for (var child : root.getChildrenUnmodifiable()) {
            if (child instanceof Parent childParent) {
                refreshStylesheets(childParent, cssResourcePath, sourceFileUri);
            }
        }
    }

    /**
     * Collects all components for the given paths.
     */
    private Set<HotReloadable> collectComponents(Set<String> paths) {
        Set<HotReloadable> components = new LinkedHashSet<>();
        for (String path : paths) {
            List<HotReloadable> list = componentsByPath.get(path);
            if (list != null) {
                components.addAll(list);
            }
        }
        return components;
    }

    /**
     * Builds the dependency graph for a component's FXML.
     */
    private void buildDependencyGraph(HotReloadable component) {
        URL fxmlUrl = component.getFxmlUrl();
        if (fxmlUrl == null) {
            return;
        }

        String parentPath = component.getFxmlResourcePath();

        try {
            Set<URI> allFxmls = FxmlDependencyAnalyzer.findAllIncludedFxmls(fxmlUrl);

            for (URI includedUri : allFxmls) {
                String includedPath = uriToResourcePath(includedUri);
                if (includedPath != null && !includedPath.equals(parentPath)) {
                    // Add reverse dependency: child -> parent
                    dependencyGraph.computeIfAbsent(includedPath, k -> ConcurrentHashMap.newKeySet())
                            .add(parentPath);

                    logger.log(Level.FINEST, "Dependency: {0} -> {1}",
                            new Object[]{includedPath, parentPath});
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to analyze dependencies for: {0}", parentPath);
        }
    }

    /**
     * Builds the stylesheet to FXML mapping for a component.
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
     * Finds all resource paths affected by a change using BFS traversal.
     */
    private Set<String> findAffectedPaths(String changedPath) {
        Set<String> affected = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.offer(changedPath);
        affected.add(changedPath);
        visited.add(changedPath);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> parents = dependencyGraph.get(current);

            if (parents != null) {
                for (String parent : parents) {
                    if (visited.add(parent)) {
                        queue.offer(parent);
                        affected.add(parent);
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
     * Finds all FXML paths that use the given stylesheet.
     *
     * <p>CSS changes do NOT propagate to parent FXMLs.
     */
    private Set<String> findFxmlsUsingStylesheet(String stylesheetPath) {
        Set<String> result = new LinkedHashSet<>();

        // Direct mapping from stylesheet to FXML
        Set<String> directFxmls = stylesheetToFxml.get(stylesheetPath);
        if (directFxmls != null) {
            result.addAll(directFxmls);
        }

        // Fallback: check by base name if no mapping exists
        if (result.isEmpty()) {
            String baseName = getBaseName(stylesheetPath);
            String parentDir = getParentPath(stylesheetPath);
            String potentialFxml = (parentDir != null ? parentDir + "/" : "") + baseName + ".fxml";

            if (componentsByPath.containsKey(potentialFxml)) {
                result.add(potentialFxml);
            }
        }

        return result;
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
            path = path.substring(bangIndex + 2);
        }

        // Remove /classes/ prefix (Maven)
        int classesIndex = path.indexOf("/classes/");
        if (classesIndex >= 0) {
            return path.substring(classesIndex + "/classes/".length());
        }

        // Remove /build/classes/java/main/ prefix (Gradle)
        int gradleIndex = path.indexOf("/build/classes/java/main/");
        if (gradleIndex >= 0) {
            return path.substring(gradleIndex + "/build/classes/java/main/".length());
        }

        // Remove /build/resources/main/ prefix (Gradle)
        int resourcesIndex = path.indexOf("/build/resources/main/");
        if (resourcesIndex >= 0) {
            return path.substring(resourcesIndex + "/build/resources/main/".length());
        }

        // Remove /out/production/resources/ prefix (IntelliJ)
        int intellijResourcesIndex = path.indexOf("/out/production/resources/");
        if (intellijResourcesIndex >= 0) {
            return path.substring(intellijResourcesIndex + "/out/production/resources/".length());
        }

        // Remove /out/production/classes/ prefix (IntelliJ)
        int intellijClassesIndex = path.indexOf("/out/production/classes/");
        if (intellijClassesIndex >= 0) {
            return path.substring(intellijClassesIndex + "/out/production/classes/".length());
        }

        // Remove leading slash
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        return path;
    }

    /**
     * Gets the file extension from a file name.
     */
    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
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
     * Checks if a directory is a source directory (vs target).
     */
    private boolean isSourceDirectory(Path dir) {
        String path = dir.toString();
        return path.contains("/src/main/resources") || path.contains("\\src\\main\\resources");
    }

    /**
     * Calculates the classpath-relative resource path from a file path.
     */
    private String calculateResourcePath(Path changedFile, Path watchedDir) {
        Path root = findMonitoredRoot(watchedDir);
        if (root == null) {
            return null;
        }

        try {
            Path relativePath = root.relativize(changedFile);
            return relativePath.toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Finds the monitored root directory containing the given directory.
     */
    private Path findMonitoredRoot(Path dir) {
        for (Path root : monitoredRoots) {
            if (dir.startsWith(root) || dir.equals(root)) {
                return root;
            }
        }
        return null;
    }

    /**
     * Syncs a source file to the target directory.
     */
    private void syncToTarget(Path sourceFile, Path sourceDir) {
        Path sourceRoot = findMonitoredRoot(sourceDir);
        if (sourceRoot == null) {
            return;
        }

        Path targetRoot = inferTargetDirectory(sourceRoot);
        if (targetRoot == null || !Files.exists(targetRoot)) {
            return;
        }

        try {
            Path relativePath = sourceRoot.relativize(sourceFile);
            Path targetFile = targetRoot.resolve(relativePath);

            Files.createDirectories(targetFile.getParent());
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            logger.log(Level.FINE, "Synced to target: {0}", relativePath);

        } catch (IOException e) {
            logger.log(Level.FINE, "Failed to sync to target: {0}", e.getMessage());
        }
    }

    /**
     * Infers the target directory from a source directory.
     */
    private Path inferTargetDirectory(Path sourceRoot) {
        String path = sourceRoot.toString();

        if (path.contains("/src/main/resources") || path.contains("\\src\\main\\resources")) {
            String projectPath = path.replaceAll("[/\\\\]src[/\\\\]main[/\\\\]resources.*", "");
            return Path.of(projectPath, "target", "classes");
        }

        return null;
    }

    /**
     * Debounce check - returns true if enough time has passed since last reload.
     */
    private boolean shouldReload(String resourcePath) {
        long now = System.currentTimeMillis();
        Long lastTime = lastReloadTime.get(resourcePath);

        if (lastTime != null && (now - lastTime) < DEBOUNCE_MILLIS) {
            return false;
        }

        lastReloadTime.put(resourcePath, now);
        return true;
    }

    /**
     * Returns the GlobalCssMonitor instance.
     *
     * @return the global CSS monitor
     */
    public GlobalCssMonitor getGlobalCssMonitor() {
        return globalCssMonitor;
    }

    /**
     * Clears all internal state. Primarily for testing.
     */
    public synchronized void reset() {
        fxmlHotReloadEnabled = false;
        cssHotReloadEnabled = false;
        globalCssMonitoring = false;
        stopWatchService();
        globalCssMonitor.reset();
        componentsByPath.clear();
        dependencyGraph.clear();
        stylesheetToFxml.clear();
        lastReloadTime.clear();
        cachedProjectRoot = null;
    }
}