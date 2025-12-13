package com.dlsc.fxmlkit.hotreload;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.layout.Region;
import javafx.stage.Window;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors stylesheets for CSS hot reload (Simplified Version).
 *
 * <p>Tracks multiple categories of stylesheets:
 * <ul>
 *   <li><b>Normal stylesheets</b> - {@code scene.getStylesheets()}, {@code parent.getStylesheets()}</li>
 *   <li><b>User Agent stylesheets</b> - Application, Scene, SubScene levels</li>
 *   <li><b>Control stylesheets</b> - Custom controls with {@code getUserAgentStylesheet()} override</li>
 * </ul>
 *
 * <h2>Key Changes (v2)</h2>
 * <ul>
 *   <li>No longer holds a reference to HotReloadManager (avoids circular dependency)</li>
 *   <li>Uses HotReloadManager.getInstance() when needed</li>
 *   <li>Removed complex project root tracking</li>
 * </ul>
 *
 * @see HotReloadManager
 */
public final class GlobalCssMonitor {

    private static final Logger logger = Logger.getLogger(GlobalCssMonitor.class.getName());

    /**
     * Bridge property for Application-level User Agent Stylesheet.
     * Syncs to {@link Application#setUserAgentStylesheet(String)} on changes.
     */
    private final StringProperty applicationUserAgentStylesheet =
            new SimpleStringProperty(this, "applicationUserAgentStylesheet");

    /**
     * Resource path of the current Application UA stylesheet.
     */
    private String applicationUAResourcePath;

    /**
     * Maps resource paths to Scenes using them as UA stylesheet.
     */
    private final Map<String, List<WeakReference<Scene>>> sceneUAOwners = new HashMap<>();

    /**
     * Maps resource paths to SubScenes using them as UA stylesheet.
     */
    private final Map<String, List<WeakReference<SubScene>>> subSceneUAOwners = new HashMap<>();

    /**
     * Tracks Regions that have had their getUserAgentStylesheet() promoted.
     */
    private final Map<Region, String> promotedUserAgentStylesheets = new WeakHashMap<>();

    /**
     * Tracks monitored scenes.
     */
    private final Map<Scene, Boolean> monitoredScenes = new WeakHashMap<>();

    /**
     * Tracks monitored nodes.
     */
    private final Map<Node, Boolean> monitoredNodes = new WeakHashMap<>();

    /**
     * Tracks stylesheet lists that already have listeners.
     */
    private final Set<ObservableList<String>> listenedStylesheets = new HashSet<>();

    /**
     * Maps resource paths to their stylesheet list owners.
     */
    private final Map<String, List<WeakReference<ObservableList<String>>>> stylesheetOwners = new HashMap<>();

    /**
     * Whether monitoring is active.
     */
    private volatile boolean monitoring = false;

    /**
     * Whether control getUserAgentStylesheet() hot reload is enabled.
     */
    private boolean controlUAHotReloadEnabled = false;

    /**
     * Listener for window list changes.
     */
    private ListChangeListener<Window> windowListListener;

    /**
     * Creates a new GlobalCssMonitor.
     *
     * <p>No HotReloadManager reference is passed to avoid circular dependency.
     * HotReloadManager.getInstance() is used when needed.
     */
    public GlobalCssMonitor() {
        // Initialize Application UA Stylesheet property listener
        applicationUserAgentStylesheet.addListener((obs, oldVal, newVal) -> {
            // In development mode, load directly from source directory
            String uriToSet = newVal;
            if (newVal != null && HotReloadManager.getInstance().isCssHotReloadEnabled()) {
                String sourceUri = resolveToSourceUri(newVal);
                if (sourceUri != null && !sourceUri.equals(newVal)) {
                    uriToSet = sourceUri;
                    logger.log(Level.FINE, "Loading UA stylesheet from source: {0}", sourceUri);
                }
            }
            Application.setUserAgentStylesheet(uriToSet);
            updateApplicationUAMonitoring(oldVal, newVal);
        });
    }

    /**
     * Returns the Application UA Stylesheet property.
     */
    public StringProperty applicationUserAgentStylesheetProperty() {
        return applicationUserAgentStylesheet;
    }

    /**
     * Gets the Application UA Stylesheet.
     */
    public String getApplicationUserAgentStylesheet() {
        return applicationUserAgentStylesheet.get();
    }

