package com.dlsc.fxmlkit.hotreload;

import com.dlsc.fxmlkit.fxml.FxmlDependencyAnalyzer;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;

import java.io.IOException;
import java.lang.ref.WeakReference;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central manager for FXML and CSS hot reload functionality.
 *
 * <p>This singleton class coordinates file monitoring, dependency tracking, and view reloading
 * during development. It provides zero-configuration hot reload support for FxmlKit views.
 *
 * <p>Features:
 * <ul>
 *   <li>Automatic file change detection via WatchService</li>
 *   <li>Source directory monitoring with automatic sync to target (preferred)</li>
 *   <li>Fallback to target directory monitoring when source is unavailable</li>
 *   <li>Dependency propagation for fx:include hierarchies</li>
 *   <li>CSS/BSS stylesheet change detection and refresh</li>
 *   <li>Debouncing to prevent duplicate reloads and handle partial file writes</li>
 *   <li>Thread-safe component registration</li>
 *   <li>Support for multi-module Maven/Gradle projects</li>
 * </ul>
 *
 * <p>Supported file types:
 * <ul>
 *   <li>.fxml - Full reload (loses runtime state)</li>
 *   <li>.css, .bss - Stylesheet refresh (preserves runtime state)</li>
 *   <li>.properties - Ignored (Java ResourceBundle caching limitation)</li>
 *   <li>.png, .jpg, etc. - Ignored (JavaFX Image caching limitation)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Enable hot reload at application startup
 * if (isDevelopmentMode()) {
 *     FxmlKit.enableDevelopmentMode();
 * }
 *
 * // Components auto-register when created (if enabled)
 * MainView view = new MainView();  // Auto-registered
 *
 * // Disable at shutdown
 * FxmlKit.disableDevelopmentMode();
 * }</pre>
 *
 * <p>Architecture (source monitoring - preferred):
 * <pre>
 * Source File Change (src/main/resources)
 *     ↓
 * WatchService Thread
 *     ↓
 * Sync to Target (copy file to target/classes)
 *     ↓
 * Schedule Reload (debounce 200ms)
 *     ↓
 * Dependency Analysis (BFS for fx:include / CSS mapping)
 *     ↓
 * Platform.runLater()
 *     ↓
 * Component.reload() on JavaFX Thread
 * </pre>
 *
 * <p>Architecture (target monitoring - fallback):
 * <pre>
 * Target File Change (target/classes)
 *     ↓
 * WatchService Thread
 *     ↓
 * Schedule Reload (debounce 200ms)
 *     ↓
 * Component.reload() on JavaFX Thread
 * </pre>
 *
 * <p>All public methods are thread-safe. Internal state is protected by
 * ConcurrentHashMap and synchronized blocks where necessary.
 *
 * @see HotReloadable
 * @see BuildSystem
 */
public final class HotReloadManager {

    private static final Logger logger = Logger.getLogger(HotReloadManager.class.getName());

    /**
     * Singleton instance.
     */
    private static final HotReloadManager INSTANCE = new HotReloadManager();

    /**
     * Debounce window in milliseconds.
     * This delay allows files to stabilize after editors write them
     * (some editors write in multiple steps).
     */
    private static final long DEBOUNCE_MILLIS = 200;

    /**
     * Whether FXML hot reload is enabled.
     */
    private volatile boolean fxmlHotReloadEnabled = false;

    /**
     * Whether CSS hot reload is enabled.
     */
    private volatile boolean cssHotReloadEnabled = false;

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
     * Scheduler for debounced reload execution.
     * Ensures files are fully written before triggering reload.
     */
    private ScheduledExecutorService scheduler;

    /**
     * Pending FXML reload tasks, keyed by resource path.
     * Used to cancel and reschedule when new events arrive.
     */
    private final Map<String, ScheduledFuture<?>> pendingFxmlReloads = new ConcurrentHashMap<>();

    /**
     * Pending CSS reload tasks, keyed by resource path.
     * Used to cancel and reschedule when new events arrive.
     */
    private final Map<String, ScheduledFuture<?>> pendingCssReloads = new ConcurrentHashMap<>();

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
     * Uses WeakReference to allow components to be garbage collected.
     * Key: classpath-relative path (e.g., "com/example/View.fxml")
     * Value: List of weak references to components using that FXML
     */
    private final Map<String, List<WeakReference<HotReloadable>>> componentsByPath = new ConcurrentHashMap<>();

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
     * Cached project root for global CSS monitoring.
     */
    private volatile Path cachedProjectRoot;

