package com.dlsc.fxmlkit.hotreload;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global CSS monitoring for hot reload functionality.
 *
 * <p>This class provides comprehensive CSS hot reload by monitoring all stylesheets
 * across the entire JavaFX scene graph, including:
 * <ul>
 *   <li>Scene-level stylesheets</li>
 *   <li>Parent node stylesheets at any depth</li>
 *   <li>Dynamically added nodes and stylesheets</li>
 *   <li>Shared stylesheets used by multiple nodes</li>
 * </ul>
 *
 * <h2>Memory Management</h2>
 * <p>Uses WeakReferences to track stylesheet lists, preventing memory leaks when
 * nodes are garbage collected. Stale references are cleaned up lazily during
 * refresh operations.
 *
 * <h2>CSS Refresh Mechanism</h2>
 * <p>When a CSS file changes, the stylesheet URI is converted to a file:// URI
 * pointing to the source file. This forces JavaFX to re-read the file content,
 * making the changes visible immediately.
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe. Scene graph operations are always
 * executed on the JavaFX Application Thread via Platform.runLater().
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Enable global CSS monitoring
 * GlobalCssMonitor monitor = new GlobalCssMonitor();
 * monitor.setProjectRoot(projectPath);
 * monitor.startMonitoring();
 *
 * // When CSS file changes (called by HotReloadManager)
 * monitor.refreshStylesheet("com/example/app.css");
 * }</pre>
 *
 * @see HotReloadManager
 * @see StylesheetUriConverter
 */
public class GlobalCssMonitor {

    private static final Logger logger = Logger.getLogger(GlobalCssMonitor.class.getName());

    /**
     * Tracks monitored scenes to avoid duplicate registration.
     * Uses WeakHashMap for automatic cleanup when scenes are GC'd.
     */
    private final Set<Scene> monitoredScenes = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Tracks monitored nodes to avoid duplicate registration.
     * Uses WeakHashMap for automatic cleanup when nodes are GC'd.
     */
    private final Set<Parent> monitoredNodes = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Tracks stylesheet lists that already have listeners attached.
     * Prevents adding duplicate listeners to the same ObservableList.
     */
    private final Set<ObservableList<String>> listenedStylesheets =
            Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Maps resource paths to stylesheet lists that use them.
     * Key: resource path (e.g., "com/example/app.css")
     * Value: List of WeakReferences to ObservableList<String> stylesheets
     *
     * <p>WeakReferences allow automatic cleanup when stylesheet lists are GC'd.
     */
    private final Map<String, List<WeakReference<ObservableList<String>>>> stylesheetOwners =
            new ConcurrentHashMap<>();

    /**
     * The project root directory, used for finding source files.
     */
    private volatile Path projectRoot;

    /**
     * Whether global monitoring is currently active.
     */
    private volatile boolean monitoring = false;

    /**
     * Listener for Window list changes (to detect new windows).
     */
    private ListChangeListener<Window> windowListListener;

    /**
     * Creates a new GlobalCssMonitor instance.
     */
    public GlobalCssMonitor() {
    }

    /**
     * Sets the project root directory.
     *
     * <p>This is used to locate source files for CSS when converting classpath URIs
     * to file:// URIs.
     *
     * @param projectRoot the project root directory
     */
    public void setProjectRoot(Path projectRoot) {
        this.projectRoot = projectRoot;
        logger.log(Level.FINE, "Project root set to: {0}", projectRoot);
    }

