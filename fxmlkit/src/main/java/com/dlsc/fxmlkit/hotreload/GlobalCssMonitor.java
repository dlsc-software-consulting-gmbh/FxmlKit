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
import javafx.stage.Window;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors all Scene stylesheets for CSS hot reload.
 *
 * <p>Tracks two categories of stylesheets:
 * <ul>
 *   <li>Normal stylesheets - {@code scene.getStylesheets()}, {@code parent.getStylesheets()},
 *       {@code subScene.getStylesheets()}</li>
 *   <li>User Agent stylesheets - Application, Scene, and SubScene levels</li>
 * </ul>
 *
 * <p>For Application-level User Agent Stylesheet, use the bridged property via
 * {@link #applicationUserAgentStylesheetProperty()} since {@link Application} only
 * provides static getter/setter without observable property support.
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
    private volatile String applicationUAResourcePath;

    /**
     * Maps resource paths to Scenes using them as UA stylesheet.
     */
    private final Map<String, List<WeakReference<Scene>>> sceneUAOwners = new ConcurrentHashMap<>();

    /**
     * Maps resource paths to SubScenes using them as UA stylesheet.
     */
    private final Map<String, List<WeakReference<SubScene>>> subSceneUAOwners = new ConcurrentHashMap<>();

    private volatile Path projectRoot;

    /**
     * Whether monitoring is active.
     */
    private volatile boolean monitoring = false;

    /**
     * Tracks monitored scenes. Uses WeakHashMap so scenes can be garbage collected.
     */
    private final Map<Scene, Boolean> monitoredScenes = new WeakHashMap<>();

    /**
     * Tracks monitored nodes to prevent duplicate monitoring.
     */
    private final Map<Node, Boolean> monitoredNodes = new WeakHashMap<>();

    /**
     * Tracks stylesheet lists that already have listeners.
     */
    private final Set<ObservableList<String>> listenedStylesheets = ConcurrentHashMap.newKeySet();

    /**
     * Maps resource paths to their stylesheet list owners.
     */
    private final Map<String, List<WeakReference<ObservableList<String>>>> stylesheetOwners =
            new ConcurrentHashMap<>();

    /**
     * Listener for window list changes.
     */
    private ListChangeListener<Window> windowListListener;

    public GlobalCssMonitor() {
        setupApplicationUserAgentStylesheetBridge();
    }

    /**
     * Returns the Application-level User Agent Stylesheet property.
     *
     * <p>This property bridges to {@link Application#setUserAgentStylesheet(String)},
     * enabling reactive binding and hot reload support.
     *
     * @return the Application UA stylesheet property
     */
    public StringProperty applicationUserAgentStylesheetProperty() {
        return applicationUserAgentStylesheet;
    }

    private void setupApplicationUserAgentStylesheetBridge() {
        // Sync Application's current value if already set
        String initial = Application.getUserAgentStylesheet();
        if (initial != null && !initial.equals(applicationUserAgentStylesheet.get())) {
            applicationUserAgentStylesheet.set(initial);
        }

        // Listen for property changes
        applicationUserAgentStylesheet.addListener((obs, oldUri, newUri) -> {
            // Sync to Application
            Application.setUserAgentStylesheet(newUri);

            // Update hot reload monitoring
            if (monitoring) {
                if (oldUri != null) {
                    unregisterApplicationUserAgentStylesheet(oldUri);
                }
                if (newUri != null) {
                    registerApplicationUserAgentStylesheet(newUri);
                }
            }

            logger.log(Level.FINE,
                    "Application user agent stylesheet changed: {0} -> {1}",
                    new Object[]{oldUri, newUri});
        });
    }

    private void registerApplicationUserAgentStylesheet(String uri) {
        String resourcePath = StylesheetUriConverter.toResourcePath(uri);
        if (resourcePath != null) {
            applicationUAResourcePath = resourcePath;
            logger.log(Level.FINE, "Registered Application UA stylesheet: {0}", resourcePath);
        }
    }

    private void unregisterApplicationUserAgentStylesheet(String uri) {
        String resourcePath = StylesheetUriConverter.toResourcePath(uri);
        if (resourcePath != null && resourcePath.equals(applicationUAResourcePath)) {
            applicationUAResourcePath = null;
            logger.log(Level.FINE, "Unregistered Application UA stylesheet: {0}", resourcePath);
        }
    }

    public void setProjectRoot(Path projectRoot) {
        this.projectRoot = projectRoot;
        logger.log(Level.FINE, "GlobalCssMonitor project root: {0}", projectRoot);
    }

    /**
     * Returns the project root.
     *
     * @return the project root, or null if not set
     */
    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * Starts monitoring all windows and scenes.
     */
    public void startMonitoring() {
        if (monitoring) {
            return;
        }

        monitoring = true;

        Platform.runLater(() -> {
            // Register Application UA stylesheet if set
            String appUA = applicationUserAgentStylesheet.get();
            if (appUA != null) {
                registerApplicationUserAgentStylesheet(appUA);
            }

            // Monitor existing windows
            for (Window window : Window.getWindows()) {
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
            Window.getWindows().addListener(windowListListener);

            int windowCount = Window.getWindows().size();
            logger.log(Level.INFO, "Global CSS monitoring started, tracking {0} window(s)", windowCount);
        });
    }

    /**
     * Stops monitoring.
     */
    public void stopMonitoring() {
        if (!monitoring) {
            return;
        }

        monitoring = false;
        projectRoot = null;
        applicationUAResourcePath = null;

        Platform.runLater(() -> {
            if (windowListListener != null) {
                Window.getWindows().removeListener(windowListListener);
                windowListListener = null;
            }

            synchronized (monitoredScenes) {
                monitoredScenes.clear();
            }
            synchronized (monitoredNodes) {
                monitoredNodes.clear();
            }
            listenedStylesheets.clear();
            stylesheetOwners.clear();
            sceneUAOwners.clear();
            subSceneUAOwners.clear();

            logger.log(Level.FINE, "Global CSS monitoring stopped");
        });
    }

    /**
     * Returns whether monitoring is active.
     *
     * @return true if monitoring
     */
    public boolean isMonitoring() {
        return monitoring;
    }

    /**
     * Returns the number of monitored scenes.
     *
     * @return scene count
     */
    public int getMonitoredSceneCount() {
        synchronized (monitoredScenes) {
            return monitoredScenes.size();
        }
    }

    /**
     * Returns the number of tracked stylesheet resource paths.
     *
     * @return stylesheet count
     */
    public int getTrackedStylesheetCount() {
        return stylesheetOwners.size();
    }

    private void monitorWindow(Window window) {
        Scene scene = window.getScene();
        if (scene != null) {
            monitorScene(scene);
        }

        window.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                monitorScene(newScene);
            }
        });

        logger.log(Level.FINE, "Monitoring window: {0}", window.getClass().getSimpleName());
    }

    /**
     * Monitors a scene for stylesheet changes.
     */
    private void monitorScene(Scene scene) {
        synchronized (monitoredScenes) {
            if (monitoredScenes.containsKey(scene)) {
                return;
            }
            monitoredScenes.put(scene, Boolean.TRUE);
        }

        // Monitor scene normal stylesheets
        registerStylesheetList(scene.getStylesheets());

        // Monitor scene User Agent Stylesheet
        monitorSceneUserAgentStylesheet(scene);

        // Monitor root and all children
        Parent root = scene.getRoot();
        if (root != null) {
            monitorNodeRecursively(root);
        }

        // Listen for root changes
        scene.rootProperty().addListener((obs, oldRoot, newRoot) -> {
            if (newRoot != null) {
                monitorNodeRecursively(newRoot);
            }
        });

        logger.log(Level.FINE, "Monitoring scene with {0} stylesheet(s)",
                scene.getStylesheets().size());
    }

    private void monitorSceneUserAgentStylesheet(Scene scene) {
        String current = scene.getUserAgentStylesheet();
        if (current != null) {
            registerSceneUserAgentStylesheet(current, scene);
        }

        scene.userAgentStylesheetProperty().addListener((obs, oldUri, newUri) -> {
            if (oldUri != null) {
                unregisterSceneUserAgentStylesheet(oldUri, scene);
            }
            if (newUri != null) {
                registerSceneUserAgentStylesheet(newUri, scene);
            }
            logger.log(Level.FINE, "Scene UA stylesheet changed: {0} -> {1}",
                    new Object[]{oldUri, newUri});
        });
    }

    private void registerSceneUserAgentStylesheet(String uri, Scene scene) {
        String resourcePath = StylesheetUriConverter.toResourcePath(uri);
        if (resourcePath != null) {
            sceneUAOwners.computeIfAbsent(resourcePath, k -> new CopyOnWriteArrayList<>())
                    .add(new WeakReference<>(scene));
            logger.log(Level.FINE, "Registered Scene UA stylesheet: {0}", resourcePath);
        }
    }

    private void unregisterSceneUserAgentStylesheet(String uri, Scene scene) {
        String resourcePath = StylesheetUriConverter.toResourcePath(uri);
        if (resourcePath != null) {
            List<WeakReference<Scene>> owners = sceneUAOwners.get(resourcePath);
            if (owners != null) {
                owners.removeIf(ref -> {
                    Scene s = ref.get();
                    return s == null || s == scene;
                });
            }
        }
    }

    private void monitorSubSceneUserAgentStylesheet(SubScene subScene) {
        String current = subScene.getUserAgentStylesheet();
        if (current != null) {
            registerSubSceneUserAgentStylesheet(current, subScene);
        }

        subScene.userAgentStylesheetProperty().addListener((obs, oldUri, newUri) -> {
            if (oldUri != null) {
                unregisterSubSceneUserAgentStylesheet(oldUri, subScene);
            }
            if (newUri != null) {
                registerSubSceneUserAgentStylesheet(newUri, subScene);
            }
            logger.log(Level.FINE, "SubScene UA stylesheet changed: {0} -> {1}",
                    new Object[]{oldUri, newUri});
        });
    }

    private void registerSubSceneUserAgentStylesheet(String uri, SubScene subScene) {
        String resourcePath = StylesheetUriConverter.toResourcePath(uri);
        if (resourcePath != null) {
            subSceneUAOwners.computeIfAbsent(resourcePath, k -> new CopyOnWriteArrayList<>())
                    .add(new WeakReference<>(subScene));
            logger.log(Level.FINE, "Registered SubScene UA stylesheet: {0}", resourcePath);
        }
    }

    private void unregisterSubSceneUserAgentStylesheet(String uri, SubScene subScene) {
        String resourcePath = StylesheetUriConverter.toResourcePath(uri);
        if (resourcePath != null) {
            List<WeakReference<SubScene>> owners = subSceneUAOwners.get(resourcePath);
            if (owners != null) {
                owners.removeIf(ref -> {
                    SubScene s = ref.get();
                    return s == null || s == subScene;
                });
            }
        }
    }

    private void monitorNodeRecursively(Node node) {
        if (node == null) {
            return;
        }

        synchronized (monitoredNodes) {
            if (monitoredNodes.containsKey(node)) {
                return;
            }
            monitoredNodes.put(node, Boolean.TRUE);
        }

        // SubScene has its own UA stylesheet (not a Parent subclass, no getStylesheets())
        if (node instanceof SubScene subScene) {
            monitorSubSceneUserAgentStylesheet(subScene);

            Parent subRoot = subScene.getRoot();
            if (subRoot != null) {
                monitorNodeRecursively(subRoot);
            }

            subScene.rootProperty().addListener((obs, oldRoot, newRoot) -> {
                if (newRoot != null) {
                    monitorNodeRecursively(newRoot);
                }
            });
        }

        if (node instanceof Parent parent) {
            // Monitor this parent's stylesheets
            registerStylesheetList(parent.getStylesheets());

            // Monitor existing children
            for (Node child : parent.getChildrenUnmodifiable()) {
                monitorNodeRecursively(child);
            }

            // Listen for new children
            parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        for (Node added : change.getAddedSubList()) {
                            monitorNodeRecursively(added);
                        }
                    }
                }
            });
        }
    }

    /**
     * Registers a stylesheet list for monitoring.
     */
    private void registerStylesheetList(ObservableList<String> stylesheets) {
        if (stylesheets == null) {
            return;
        }

        // Register existing stylesheet URIs
        for (String uri : stylesheets) {
            registerStylesheetUri(uri, stylesheets);
        }

        // Add listener for future changes (only once per list)
        // Important: register listener even for empty lists so future additions are monitored
        if (listenedStylesheets.add(stylesheets)) {
            stylesheets.addListener((ListChangeListener<String>) change -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        for (String uri : change.getAddedSubList()) {
                            registerStylesheetUri(uri, stylesheets);
                        }
                    }
                }
            });
        }
    }

    /**
     * Registers a single stylesheet URI.
     */
    private void registerStylesheetUri(String uri, ObservableList<String> owner) {
        String resourcePath = StylesheetUriConverter.toResourcePath(uri);
        if (resourcePath == null) {
            logger.log(Level.FINE, "Could not convert URI to resource path: {0}", uri);
            return;
        }

        stylesheetOwners.computeIfAbsent(resourcePath, k -> new CopyOnWriteArrayList<>())
                .add(new WeakReference<>(owner));

        logger.log(Level.FINE, "Registered stylesheet: {0}", resourcePath);
    }

    /**
     * Refreshes all stylesheets matching the given resource path.
     *
     * @param resourcePath the resource path (e.g., "com/example/app.css")
     */
    public void refreshStylesheet(String resourcePath) {
        if (!monitoring || resourcePath == null) {
            return;
        }

        // Find source file URI (for bypassing cache)
        String sourceFileUri = null;
        if (projectRoot != null) {
            Path sourceFile = StylesheetUriConverter.findSourceFile(resourcePath, projectRoot);
            if (sourceFile != null) {
                sourceFileUri = sourceFile.toUri().toString();
            }
        }
        final String finalSourceUri = sourceFileUri;

        Platform.runLater(() -> {
            int refreshCount = 0;

            refreshCount += refreshNormalStylesheets(resourcePath, finalSourceUri);
            refreshCount += refreshSceneUserAgentStylesheets(resourcePath, finalSourceUri);
            refreshCount += refreshSubSceneUserAgentStylesheets(resourcePath, finalSourceUri);
            refreshCount += refreshApplicationUserAgentStylesheet(resourcePath, finalSourceUri);

            if (refreshCount > 0) {
                logger.log(Level.INFO, "Refreshed {0} stylesheet reference(s) for: {1}",
                        new Object[]{refreshCount, resourcePath});
            }
        });
    }

    /**
     * Refreshes normal stylesheets (Scene and Parent level).
     * Skips stale WeakReferences during iteration.
     */
    private int refreshNormalStylesheets(String resourcePath, String sourceFileUri) {
        List<WeakReference<ObservableList<String>>> owners = stylesheetOwners.get(resourcePath);
        if (owners == null || owners.isEmpty()) {
            return 0;
        }

        int refreshCount = 0;

        for (WeakReference<ObservableList<String>> ref : owners) {
            ObservableList<String> stylesheets = ref.get();
            if (stylesheets == null) {
                continue;  // Skip stale reference
            }

            // Find and refresh matching stylesheets
            for (int i = 0; i < stylesheets.size(); i++) {
                String uri = stylesheets.get(i);
                if (StylesheetUriConverter.matchesResourcePath(uri, resourcePath)) {
                    if (sourceFileUri != null) {
                        stylesheets.set(i, sourceFileUri);
                    } else {
                        // Fallback: remove and re-add in place
                        stylesheets.remove(i);
                        stylesheets.add(i, uri);
                    }
                    refreshCount++;
                }
            }
        }

        return refreshCount;
    }

    /**
     * Refreshes Scene-level User Agent Stylesheets.
     * Skips stale WeakReferences during iteration.
     */
    private int refreshSceneUserAgentStylesheets(String resourcePath, String sourceFileUri) {
        List<WeakReference<Scene>> owners = sceneUAOwners.get(resourcePath);
        if (owners == null || owners.isEmpty()) {
            return 0;
        }

        int refreshCount = 0;

        for (WeakReference<Scene> ref : owners) {
            Scene scene = ref.get();
            if (scene == null) {
                continue;  // Skip stale reference
            }

            String currentUri = scene.getUserAgentStylesheet();
            if (currentUri != null && StylesheetUriConverter.matchesResourcePath(currentUri, resourcePath)) {
                if (sourceFileUri != null) {
                    scene.setUserAgentStylesheet(sourceFileUri);
                } else {
                    scene.setUserAgentStylesheet(null);
                    scene.setUserAgentStylesheet(currentUri);
                }
                refreshCount++;
            }
        }

        return refreshCount;
    }

    /**
     * Refreshes SubScene-level User Agent Stylesheets.
     * Skips stale WeakReferences during iteration.
     */
    private int refreshSubSceneUserAgentStylesheets(String resourcePath, String sourceFileUri) {
        List<WeakReference<SubScene>> owners = subSceneUAOwners.get(resourcePath);
        if (owners == null || owners.isEmpty()) {
            return 0;
        }

        int refreshCount = 0;

        for (WeakReference<SubScene> ref : owners) {
            SubScene subScene = ref.get();
            if (subScene == null) {
                continue;  // Skip stale reference
            }

            String currentUri = subScene.getUserAgentStylesheet();
            if (currentUri != null && StylesheetUriConverter.matchesResourcePath(currentUri, resourcePath)) {
                if (sourceFileUri != null) {
                    subScene.setUserAgentStylesheet(sourceFileUri);
                } else {
                    subScene.setUserAgentStylesheet(null);
                    subScene.setUserAgentStylesheet(currentUri);
                }
                refreshCount++;
            }
        }

        return refreshCount;
    }

    private int refreshApplicationUserAgentStylesheet(String resourcePath, String sourceFileUri) {
        if (!resourcePath.equals(applicationUAResourcePath)) {
            return 0;
        }

        String currentUri = applicationUserAgentStylesheet.get();
        if (currentUri == null) {
            return 0;
        }

        if (sourceFileUri != null) {
            Application.setUserAgentStylesheet(sourceFileUri);
        } else {
            Application.setUserAgentStylesheet(null);
            Application.setUserAgentStylesheet(currentUri);
        }

        logger.log(Level.FINE, "Refreshed Application UA stylesheet: {0}", resourcePath);
        return 1;
    }
}
