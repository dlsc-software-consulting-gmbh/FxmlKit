package com.dlsc.fxmlkit.hotreload;

import javafx.scene.Parent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Defines reload strategies for different resource file types.
 *
 * <p>Each strategy handles file changes differently based on the resource type:
 * <ul>
 *   <li>{@link #FULL_RELOAD} - Complete view reload for FXML, properties, and images</li>
 *   <li>{@link #STYLESHEET_RELOAD} - Hot-swap stylesheets without losing view state</li>
 *   <li>{@link #IGNORE} - No action for non-reloadable files like .java or .class</li>
 * </ul>
 *
 * <h2>File Type Mapping</h2>
 * <table border="1">
 *   <tr><th>Extension</th><th>Strategy</th></tr>
 *   <tr><td>.fxml</td><td>FULL_RELOAD</td></tr>
 *   <tr><td>.properties</td><td>FULL_RELOAD</td></tr>
 *   <tr><td>.png, .jpg, .gif, .svg</td><td>FULL_RELOAD</td></tr>
 *   <tr><td>.css, .bss</td><td>STYLESHEET_RELOAD (if enabled) or IGNORE</td></tr>
 *   <tr><td>.java, .class</td><td>IGNORE</td></tr>
 * </table>
 *
 * <h2>CSS Hot Reload Configuration</h2>
 * <p>CSS hot reload is enabled by default. If you prefer to use CSSFX for CSS
 * hot reload, you can disable FxmlKit's CSS handling:
 * <pre>{@code
 * // Use CSSFX instead
 * ReloadStrategy.setCssReloadEnabled(false);
 * CSSFX.start();
 * }</pre>
 */
public enum ReloadStrategy {

    /**
     * Performs a complete view reload.
     *
     * <p>Used for:
     * <ul>
     *   <li>FXML files - structure changes require full reload</li>
     *   <li>Resource bundles (.properties) - text changes</li>
     *   <li>Images (.png, .jpg, etc.) - binary resource changes</li>
     * </ul>
     *
     * <p>Note: This strategy loses all runtime state (user input, scroll position, etc.)
     */
    FULL_RELOAD {
        @Override
        public void apply(HotReloadable component) {
            component.reload();
            logger.log(Level.FINE, "Full reload completed for: {0}",
                    component.getClass().getSimpleName());
        }
    },

    /**
     * Hot-swaps stylesheets while preserving view state.
     *
     * <p>Used for CSS and BSS files. This strategy:
     * <ol>
     *   <li>Obtains the root node via {@link HotReloadable#getRootForStyleRefresh()}</li>
     *   <li>Removes and re-adds all stylesheets to force a refresh</li>
     *   <li>Recursively refreshes child nodes</li>
     * </ol>
     *
     * <p>Advantages over full reload:
     * <ul>
     *   <li>Preserves user input and view state</li>
     *   <li>Faster than full FXML parsing</li>
     *   <li>Better development experience for style iteration</li>
     * </ul>
     *
     * <p>Falls back to {@link #FULL_RELOAD} if the root node is not available.
     */
    STYLESHEET_RELOAD {
        @Override
        public void apply(HotReloadable component) {
            try {
                Parent root = component.getRootForStyleRefresh();
                if (root != null) {
                    refreshStylesheets(root);
                    logger.log(Level.FINE, "Stylesheet reload completed for: {0}",
                            component.getClass().getSimpleName());
                } else {
                    // Fall back to full reload if root not available
                    FULL_RELOAD.apply(component);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING,
                        "Stylesheet reload failed, falling back to full reload: {0}",
                        e.getMessage());
                FULL_RELOAD.apply(component);
            }
        }

        private void refreshStylesheets(Parent root) {
            // Create a copy of stylesheets to avoid concurrent modification
            var stylesheets = new java.util.ArrayList<>(root.getStylesheets());
            if (!stylesheets.isEmpty()) {
                root.getStylesheets().clear();
                root.getStylesheets().addAll(stylesheets);
            }

            // Recursively refresh children
            for (var child : root.getChildrenUnmodifiable()) {
                if (child instanceof Parent childParent) {
                    refreshStylesheets(childParent);
                }
            }
        }
    },

    /**
     * Ignores the file change - no action taken.
     *
     * <p>Used for files that don't require or support hot reload:
     * <ul>
     *   <li>.java source files (require recompilation)</li>
     *   <li>.class bytecode files (require classloader replacement)</li>
     *   <li>Build artifacts and temporary files</li>
     * </ul>
     */
    IGNORE {
        @Override
        public void apply(HotReloadable component) {
            // No action
        }
    };

    private static final Logger logger = Logger.getLogger(ReloadStrategy.class.getName());

    /**
     * Whether CSS/BSS hot reload is enabled.
     * Default is true. Set to false if using CSSFX or another CSS hot reload solution.
     */
    private static volatile boolean cssReloadEnabled = true;

    /**
     * Applies this reload strategy to the given component.
     *
     * <p>Must be called from the JavaFX Application Thread.
     *
     * @param component the component to reload
     */
    public abstract void apply(HotReloadable component);

    /**
     * Enables or disables CSS/BSS hot reload.
     *
     * <p>When disabled, CSS/BSS file changes will be ignored by FxmlKit.
     * This is useful when using an external CSS hot reload solution like CSSFX.
     *
     * <p>Example:
     * <pre>{@code
     * // Disable FxmlKit CSS reload, use CSSFX instead
     * ReloadStrategy.setCssReloadEnabled(false);
     * CSSFX.start();
     * }</pre>
     *
     * @param enabled true to enable CSS hot reload (default), false to disable
     */
    public static void setCssReloadEnabled(boolean enabled) {
        cssReloadEnabled = enabled;
        logger.log(Level.INFO, "CSS hot reload {0}",
                enabled ? "enabled" : "disabled (use CSSFX or similar for CSS hot reload)");
    }

    /**
     * Returns whether CSS/BSS hot reload is enabled.
     *
     * @return true if CSS hot reload is enabled
     */
    public static boolean isCssReloadEnabled() {
        return cssReloadEnabled;
    }

    /**
     * Determines the appropriate reload strategy for a file extension.
     *
     * @param extension the file extension (with or without leading dot)
     * @return the appropriate reload strategy
     */
    public static ReloadStrategy forExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return IGNORE;
        }

        // Normalize extension
        String ext = extension.startsWith(".") ? extension.substring(1) : extension;
        ext = ext.toLowerCase();

        return switch (ext) {
            case "fxml" -> FULL_RELOAD;
            case "properties" -> FULL_RELOAD;
            case "png", "jpg", "jpeg", "gif", "svg", "ico" -> FULL_RELOAD;
            case "css", "bss" -> cssReloadEnabled ? STYLESHEET_RELOAD : IGNORE;
            case "java", "class", "jar" -> IGNORE;
            default -> IGNORE;
        };
    }

    /**
     * Extracts the file extension from a path.
     *
     * @param path the file path
     * @return the extension without leading dot, or empty string if none
     */
    public static String getExtension(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        int lastDot = path.lastIndexOf('.');
        int lastSeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));

        if (lastDot > lastSeparator && lastDot < path.length() - 1) {
            return path.substring(lastDot + 1).toLowerCase();
        }

        return "";
    }
}