    /**
     * Global CSS monitor for Scene-level stylesheets.
     */
    private final GlobalCssMonitor globalCssMonitor = new GlobalCssMonitor();

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
     * Returns the GlobalCssMonitor instance.
     *
     * @return the global CSS monitor
     */
    public GlobalCssMonitor getGlobalCssMonitor() {
        return globalCssMonitor;
    }

    /**
     * Enables or disables FXML hot reload.
     *
     * <p>When enabled, the manager will:
     * <ul>
     *   <li>Start monitoring directories when components register</li>
     *   <li>Automatically reload views when FXML files change</li>
     *   <li>Propagate changes through fx:include dependencies</li>
     * </ul>
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
     * <p>When enabled, the manager will:
     * <ul>
     *   <li>Monitor CSS/BSS files for changes</li>
     *   <li>Refresh stylesheets without full view reload</li>
     *   <li>Preserve runtime state (user input, scroll position, etc.)</li>
     * </ul>
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
     * Returns whether any hot reload feature is enabled.
     *
     * @return true if either FXML or CSS hot reload is enabled
     */
    public boolean isEnabled() {
        return fxmlHotReloadEnabled || cssHotReloadEnabled;
    }

    /**
     * Registers a component for hot reload monitoring.
     *
     * <p>When registered, the component's FXML file location is analyzed to determine
     * which directories to monitor. The component will receive reload events when
     * its FXML or any of its fx:include dependencies change.
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

        // Add to component registry with WeakReference
        componentsByPath.computeIfAbsent(resourcePath, k -> new CopyOnWriteArrayList<>())
                .add(new WeakReference<>(component));

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
     * Updates the GlobalCssMonitor state based on current configuration.
     */
    private void updateGlobalCssMonitorState() {
        if (cssHotReloadEnabled) {
            if (cachedProjectRoot != null) {
                globalCssMonitor.setProjectRoot(cachedProjectRoot);
            }
            globalCssMonitor.startMonitoring();
        } else {
            globalCssMonitor.stopMonitoring();
        }
    }

    /**
     * Stops the WatchService, scheduler, and releases resources.
     */
    private synchronized void stopWatchService() {
        // Cancel all pending reload tasks
        cancelAllPendingReloads();

        // Shutdown scheduler
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }

        // Stop watch thread
        if (watchThread != null) {
            watchThread.interrupt();
            try {
                watchThread.join(1000);
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
     * Cancels all pending reload tasks.
     */
    private void cancelAllPendingReloads() {
        for (ScheduledFuture<?> future : pendingFxmlReloads.values()) {
            future.cancel(false);
        }
        pendingFxmlReloads.clear();

        for (ScheduledFuture<?> future : pendingCssReloads.values()) {
            future.cancel(false);
        }
        pendingCssReloads.clear();
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
            Path targetDir = BuildSystem.findOutputRoot(fxmlPath);

            if (targetDir != null && !monitoredRoots.contains(targetDir)) {
                initializeMonitoring(targetDir);
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to initialize monitoring from component: {0}",
                    e.getMessage());
        }
    }

    /**
     * Initializes the WatchService and starts monitoring directories.
     *
     * <p>Prefers monitoring source directories for faster hot reload.
     * When source files change, they are synced to target and reload
     * is triggered immediately. Falls back to target monitoring if
     * source directory cannot be determined.
     */
    private synchronized void initializeMonitoring(Path targetDir) {
        // Infer source directory from target
        Path sourceDir = BuildSystem.inferSourceDirectory(targetDir);

        // Determine which directory to monitor
        Path dirToMonitor;
        boolean isSourceMonitoring;

        if (sourceDir != null && Files.exists(sourceDir)) {
            // Prefer source directory (faster hot reload)
            dirToMonitor = sourceDir;
            isSourceMonitoring = true;
        } else {
            // Fallback to target directory
            dirToMonitor = targetDir;
            isSourceMonitoring = false;
            logger.log(Level.FINE,
                    "Source directory not found, falling back to target: {0}", targetDir);
        }

        // Skip if already monitoring this directory
        if (monitoredRoots.contains(dirToMonitor)) {
            return;
        }

        try {
            // Create WatchService if needed
            if (watchService == null) {
                watchService = FileSystems.getDefault().newWatchService();
                startWatchThread();
            }

            // Create scheduler if needed
            if (scheduler == null) {
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "FxmlKit-ReloadScheduler");
                    t.setDaemon(true);
                    return t;
                });
            }