    /**
     * Returns the current project root.
     *
     * @return the project root, or null if not set
     */
    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * Starts global CSS monitoring.
     *
     * <p>This method monitors all existing windows and scenes, and registers
     * listeners to detect new windows, scenes, and nodes as they are added.
     *
     * <p>Must be called from the JavaFX Application Thread or will use
     * Platform.runLater() to execute on the FX thread.
     */
    public void startMonitoring() {
        if (monitoring) {
            return;
        }

        monitoring = true;
        logger.log(Level.FINE, "Starting global CSS monitoring");

        Runnable startTask = () -> {
            // Monitor all existing windows
            ObservableList<Window> windows = Window.getWindows();
            for (Window window : windows) {
                monitorWindow(window);
            }

            // Listen for new windows
            windowListListener = change -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        for (Window window : change.getAddedSubList()) {
                            monitorWindow(window);
                        }
                    }
                }
            };
            windows.addListener(windowListListener);

            logger.log(Level.INFO, "Global CSS monitoring started, tracking {0} window(s)",
                    windows.size());
        };

        if (Platform.isFxApplicationThread()) {
            startTask.run();
        } else {
            Platform.runLater(startTask);
        }
    }

    /**
     * Stops global CSS monitoring and releases resources.
     */
    public void stopMonitoring() {
        if (!monitoring) {
            return;
        }

        monitoring = false;

        Platform.runLater(() -> {
            // Remove window listener
            if (windowListListener != null) {
                Window.getWindows().removeListener(windowListListener);
                windowListListener = null;
            }

            // Clear tracked state
            monitoredScenes.clear();
            monitoredNodes.clear();
            listenedStylesheets.clear();
            stylesheetOwners.clear();

            logger.log(Level.FINE, "Global CSS monitoring stopped");
        });
    }

    /**
     * Returns whether global monitoring is active.
     *
     * @return true if monitoring is active
     */
    public boolean isMonitoring() {
        return monitoring;
    }

    /**
     * Monitors a window for CSS changes.
     */
    private void monitorWindow(Window window) {
        if (!monitoring) {
            return;
        }

        // Monitor current scene
        Scene scene = window.getScene();
        if (scene != null) {
            monitorScene(scene);
        }

        // Listen for scene changes
        window.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && monitoring) {
                monitorScene(newScene);
            }
        });

        logger.log(Level.FINE, "Monitoring window: {0}", window);
    }

    /**
     * Monitors a scene for CSS changes.
     */
    private void monitorScene(Scene scene) {
        if (!monitoring || scene == null) {
            return;
        }

        // Check if already monitored
        if (!monitoredScenes.add(scene)) {
            return;
        }

        logger.log(Level.FINE, "Monitoring scene: {0}", scene);

        // Monitor scene-level stylesheets
        registerStylesheetList(scene.getStylesheets(), "Scene");

        // Monitor current root
        Parent root = scene.getRoot();
        if (root != null) {
            monitorNodeRecursively(root);
        }

        // Listen for root changes
        scene.rootProperty().addListener((obs, oldRoot, newRoot) -> {
            if (newRoot != null && monitoring) {
                monitorNodeRecursively(newRoot);
            }
        });
    }

    /**
     * Recursively monitors a node and all its descendants for CSS changes.
     */
    private void monitorNodeRecursively(Node node) {
        if (!monitoring || node == null) {
            return;
        }

        if (!(node instanceof Parent parent)) {
            return;
        }

        // Check if already monitored
        if (!monitoredNodes.add(parent)) {
            return;
        }

        // Register this node's stylesheets (includes adding listener)
        // Note: registerStylesheetList handles empty lists too, for future additions
        registerStylesheetList(parent.getStylesheets(), parent.getClass().getSimpleName());

        // Monitor current children
        ObservableList<Node> children = parent.getChildrenUnmodifiable();
        for (Node child : children) {
            monitorNodeRecursively(child);
        }

        // Listen for new children
        children.addListener((ListChangeListener<Node>) change -> {
            while (change.next()) {
                if (change.wasAdded() && monitoring) {
                    for (Node addedChild : change.getAddedSubList()) {
                        monitorNodeRecursively(addedChild);
                    }
                }
            }
        });
    }

    /**
     * Registers a stylesheet list for monitoring.
     *
     * <p>This method handles both initial registration of existing stylesheets
     * and sets up a listener for future changes. It's safe to call multiple times
     * for the same list - duplicate listeners are prevented.
     *
     * @param stylesheets the stylesheet list to monitor (may be empty)
     * @param ownerName   descriptive name for logging
     */
    private void registerStylesheetList(ObservableList<String> stylesheets, String ownerName) {
        if (stylesheets == null) {
            return;
        }

        // Check if we've already added a listener to this list
        boolean alreadyListening = !listenedStylesheets.add(stylesheets);

        // Always register existing stylesheets (they might be new URIs)
        for (String uri : stylesheets) {
            registerStylesheetUri(uri, stylesheets);
        }

        // Only add listener once per stylesheet list
        if (alreadyListening) {
            return;
        }

        // Listen for future changes
        stylesheets.addListener((ListChangeListener<String>) change -> {
            while (change.next()) {
                if (change.wasAdded() && monitoring) {
                    for (String uri : change.getAddedSubList()) {
                        registerStylesheetUri(uri, stylesheets);
                    }
                }
            }
        });

        if (!stylesheets.isEmpty()) {
            logger.log(Level.FINE, "Registered {0} stylesheet(s) from {1}",
                    new Object[]{stylesheets.size(), ownerName});
        }
    }

    /**
     * Registers a single stylesheet URI for monitoring.
     */
    private void registerStylesheetUri(String uri, ObservableList<String> owner) {
        if (uri == null || uri.isEmpty()) {
            return;
        }

        String resourcePath = StylesheetUriConverter.toResourcePath(uri);
        if (resourcePath == null) {
            logger.log(Level.FINE, "Could not convert URI to resource path: {0}", uri);
            return;
        }

        // Add to owners map with weak reference
        stylesheetOwners.computeIfAbsent(resourcePath, k -> new CopyOnWriteArrayList<>())
                .add(new WeakReference<>(owner));

        logger.log(Level.FINEST, "Registered stylesheet: {0} -> {1}", new Object[]{uri, resourcePath});
    }

    /**
     * Refreshes all stylesheets that match the given resource path.
     *
     * <p>This method is called when a CSS file change is detected. It finds all
     * stylesheet lists that use this resource and refreshes them by replacing
     * the classpath URI with a file:// URI pointing to the source file.
     *
     * @param changedResourcePath the resource path that changed (e.g., "com/example/app.css")
     */
    public void refreshStylesheet(String changedResourcePath) {
        if (!monitoring || changedResourcePath == null) {
            return;
        }

        List<WeakReference<ObservableList<String>>> owners = stylesheetOwners.get(changedResourcePath);
        if (owners == null || owners.isEmpty()) {
            logger.log(Level.FINE, "No registered owners for stylesheet: {0}", changedResourcePath);
            return;
        }

        // Find source file URI
        String sourceFileUri = null;
        if (projectRoot != null) {
            Path sourceFile = StylesheetUriConverter.findSourceFile(changedResourcePath, projectRoot);
            if (sourceFile != null) {
                sourceFileUri = sourceFile.toUri().toString();
            }
        }

        final String finalSourceFileUri = sourceFileUri;

        Platform.runLater(() -> {
            int refreshedCount = 0;

            Iterator<WeakReference<ObservableList<String>>> iterator = owners.iterator();
            while (iterator.hasNext()) {
                WeakReference<ObservableList<String>> ref = iterator.next();
                ObservableList<String> stylesheets = ref.get();

                // Remove stale references
                if (stylesheets == null) {
                    iterator.remove();
                    continue;
                }

                // Refresh matching stylesheets in this list
                if (refreshStylesheetList(stylesheets, changedResourcePath, finalSourceFileUri)) {
                    refreshedCount++;
                }
            }

            if (refreshedCount > 0) {
                logger.log(Level.INFO, "Refreshed stylesheet {0} in {1} location(s)",
                        new Object[]{changedResourcePath, refreshedCount});
            }
        });
    }

    /**
     * Refreshes matching stylesheets in a single stylesheet list.
     *
     * @param stylesheets         the stylesheet list to refresh
     * @param changedResourcePath the resource path that changed
     * @param sourceFileUri       the source file URI to use (may be null)
     * @return true if any stylesheet was refreshed
     */
    private boolean refreshStylesheetList(ObservableList<String> stylesheets,
                                          String changedResourcePath,
                                          String sourceFileUri) {
        boolean refreshed = false;

        for (int i = 0; i < stylesheets.size(); i++) {
            String uri = stylesheets.get(i);

            if (StylesheetUriConverter.matchesResourcePath(uri, changedResourcePath)) {
                String newUri;

                if (sourceFileUri != null) {
                    // Replace with file:// URI pointing to source file
                    // This is the key to making hot reload work!
                    newUri = sourceFileUri;
                } else {
                    // Fallback: remove query string and do in-place replacement
                    // This may or may not work depending on JavaFX caching
                    newUri = StylesheetUriConverter.removeQueryString(uri);
                }

                // Only update if different (avoid unnecessary events)
                if (!newUri.equals(uri)) {
                    stylesheets.set(i, newUri);
                    refreshed = true;

                    logger.log(Level.FINE, "Replaced stylesheet: {0} -> {1}",
                            new Object[]{uri, newUri});
                } else {
                    // Same URI - do in-place refresh by remove and re-add
                    stylesheets.remove(i);
                    stylesheets.add(i, uri);
                    refreshed = true;

                    logger.log(Level.FINE, "In-place refresh: {0}", uri);
                }
            }
        }

        return refreshed;
    }

    /**
     * Refreshes all stylesheets across all monitored nodes.
     *
     * <p>This is a fallback method that can be used when the specific changed
     * resource path is unknown.
     */
    public void refreshAllStylesheets() {
        if (!monitoring) {
            return;
        }

        Platform.runLater(() -> {
            for (Map.Entry<String, List<WeakReference<ObservableList<String>>>> entry :
                    stylesheetOwners.entrySet()) {

                String resourcePath = entry.getKey();
                String sourceFileUri = null;

                if (projectRoot != null) {
                    Path sourceFile = StylesheetUriConverter.findSourceFile(resourcePath, projectRoot);
                    if (sourceFile != null) {
                        sourceFileUri = sourceFile.toUri().toString();
                    }
                }

                for (WeakReference<ObservableList<String>> ref : entry.getValue()) {
                    ObservableList<String> stylesheets = ref.get();
                    if (stylesheets != null) {
                        refreshStylesheetList(stylesheets, resourcePath, sourceFileUri);
                    }
                }
            }

            logger.log(Level.INFO, "Refreshed all stylesheets");
        });
    }

    /**
     * Returns the number of monitored scenes.
     *
     * @return the count of monitored scenes
     */
    public int getMonitoredSceneCount() {
        return monitoredScenes.size();
    }

    /**
     * Returns the number of monitored nodes.
     *
     * @return the count of monitored nodes
     */
    public int getMonitoredNodeCount() {
        return monitoredNodes.size();
    }

    /**
     * Returns the number of tracked resource paths.
     *
     * @return the count of tracked stylesheet resource paths
     */
    public int getTrackedStylesheetCount() {
        return stylesheetOwners.size();
    }

    /**
     * Cleans up stale weak references.
     *
     * <p>This is called automatically during refresh operations, but can also
     * be called manually if needed.
     */
    public void cleanupStaleReferences() {
        for (List<WeakReference<ObservableList<String>>> refs : stylesheetOwners.values()) {
            refs.removeIf(ref -> ref.get() == null);
        }

        // Remove entries with no remaining references
        stylesheetOwners.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Resets the monitor to its initial state.
     *
     * <p>Stops monitoring and clears all tracked state.
     */
    public void reset() {
        stopMonitoring();
        projectRoot = null;
    }
}