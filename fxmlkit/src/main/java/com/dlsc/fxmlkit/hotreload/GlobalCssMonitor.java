package com.dlsc.fxmlkit.hotreload;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
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
 * Monitors all Scene stylesheets for CSS hot reload.
 *
 * <p>This class traverses the entire scene graph to find and track all
 * stylesheet lists. When a CSS file changes, it refreshes the corresponding
 * stylesheets by replacing classpath URIs with file:// URIs.
 */
public final class GlobalCssMonitor {

    private static final Logger logger = Logger.getLogger(GlobalCssMonitor.class.getName());

    /**
     * Project root for finding source files.
     */
    private volatile Path projectRoot;

    /**
     * Whether monitoring is active.
     */
    private volatile boolean monitoring = false;

    /**
     * Tracks monitored scenes to prevent duplicate monitoring.
     * Uses WeakHashMap so scenes can be garbage collected.
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
     * Key: resource path (e.g., "com/example/app.css")
     * Value: List of WeakReferences to ObservableList<String>
     */
    private final Map<String, List<WeakReference<ObservableList<String>>>> stylesheetOwners =
            new ConcurrentHashMap<>();

    /**
     * Listener for window list changes.
     */
    private ListChangeListener<Window> windowListListener;

    /**
     * Sets the project root for source file lookup.
     *
     * @param projectRoot the project root directory
     */
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

    /**
     * Monitors a window for scene changes.
     */
    private void monitorWindow(Window window) {
        Scene scene = window.getScene();
        if (scene != null) {
            monitorScene(scene);
        }

        // Listen for scene changes
        ChangeListener<Scene> sceneListener = (obs, oldScene, newScene) -> {
            if (newScene != null) {
                monitorScene(newScene);
            }
        };
        window.sceneProperty().addListener(sceneListener);

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

        // Monitor scene stylesheets
        registerStylesheetList(scene.getStylesheets());

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

    /**
     * Recursively monitors a node and its children.
     */
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

        List<WeakReference<ObservableList<String>>> owners = stylesheetOwners.get(resourcePath);
        if (owners == null || owners.isEmpty()) {
            logger.log(Level.FINE, "No stylesheet lists found for: {0}", resourcePath);
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

            Iterator<WeakReference<ObservableList<String>>> iterator = owners.iterator();
            while (iterator.hasNext()) {
                WeakReference<ObservableList<String>> ref = iterator.next();
                ObservableList<String> stylesheets = ref.get();

                if (stylesheets == null) {
                    // Clean up stale reference
                    iterator.remove();
                    continue;
                }

                // Find and refresh matching stylesheets
                for (int i = 0; i < stylesheets.size(); i++) {
                    String uri = stylesheets.get(i);
                    if (StylesheetUriConverter.matchesResourcePath(uri, resourcePath)) {
                        if (finalSourceUri != null) {
                            // Replace with file:// URI to bypass cache
                            stylesheets.set(i, finalSourceUri);
                        } else {
                            // Fallback: remove and re-add in place
                            stylesheets.remove(i);
                            stylesheets.add(i, uri);
                        }
                        refreshCount++;
                    }
                }
            }

            if (refreshCount > 0) {
                logger.log(Level.INFO, "Refreshed {0} stylesheet reference(s) for: {1}",
                        new Object[]{refreshCount, resourcePath});
            }
        });
    }
}