            // Register directory
            registerDirectoryRecursive(dirToMonitor);
            monitoredRoots.add(dirToMonitor);

            if (isSourceMonitoring) {
                logger.log(Level.INFO, "Monitoring source: {0}", dirToMonitor);
            } else {
                logger.log(Level.INFO, "Monitoring target: {0}", dirToMonitor);
            }

            watchServiceInitialized = true;

            // Cache project root for global CSS monitor
            if (cachedProjectRoot == null) {
                cachedProjectRoot = BuildSystem.inferProjectRoot(targetDir);
                if (cachedProjectRoot != null) {
                    logger.log(Level.FINE, "Cached project root: {0}", cachedProjectRoot);
                    // Update GlobalCssMonitor if CSS hot reload is enabled
                    if (cssHotReloadEnabled) {
                        globalCssMonitor.setProjectRoot(cachedProjectRoot);
                        // Ensure monitoring is started (may not have started earlier if project root was null)
                        if (!globalCssMonitor.isMonitoring()) {
                            globalCssMonitor.startMonitoring();
                        }
                    }
                }
            }

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to initialize monitoring: {0}", e.getMessage());
        }
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
     *
     * <p>Source changes trigger sync to target followed by immediate reload.
     * Target changes (fallback mode) trigger reload directly without sync.
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
            return;  // Not a supported file type
        }

        // Calculate resource path
        String resourcePath = calculateResourcePath(changedFile, watchedDir);
        if (resourcePath == null) {
            return;
        }

        logger.log(Level.FINE, "File {0}: {1}", new Object[]{kind.name(), resourcePath});

        // Sync to target only if monitoring source directory
        boolean isSourceDir = BuildSystem.isSourcePath(watchedDir.toString());
        if (isSourceDir && kind != StandardWatchEventKinds.ENTRY_DELETE) {
            syncToTarget(changedFile, watchedDir);
        }

        // Schedule reload
        if (isFxml) {
            scheduleFxmlReload(resourcePath);
        } else {
            scheduleCssReload(resourcePath);
        }
    }

    /**
     * Schedules an FXML reload with debouncing.
     *
     * <p>If a reload is already pending for this resource, it will be cancelled
     * and rescheduled. This ensures we wait for the file to stabilize before reloading.
     *
     * @param resourcePath the resource path that changed
     */
    private void scheduleFxmlReload(String resourcePath) {
        // Cancel any pending reload for this resource
        ScheduledFuture<?> existing = pendingFxmlReloads.remove(resourcePath);
        if (existing != null) {
            existing.cancel(false);
            logger.log(Level.FINEST, "Cancelled pending FXML reload: {0}", resourcePath);
        }

        // Schedule new reload after debounce delay
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pendingFxmlReloads.remove(resourcePath);
            processFxmlChange(resourcePath);
        }, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);

        pendingFxmlReloads.put(resourcePath, future);
        logger.log(Level.FINEST, "Scheduled FXML reload in {0}ms: {1}",
                new Object[]{DEBOUNCE_MILLIS, resourcePath});
    }

    /**
     * Schedules a CSS reload with debouncing.
     *
     * <p>If a reload is already pending for this resource, it will be cancelled
     * and rescheduled. This ensures we wait for the file to stabilize before reloading.
     *
     * @param resourcePath the resource path that changed
     */
    private void scheduleCssReload(String resourcePath) {
        // Cancel any pending reload for this resource
        ScheduledFuture<?> existing = pendingCssReloads.remove(resourcePath);
        if (existing != null) {
            existing.cancel(false);
            logger.log(Level.FINEST, "Cancelled pending CSS reload: {0}", resourcePath);
        }

        // Schedule new reload after debounce delay
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pendingCssReloads.remove(resourcePath);
            processCssChange(resourcePath);
        }, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);

        pendingCssReloads.put(resourcePath, future);
        logger.log(Level.FINEST, "Scheduled CSS reload in {0}ms: {1}",
                new Object[]{DEBOUNCE_MILLIS, resourcePath});
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
        // Global CSS monitoring (Scene-level stylesheets)
        globalCssMonitor.refreshStylesheet(resourcePath);

        // Component-based CSS monitoring (same-name stylesheets)
        Set<String> affectedFxmlPaths = findFxmlsUsingStylesheet(resourcePath);

        if (!affectedFxmlPaths.isEmpty()) {
            reloadComponentsStylesheet(affectedFxmlPaths, resourcePath);
        } else {
            logger.log(Level.FINE, "No registered components affected by: {0}", resourcePath);
        }
    }

    /**
     * Performs full reload for affected components.
     *
     * <p>After FXML reload, all CSS stylesheets are refreshed to ensure
     * the latest styles are applied. This fixes the issue where JavaFX's
     * StyleManager cache causes stale CSS to be displayed after FXML reload.
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

            // After FXML reload, refresh all CSS to ensure latest content
            globalCssMonitor.refreshAllStylesheets();

            logger.log(Level.INFO, "Hot reload complete");
        });
    }

    /**
     * Performs stylesheet refresh for affected components.
     */
    private void reloadComponentsStylesheet(Set<String> affectedPaths, String cssResourcePath) {
        Set<HotReloadable> componentsToReload = collectComponents(affectedPaths);

        if (componentsToReload.isEmpty()) {
            return;
        }

        // Find source file URI for bypassing cache
        String sourceFileUri = null;
        if (cachedProjectRoot != null) {
            Path sourceFile = StylesheetUriConverter.findSourceFile(cssResourcePath, cachedProjectRoot);
            if (sourceFile != null) {
                sourceFileUri = sourceFile.toUri().toString();
            }
        }
        final String finalSourceUri = sourceFileUri;

        logger.log(Level.INFO, "Stylesheet refresh: {0} component(s)", componentsToReload.size());

        Platform.runLater(() -> {
            for (HotReloadable component : componentsToReload) {
                try {
                    Parent root = component.getRootForStyleRefresh();
                    if (root != null) {
                        refreshStylesheets(root, cssResourcePath, finalSourceUri);
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
     * <p>Uses remove-add strategy to force JavaFX to reload the stylesheet.
     * This approach is compatible with other CSS monitoring tools like CSSFX.
     */
    private void refreshStylesheets(Parent root, String cssResourcePath, String sourceFileUri) {
        refreshStylesheetsRecursive(root, cssResourcePath);
    }

    /**
     * Recursively refreshes stylesheets using remove-add strategy.
     */
    private void refreshStylesheetsRecursive(Parent root, String cssResourcePath) {
        ObservableList<String> stylesheets = root.getStylesheets();
        for (int i = 0; i < stylesheets.size(); i++) {
            String uri = stylesheets.get(i);
            if (StylesheetUriConverter.matchesResourcePath(uri, cssResourcePath)) {
                // Remove and re-add to force reload
                stylesheets.remove(i);
                stylesheets.add(i, uri);
            }
        }

        // Recursively refresh children
        for (Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof Parent) {
                // Intentional: traditional instanceof for backward compatibility.
                Parent childParent = (Parent) child;
                refreshStylesheetsRecursive(childParent, cssResourcePath);
            }
        }
    }

    /**
     * Collects all live components for the given paths.
     *
     * <p>Stale WeakReferences are not cleaned up during collection for simplicity.
     * Memory impact is negligible, and
     * {@link #reset()} clears all state when hot reload is disabled.
     */
    private Set<HotReloadable> collectComponents(Set<String> paths) {
        Set<HotReloadable> components = new LinkedHashSet<>();

        for (String path : paths) {
            List<WeakReference<HotReloadable>> refs = componentsByPath.get(path);
            if (refs == null) {
                continue;
            }

            for (WeakReference<HotReloadable> ref : refs) {
                HotReloadable component = ref.get();
                if (component != null) {
                    components.add(component);
                }
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
            return path.substring(bangIndex + 2);
        }

        // Use BuildSystem to extract resource path
        return BuildSystem.extractResourcePath(path);
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

        Path targetRoot = BuildSystem.inferOutputDirectory(sourceRoot);
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
            // Use FINE level - sync failure is normal when target doesn't exist yet
            logger.log(Level.FINE, "Failed to sync to target: {0}", e.getMessage());
        }
    }

    /**
     * Clears all internal state. Primarily for testing.
     */
    public synchronized void reset() {
        fxmlHotReloadEnabled = false;
        cssHotReloadEnabled = false;
        stopWatchService();
        globalCssMonitor.stopMonitoring();
        cachedProjectRoot = null;
        componentsByPath.clear();
        dependencyGraph.clear();
        stylesheetToFxml.clear();
    }
}