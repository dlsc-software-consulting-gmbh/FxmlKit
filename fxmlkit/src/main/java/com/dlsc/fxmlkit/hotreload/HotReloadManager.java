package com.dlsc.fxmlkit.hotreload;

import com.dlsc.fxmlkit.fxml.FxmlDependencyAnalyzer;
import javafx.application.Platform;

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
 * Central manager for FXML hot reload functionality.
 *
 * <p>This singleton class coordinates file monitoring, dependency tracking, and view reloading
 * during development. It provides zero-configuration hot reload support for FxmlKit views.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Automatic file change detection via WatchService</li>
 *   <li>Dual directory monitoring (source and target/classes)</li>
 *   <li>Dependency propagation for fx:include hierarchies</li>
 *   <li>CSS/BSS stylesheet change detection and refresh</li>
 *   <li>Debouncing to prevent duplicate reloads</li>
 *   <li>Thread-safe component registration</li>
 *   <li>Support for multi-module Maven/Gradle projects</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Enable hot reload at application startup
 * if (isDevelopmentMode()) {
 *     HotReloadManager.getInstance().enable();
 * }
 *
 * // Components auto-register when created (if enabled)
 * MainView view = new MainView();  // Auto-registered
 *
 * // Disable at shutdown
 * HotReloadManager.getInstance().disable();
 * }</pre>
 *
 * <h2>Architecture</h2>
 * <pre>
 * File Change (src or target)
 *     |
 *     v
 * WatchService Thread
 *     |
 *     v
 * Debounce Check (500ms window)
 *     |
 *     v
 * Dependency Analysis (BFS for fx:include / CSS mapping)
 *     |
 *     v
 * Platform.runLater()
 *     |
 *     v
 * Component.reload() on JavaFX Thread
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe. Internal state is protected by
 * ConcurrentHashMap and synchronized blocks where necessary.
 *
 * @see HotReloadable
 * @see ReloadStrategy
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
     * Whether hot reload is currently enabled.
     */
    private volatile boolean enabled = false;

    /**
     * Whether the manager has been initialized.
     */
    private volatile boolean initialized = false;

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
     * Enables hot reload functionality.
     *
     * <p>When enabled, the manager will:
     * <ul>
     *   <li>Start monitoring directories when components register</li>
     *   <li>Automatically reload views when FXML files change</li>
     *   <li>Refresh stylesheets when CSS/BSS files change</li>
     *   <li>Propagate changes through fx:include dependencies</li>
     * </ul>
     *
     * <p>Call this during application startup in development mode.
     */
    public synchronized void enable() {
        if (enabled) {
            logger.log(Level.FINE, "Hot reload already enabled");
            return;
        }

        enabled = true;
        logger.log(Level.INFO, "Hot reload enabled");
    }

    /**
     * Disables hot reload and releases resources.
     *
     * <p>Stops the file watching thread and closes the WatchService.
     * Registered components remain in memory but won't receive reload events.
     */
    public synchronized void disable() {
        if (!enabled) {
            return;
        }

        enabled = false;

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

        initialized = false;
        watchKeyToPath.clear();
        monitoredRoots.clear();

        logger.log(Level.INFO, "Hot reload disabled");
    }

    /**
     * Returns whether hot reload is currently enabled.
     *
     * @return true if hot reload is enabled
     */
    public boolean isEnabled() {
        return enabled;
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
        if (!enabled) {
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

            initialized = true;

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
        while (enabled && !Thread.currentThread().isInterrupted()) {
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
                if (enabled) {
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
        String extension = ReloadStrategy.getExtension(fileName);
        ReloadStrategy strategy = ReloadStrategy.forExtension(extension);

        // Skip ignored files
        if (strategy == ReloadStrategy.IGNORE) {
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

        // Find all affected paths
        Set<String> affectedFxmlPaths;
        if (strategy == ReloadStrategy.STYLESHEET_RELOAD) {
            // For CSS/BSS: find FXMLs that use this stylesheet
            affectedFxmlPaths = findFxmlsUsingStylesheet(resourcePath);
        } else {
            // For FXML: find affected paths via dependency graph
            affectedFxmlPaths = findAffectedPaths(resourcePath);
        }

        if (affectedFxmlPaths.isEmpty()) {
            logger.log(Level.FINE, "No registered components affected by: {0}", resourcePath);
            return;
        }

        // Reload components
        reloadAffectedComponents(affectedFxmlPaths, strategy);
    }

    /**
     * Finds all FXML paths that use the given stylesheet.
     */
    private Set<String> findFxmlsUsingStylesheet(String stylesheetPath) {
        Set<String> result = new LinkedHashSet<>();

        // Direct mapping
        Set<String> directFxmls = stylesheetToFxml.get(stylesheetPath);
        if (directFxmls != null) {
            result.addAll(directFxmls);
        }

        // Also check by base name (for auto-attached stylesheets)
        // Hello.css -> Hello.fxml
        String baseName = getBaseName(stylesheetPath);
        if (baseName != null) {
            String parentDir = getParentPath(stylesheetPath);
            String potentialFxml = (parentDir != null ? parentDir + "/" : "") + baseName + ".fxml";

            if (componentsByPath.containsKey(potentialFxml)) {
                result.add(potentialFxml);
            }
        }

        // Propagate through dependency graph (if parent uses the stylesheet, children might too)
        Set<String> expanded = new LinkedHashSet<>();
        for (String fxmlPath : result) {
            expanded.addAll(findAffectedPaths(fxmlPath));
        }

        return expanded;
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
        // Find the monitored root that contains this directory
        Path root = findMonitoredRoot(watchedDir);
        if (root == null) {
            return null;
        }

        try {
            Path relativePath = root.relativize(changedFile);
            // Normalize to forward slashes
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

            // Ensure parent directories exist
            Files.createDirectories(targetFile.getParent());

            // Copy file
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

        // src/main/resources -> target/classes (Maven)
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

        // TODO: Parse FXML to find explicitly declared stylesheets
        // This would require reading the FXML and extracting stylesheet attributes
        // For now, rely on same-name convention
    }

    /**
     * Converts a URI to a classpath-relative resource path.
     */
    private String uriToResourcePath(URI uri) {
        String path = uri.getPath();
        if (path == null) {
            return null;
        }

        // Handle JAR URLs: jar:file:/path/app.jar!/com/example/View.fxml
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

        // Remove leading slash
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        return path;
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
     * Reloads all components affected by the changed paths.
     */
    private void reloadAffectedComponents(Set<String> affectedPaths, ReloadStrategy strategy) {
        Set<HotReloadable> componentsToReload = new LinkedHashSet<>();

        for (String path : affectedPaths) {
            List<HotReloadable> components = componentsByPath.get(path);
            if (components != null) {
                componentsToReload.addAll(components);
            }
        }

        if (componentsToReload.isEmpty()) {
            logger.log(Level.FINEST, "No registered components for affected paths");
            return;
        }

        logger.log(Level.INFO, "Reloading {0} component(s) using {1}",
                new Object[]{componentsToReload.size(), strategy});

        // Execute reload on JavaFX thread
        Platform.runLater(() -> {
            for (HotReloadable component : componentsToReload) {
                try {
                    strategy.apply(component);
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
     * Clears all internal state. Primarily for testing.
     */
    public synchronized void reset() {
        disable();
        componentsByPath.clear();
        dependencyGraph.clear();
        stylesheetToFxml.clear();
        lastReloadTime.clear();
    }
}