    /**
     * Sets the Application UA Stylesheet.
     */
    public void setApplicationUserAgentStylesheet(String stylesheet) {
        applicationUserAgentStylesheet.set(stylesheet);
    }

    /**
     * Enables or disables control UA stylesheet hot reload.
     *
     * <p>When enabled, custom controls that override getUserAgentStylesheet()
     * will have their stylesheets promoted to the normal stylesheet list for
     * hot reload monitoring.
     *
     * <p><b>Note:</b> This may affect style priority. Disabled by default.
     */
    public void setControlUAHotReloadEnabled(boolean enabled) {
        this.controlUAHotReloadEnabled = enabled;
    }

    /**
     * Returns whether control UA hot reload is enabled.
     */
    public boolean isControlUAHotReloadEnabled() {
        return controlUAHotReloadEnabled;
    }

    /**
     * Starts monitoring for CSS changes.
     */
    public void startMonitoring() {
        if (monitoring) {
            return;
        }
        monitoring = true;

        // Set up window listener to monitor new windows/scenes
        setupWindowListener();

        // Monitor existing windows
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (scene != null) {
                monitorScene(scene);
            }
        }

        logger.log(Level.FINE, "GlobalCssMonitor started");
    }

    /**
     * Stops monitoring.
     */
    public void stopMonitoring() {
        monitoring = false;

        // Remove window listener
        if (windowListListener != null) {
            Window.getWindows().removeListener(windowListListener);
            windowListListener = null;
        }

        // Clear tracking maps
        monitoredScenes.clear();
        monitoredNodes.clear();
        listenedStylesheets.clear();
        stylesheetOwners.clear();
        sceneUAOwners.clear();
        subSceneUAOwners.clear();
        promotedUserAgentStylesheets.clear();

        logger.log(Level.FINE, "GlobalCssMonitor stopped");
    }

    /**
     * Monitors a stylesheet list for changes.
     *
     * @param stylesheets the stylesheet list to monitor
     * @param onRefresh   callback when stylesheets need refresh
     */
    public void monitorStylesheets(ObservableList<String> stylesheets, Runnable onRefresh) {
        HotReloadManager manager = HotReloadManager.getInstance();

        // Register and monitor existing stylesheets
        for (String uri : stylesheets) {
            registerStylesheetUri(uri, stylesheets);
            manager.monitorStylesheet(uri, onRefresh);
        }

        // Monitor list changes
        if (!listenedStylesheets.contains(stylesheets)) {
            stylesheets.addListener((ListChangeListener<String>) change -> {
                while (change.next()) {
                    for (String added : change.getAddedSubList()) {
                        registerStylesheetUri(added, stylesheets);
                        manager.monitorStylesheet(added, onRefresh);
                    }
                }
            });
            listenedStylesheets.add(stylesheets);
        }
    }

    /**
     * Registers a stylesheet URI to the owners map.
     */
    private void registerStylesheetUri(String uri, ObservableList<String> owner) {
        String resourcePath = extractResourcePath(uri);
        if (resourcePath == null) {
            logger.log(Level.FINE, "Could not extract resource path from: {0}", uri);
            return;
        }

        List<WeakReference<ObservableList<String>>> owners =
                stylesheetOwners.computeIfAbsent(resourcePath, k -> new CopyOnWriteArrayList<>());

        // Check if already registered
        for (WeakReference<ObservableList<String>> ref : owners) {
            if (ref.get() == owner) {
                return;
            }
        }

        owners.add(new WeakReference<>(owner));
        logger.log(Level.FINE, "Registered stylesheet: {0}", resourcePath);
    }

    /**
     * Refreshes a stylesheet by removing and re-adding it.
     *
     * @param resourcePath the resource path of the stylesheet
     */
    public void refreshStylesheet(String resourcePath) {
        if (!monitoring) {
            return;
        }

        Platform.runLater(() -> {
            // Refresh normal stylesheets
            refreshNormalStylesheets(resourcePath);

            // Refresh UA stylesheets
            refreshApplicationUA(resourcePath);
            refreshSceneUA(resourcePath);
            refreshSubSceneUA(resourcePath);
        });
    }

    /**
     * Monitors a scene for stylesheet changes.
     */
    public void monitorScene(Scene scene) {
        if (scene == null || monitoredScenes.containsKey(scene)) {
            return;
        }
        monitoredScenes.put(scene, true);

        // Monitor scene stylesheets
        monitorStylesheets(scene.getStylesheets(), () -> refreshSceneStylesheets(scene));

        // Monitor scene UA stylesheet
        monitorSceneUA(scene);

        // Monitor scene root (and children)
        Parent root = scene.getRoot();
        if (root != null) {
            monitorNode(root);
        }

        // Listen for root changes
        scene.rootProperty().addListener((obs, oldRoot, newRoot) -> {
            if (newRoot != null) {
                monitorNode(newRoot);
            }
        });

        logger.log(Level.FINE, "Monitoring scene: {0}", scene);
    }

    /**
     * Monitors a node and its children for stylesheet changes.
     */
    public void monitorNode(Node node) {
        if (node == null || monitoredNodes.containsKey(node)) {
            return;
        }
        monitoredNodes.put(node, true);

        if (node instanceof Parent) {
            Parent parent = (Parent) node;
            // Monitor parent stylesheets
            monitorStylesheets(parent.getStylesheets(), () -> refreshParentStylesheets(parent));

            // Monitor children
            parent.getChildrenUnmodifiable().forEach(this::monitorNode);
            parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
                while (change.next()) {
                    change.getAddedSubList().forEach(this::monitorNode);
                }
            });
        }

        // Handle SubScene
        if (node instanceof SubScene) {
            SubScene subScene = (SubScene) node;
            monitorSubScene(subScene);
        }

        // Handle Region with custom getUserAgentStylesheet()
        if (controlUAHotReloadEnabled && node instanceof Region) {
            Region region = (Region) node;
            promoteUserAgentStylesheet(region);
        }
    }

    /**
     * Monitors a SubScene.
     */
    private void monitorSubScene(SubScene subScene) {
        // Monitor SubScene UA stylesheet
        monitorSubSceneUA(subScene);

        // Monitor SubScene root
        Parent root = subScene.getRoot();
        if (root != null) {
            monitorNode(root);
        }

        subScene.rootProperty().addListener((obs, oldRoot, newRoot) -> {
            if (newRoot != null) {
                monitorNode(newRoot);
            }
        });
    }

    /**
     * Updates Application UA monitoring when the stylesheet changes.
     */
    private void updateApplicationUAMonitoring(String oldUri, String newUri) {
        HotReloadManager manager = HotReloadManager.getInstance();

        if (newUri != null && !newUri.isEmpty()) {
            applicationUAResourcePath = extractResourcePath(newUri);
            manager.monitorStylesheet(newUri, () -> refreshApplicationUA(applicationUAResourcePath));
        } else {
            applicationUAResourcePath = null;
        }
    }

    /**
     * Monitors Scene UA stylesheet.
     */
    private void monitorSceneUA(Scene scene) {
        scene.userAgentStylesheetProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                String resourcePath = extractResourcePath(newVal);
                sceneUAOwners.computeIfAbsent(resourcePath, k -> new CopyOnWriteArrayList<>())
                        .add(new WeakReference<>(scene));

                HotReloadManager.getInstance().monitorStylesheet(newVal, () -> refreshSceneUA(resourcePath));

                // In development mode, immediately convert to source URI
                // (avoids ClassLoader cache returning stale content)
                if (HotReloadManager.getInstance().isCssHotReloadEnabled()) {
                    String sourceUri = resolveToSourceUri(newVal);
                    if (!sourceUri.equals(newVal)) {
                        Platform.runLater(() -> {
                            scene.setUserAgentStylesheet(null);
                            scene.setUserAgentStylesheet(sourceUri);
                        });
                    }
                }
            }
        });

        // Handle initial value
        String initialUA = scene.getUserAgentStylesheet();
        if (initialUA != null && !initialUA.isEmpty()) {
            String resourcePath = extractResourcePath(initialUA);
            sceneUAOwners.computeIfAbsent(resourcePath, k -> new CopyOnWriteArrayList<>())
                    .add(new WeakReference<>(scene));

            HotReloadManager.getInstance().monitorStylesheet(initialUA, () -> refreshSceneUA(resourcePath));

            // In development mode, convert initial value to source URI
            if (HotReloadManager.getInstance().isCssHotReloadEnabled()) {
                String sourceUri = resolveToSourceUri(initialUA);
                if (!sourceUri.equals(initialUA)) {
                    Platform.runLater(() -> {
                        scene.setUserAgentStylesheet(null);
                        scene.setUserAgentStylesheet(sourceUri);
                    });
                }
            }
        }
    }

    /**
     * Monitors SubScene UA stylesheet.
     */
    private void monitorSubSceneUA(SubScene subScene) {
        subScene.userAgentStylesheetProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                String resourcePath = extractResourcePath(newVal);
                subSceneUAOwners.computeIfAbsent(resourcePath, k -> new CopyOnWriteArrayList<>())
                        .add(new WeakReference<>(subScene));

                HotReloadManager.getInstance().monitorStylesheet(newVal, () -> refreshSubSceneUA(resourcePath));

                // In development mode, immediately convert to source URI
                if (HotReloadManager.getInstance().isCssHotReloadEnabled()) {
                    String sourceUri = resolveToSourceUri(newVal);
                    if (!sourceUri.equals(newVal)) {
                        Platform.runLater(() -> {
                            subScene.setUserAgentStylesheet(null);
                            subScene.setUserAgentStylesheet(sourceUri);
                        });
                    }
                }
            }
        });

        // Handle initial value
        String initialUA = subScene.getUserAgentStylesheet();
        if (initialUA != null && !initialUA.isEmpty()) {
            String resourcePath = extractResourcePath(initialUA);
            subSceneUAOwners.computeIfAbsent(resourcePath, k -> new CopyOnWriteArrayList<>())
                    .add(new WeakReference<>(subScene));

            HotReloadManager.getInstance().monitorStylesheet(initialUA, () -> refreshSubSceneUA(resourcePath));

            // In development mode, convert initial value to source URI
            if (HotReloadManager.getInstance().isCssHotReloadEnabled()) {
                String sourceUri = resolveToSourceUri(initialUA);
                if (!sourceUri.equals(initialUA)) {
                    Platform.runLater(() -> {
                        subScene.setUserAgentStylesheet(null);
                        subScene.setUserAgentStylesheet(sourceUri);
                    });
                }
            }
        }
    }

    /**
     * Promotes a Region's getUserAgentStylesheet() to its stylesheet list.
     *
     * <p>If a Region subclass returns a non-null stylesheet from getUserAgentStylesheet(),
     * it will be added to the Region's stylesheet list for hot reload monitoring.
     */
    private void promoteUserAgentStylesheet(Region region) {
        if (promotedUserAgentStylesheets.containsKey(region)) {
            return;
        }

        // Directly call the public method - no reflection needed
        String stylesheet = region.getUserAgentStylesheet();

        if (stylesheet != null && !stylesheet.isEmpty()) {
            // In development mode, use source URI to load the latest file
            // (avoids ClassLoader cache returning stale content)
            String stylesheetToAdd = HotReloadManager.getInstance().isCssHotReloadEnabled()
                    ? resolveToSourceUri(stylesheet)
                    : stylesheet;

            // Add to stylesheet list at index 0 for proper priority
            ObservableList<String> stylesheets = region.getStylesheets();
            if (!stylesheets.contains(stylesheetToAdd)) {
                stylesheets.add(0, stylesheetToAdd);
                promotedUserAgentStylesheets.put(region, stylesheet);
                logger.log(Level.FINE, "Promoted control UA stylesheet: {0}", stylesheetToAdd);

                // Register and monitor the stylesheet
                registerStylesheetUri(stylesheetToAdd, stylesheets);
                HotReloadManager.getInstance().monitorStylesheet(stylesheetToAdd,
                        () -> refreshControlUA(region, stylesheets));
            }
        }
    }

    /**
     * Refreshes a control's UA stylesheet.
     */
    private void refreshControlUA(Region region, ObservableList<String> stylesheets) {
        if (region == null || stylesheets.isEmpty()) {
            return;
        }

        String stylesheet = promotedUserAgentStylesheets.get(region);
        if (stylesheet == null) {
            return;
        }

        String stylesheetResourcePath = extractResourcePath(stylesheet);

        for (int i = 0; i < stylesheets.size(); i++) {
            String uri = stylesheets.get(i);
            String uriResourcePath = extractResourcePath(uri);

            if (stylesheetResourcePath != null && stylesheetResourcePath.equals(uriResourcePath)) {
                stylesheets.remove(i);
                String sourceUri = resolveToSourceUri(uri);
                stylesheets.add(i, sourceUri);
                logger.log(Level.INFO, "Refreshed control UA stylesheet: {0}", sourceUri);
                break;
            }
        }
    }

    /**
     * Refreshes normal stylesheets for a resource path.
     */
    private void refreshNormalStylesheets(String resourcePath) {
        List<WeakReference<ObservableList<String>>> owners = stylesheetOwners.get(resourcePath);
        if (owners == null) {
            return;
        }

        owners.removeIf(ref -> ref.get() == null);

        for (WeakReference<ObservableList<String>> ref : owners) {
            ObservableList<String> stylesheets = ref.get();
            if (stylesheets != null) {
                refreshStylesheetList(stylesheets, resourcePath);
            }
        }
    }

    /**
     * Refreshes a stylesheet list by removing and re-adding the stylesheet.
     */
    private void refreshStylesheetList(ObservableList<String> stylesheets, String resourcePath) {
        for (int i = 0; i < stylesheets.size(); i++) {
            String uri = stylesheets.get(i);
            String uriResourcePath = extractResourcePath(uri);
            if (resourcePath.equals(uriResourcePath)) {
                stylesheets.remove(i);
                String sourceUri = resolveToSourceUri(uri);
                stylesheets.add(i, sourceUri);
                logger.log(Level.INFO, "Refreshed stylesheet: {0}", sourceUri);
                break;
            }
        }
    }

    private void refreshSceneStylesheets(Scene scene) {
        if (scene == null) {
            logger.log(Level.WARNING, "Cannot refresh stylesheets: scene is null");
            return;
        }
        logger.log(Level.INFO, "Refreshing scene stylesheets, count: {0}", scene.getStylesheets().size());
        refreshAllStylesheets(scene.getStylesheets());
    }

    private void refreshParentStylesheets(Parent parent) {
        if (parent == null) {
            logger.log(Level.WARNING, "Cannot refresh stylesheets: parent is null");
            return;
        }
        logger.log(Level.INFO, "Refreshing parent stylesheets, count: {0}", parent.getStylesheets().size());
        refreshAllStylesheets(parent.getStylesheets());
    }

    private void refreshAllStylesheets(ObservableList<String> stylesheets) {
        if (stylesheets.isEmpty()) {
            return;
        }

        // Remove and re-add each stylesheet to force refresh
        // Convert target URI to source URI for development mode
        for (int i = stylesheets.size() - 1; i >= 0; i--) {
            String uri = stylesheets.get(i);
            stylesheets.remove(i);
            String sourceUri = resolveToSourceUri(uri);
            stylesheets.add(i, sourceUri);
            logger.log(Level.INFO, "Refreshed stylesheet: {0}", sourceUri);
        }
    }

    /**
     * Strips query string from URI.
     */
    private String stripQueryString(String uri) {
        int index = uri.indexOf('?');
        return index >= 0 ? uri.substring(0, index) : uri;
    }

    /**
     * Refreshes Application UA stylesheet.
     */
    private void refreshApplicationUA(String resourcePath) {
        if (resourcePath != null && resourcePath.equals(applicationUAResourcePath)) {
            String currentUA = getApplicationUserAgentStylesheet();
            if (currentUA != null) {
                // Convert classpath or target URI to source path
                String sourceUri = resolveToSourceUri(currentUA);

                // Set to null then to source path to force refresh
                Application.setUserAgentStylesheet(null);
                Application.setUserAgentStylesheet(sourceUri);
                logger.log(Level.INFO, "Refreshed Application UA stylesheet: {0}", sourceUri);
            }
        }
    }

    /**
     * Resolves a URI (classpath or file) to source file URI.
     */
    private String resolveToSourceUri(String uri) {
        if (uri == null) {
            return null;
        }

        HotReloadManager manager = HotReloadManager.getInstance();

        // 1. If already a file: URI, try to convert to source
        if (uri.startsWith("file:")) {
            Path sourcePath = manager.toSourcePath(uri);
            if (sourcePath != null && Files.exists(sourcePath)) {
                String result = sourcePath.toUri().toString();
                logger.log(Level.FINE, "Resolved file URI to source: {0} -> {1}", new Object[]{uri, result});
                return result;
            }
            return uri;
        }

        // 2. Handle classpath-style URI (e.g., com/example/style.css or /com/example/style.css)
        String resourcePath = uri.startsWith("/") ? uri.substring(1) : uri;

        // Try to get the actual URL via ClassLoader
        URL url = null;
        ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
        if (contextCL != null) {
            url = contextCL.getResource(resourcePath);
        }
        if (url == null) {
            url = GlobalCssMonitor.class.getClassLoader().getResource(resourcePath);
        }

        if (url == null) {
            logger.log(Level.FINE, "Could not find resource for classpath URI: {0}", uri);
            return uri;
        }

        // Convert to source path
        String fileUri = url.toExternalForm();
        logger.log(Level.FINE, "ClassLoader resolved: {0} -> {1}", new Object[]{uri, fileUri});

        Path sourcePath = manager.toSourcePath(fileUri);
        if (sourcePath != null && Files.exists(sourcePath)) {
            String result = sourcePath.toUri().toString();
            logger.log(Level.FINE, "Converted to source: {0}", result);
            return result;
        }

        // If file protocol, use directly
        if ("file".equals(url.getProtocol())) {
            return fileUri;
        }

        return uri;
    }

    /**
     * Refreshes Scene UA stylesheets.
     */
    private void refreshSceneUA(String resourcePath) {
        List<WeakReference<Scene>> owners = sceneUAOwners.get(resourcePath);
        if (owners == null) {
            return;
        }

        owners.removeIf(ref -> ref.get() == null);

        for (WeakReference<Scene> ref : owners) {
            Scene scene = ref.get();
            if (scene != null) {
                String ua = scene.getUserAgentStylesheet();
                if (ua != null) {
                    String sourceUri = resolveToSourceUri(ua);
                    scene.setUserAgentStylesheet(null);
                    scene.setUserAgentStylesheet(sourceUri);
                    logger.log(Level.INFO, "Refreshed Scene UA stylesheet: {0}", sourceUri);
                }
            }
        }
    }

    /**
     * Refreshes SubScene UA stylesheets.
     */
    private void refreshSubSceneUA(String resourcePath) {
        List<WeakReference<SubScene>> owners = subSceneUAOwners.get(resourcePath);
        if (owners == null) {
            return;
        }

        owners.removeIf(ref -> ref.get() == null);

        for (WeakReference<SubScene> ref : owners) {
            SubScene subScene = ref.get();
            if (subScene != null) {
                String ua = subScene.getUserAgentStylesheet();
                if (ua != null) {
                    String sourceUri = resolveToSourceUri(ua);
                    subScene.setUserAgentStylesheet(null);
                    subScene.setUserAgentStylesheet(sourceUri);
                    logger.log(Level.INFO, "Refreshed SubScene UA stylesheet: {0}", sourceUri);
                }
            }
        }
    }

    /**
     * Sets up the window listener for monitoring new windows.
     */
    private void setupWindowListener() {
        if (windowListListener != null) {
            return;
        }

        windowListListener = change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) {
                    Scene scene = window.getScene();
                    if (scene != null) {
                        monitorScene(scene);
                    }

                    // Listen for scene changes
                    window.sceneProperty().addListener((obs, oldScene, newScene) -> {
                        if (newScene != null) {
                            monitorScene(newScene);
                        }
                    });
                }
            }
        };

        Window.getWindows().addListener(windowListListener);
    }

    /**
     * Extracts resource path from a URI or file path.
     */
    private String extractResourcePath(String uri) {
        if (uri == null) {
            return null;
        }

        // Strip query string first (for cache-busting timestamps)
        uri = stripQueryString(uri);

        // Handle file:// URIs
        if (uri.startsWith("file:")) {
            String path = uri.substring(5);
            // Remove leading slashes for Windows paths
            while (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
                path = path.substring(1);
            }
            // Delegate to SourcePathConverters for consistent path extraction
            String resourcePath = SourcePathConverters.extractResourcePath(path);
            return resourcePath != null ? resourcePath : path;
        }

        // Handle classpath-style paths
        if (uri.startsWith("/")) {
            return uri.substring(1);
        }

        return uri;
    }
}