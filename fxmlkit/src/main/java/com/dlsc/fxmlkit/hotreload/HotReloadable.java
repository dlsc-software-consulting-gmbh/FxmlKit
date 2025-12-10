package com.dlsc.fxmlkit.hotreload;

import javafx.scene.Parent;

import java.net.URL;

/**
 * Interface for components that support hot reload functionality.
 *
 * <p>Components implementing this interface can be automatically reloaded
 * when their associated FXML or CSS files change during development.
 *
 * <p>This interface is implemented by {@code FxmlView} and {@code FxmlViewProvider}
 * to enable seamless hot reload integration.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@link #getFxmlResourcePath()} returns the classpath-relative FXML path</li>
 *   <li>{@link #getFxmlUrl()} returns the resolved URL for dependency analysis</li>
 *   <li>{@link #reload()} performs the actual view reload on the JavaFX thread</li>
 *   <li>{@link #getRootForStyleRefresh()} returns the root node for stylesheet refresh</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>The {@link #reload()} method must only be called from the JavaFX Application Thread.
 * The other methods are thread-safe.
 *
 * @see HotReloadManager
 */
public interface HotReloadable {

    /**
     * Returns the classpath-relative path to the FXML file.
     *
     * <p>This path is used as a key for mapping file system changes to components.
     * The format should be a forward-slash separated path without a leading slash.
     *
     * <p>Example: {@code "com/example/view/Dashboard.fxml"}
     *
     * @return the FXML resource path, never null
     */
    String getFxmlResourcePath();

    /**
     * Returns the URL of the FXML file.
     *
     * <p>This URL is used for dependency analysis (finding fx:include references)
     * and for determining the target/source directory structure.
     *
     * @return the FXML URL, never null
     */
    URL getFxmlUrl();

    /**
     * Reloads this component from its FXML file.
     *
     * <p>This method clears the current view content and re-parses the FXML file.
     * All state (user input, scroll position, etc.) will be lost.
     *
     * <p><b>Thread Safety:</b> Must be called from the JavaFX Application Thread.
     */
    void reload();

    /**
     * Returns the root Parent node for stylesheet refresh operations.
     *
     * <p>This method is used by the CSS hot reload feature to refresh stylesheets
     * without a full view reload, preserving runtime state.
     *
     * <p>Default implementation returns {@code this} if the component is a Parent,
     * otherwise returns null (which triggers a fallback to full reload).
     *
     * <p>Implementations should override this method to return the appropriate
     * root node. For example, {@code FxmlViewProvider} should return {@code getView()}.
     *
     * @return the root Parent for style refresh, or null if not available
     */
    default Parent getRootForStyleRefresh() {
        if (this instanceof Parent) {
            return (Parent) this;
        }
        return null;
    }
}