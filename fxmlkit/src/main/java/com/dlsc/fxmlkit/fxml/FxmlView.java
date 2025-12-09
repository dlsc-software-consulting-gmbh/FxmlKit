package com.dlsc.fxmlkit.fxml;

import com.dlsc.fxmlkit.core.DiAdapter;
import com.dlsc.fxmlkit.hotreload.HotReloadManager;
import com.dlsc.fxmlkit.hotreload.HotReloadable;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for FXML-backed views that extend JavaFX nodes directly.
 *
 * <p>This class wraps FXML content in a StackPane, allowing views to be used
 * as direct JavaFX nodes while supporting the full three-tier DI model.
 *
 * <h2>Hot Reload Support</h2>
 * <p>When hot reload is enabled via {@code FxmlKit.enableDevelopmentMode()}, views
 * automatically register themselves for file change monitoring. Changes to the
 * FXML or CSS files will automatically trigger a view reload or stylesheet refresh.
 *
 * <pre>{@code
 * // Enable hot reload at startup
 * FxmlKit.enableDevelopmentMode();
 *
 * // Or fine-grained control
 * FxmlKit.setFxmlHotReloadEnabled(true);
 * FxmlKit.setCssHotReloadEnabled(true);
 * }</pre>
 *
 * <h2>Three-Tier Support</h2>
 *
 * <h3>Tier 1 - Zero Configuration (No DI)</h3>
 * <pre>{@code
 * public class MainView extends FxmlView<MainController> {}
 *
 * // No setup required
 * MainView view = new MainView();
 * stage.setScene(new Scene(view));  // Ready to use immediately
 * }</pre>
 *
 * <h3>Tier 2 - Global DI (Desktop)</h3>
 * <pre>{@code
 * // One-time setup at application startup
 * LiteDiAdapter di = new LiteDiAdapter();
 * di.bindInstance(UserService.class, new UserService());
 * FxmlKit.setDiAdapter(di);
 *
 * // Then use normally
 * MainView view = new MainView();
 * stage.setScene(new Scene(view));  // Ready to use immediately
 * }</pre>
 *
 * <h3>Tier 3 - Isolated DI (JPro Multi-User)</h3>
 * <pre>{@code
 * // User-specific view class with constructor injection
 * public class MainView extends FxmlView<MainController> {
 *     @Inject
 *     public MainView(DiAdapter diAdapter) {
 *         super(diAdapter);
 *     }
 * }
 *
 * // Create via DI framework
 * Injector injector = Guice.createInjector(new UserModule(user));
 * MainView view = injector.getInstance(MainView.class);
 * stage.setScene(new Scene(view));  // Ready to use immediately
 * }</pre>
 *
 * <h2>Key Differences from FxmlViewProvider</h2>
 * <ul>
 *   <li>FxmlView IS-A JavaFX node (extends StackPane)</li>
 *   <li>FxmlViewProvider HAS-A JavaFX node (returns Parent)</li>
 *   <li>FxmlView can be used directly as a Scene root</li>
 * </ul>
 *
 * <h2>Automatic Stylesheet Attachment</h2>
 * <p>Stylesheets are automatically attached for the entire FXML hierarchy:
 * <ul>
 *   <li>Main FXML and all nested {@code <fx:include>} files</li>
 *   <li>Searches for .bss (binary) and .css (text) in same directory as each FXML</li>
 *   <li>Optimized: Skips if stylesheet already declared in FXML</li>
 * </ul>
 *
 * @param <C> the controller type
 */
public abstract class FxmlView<C> extends StackPane implements HotReloadable {

    private static final Logger logger = Logger.getLogger(FxmlView.class.getName());

    /**
     * The DiAdapter used for dependency injection.
     * Maybe null in Tier 1 (zero-config mode).
     */
    private final DiAdapter diAdapter;

    /**
     * Cached controller instance.
     */
    private C controller;

    /**
     * Cached root node loaded from FXML.
     */
    private Parent loadedRoot;

    /**
     * Optional resource bundle for internationalization.
     */
    private ResourceBundle resources;

    /**
     * Whether this view has been registered for hot reload.
     */
    private boolean registeredForHotReload = false;

    /**
     * Cached FXML URL for hot reload.
     */
    private URL cachedFxmlUrl;

    /**
     * Cached resource path for hot reload.
     */
    private String cachedResourcePath;

    /**
     * Constructs the view using global DiAdapter (Tier 1/2).
     *
     * <p>This constructor is used for:
     * <ul>
     *   <li><b>Tier 1:</b> When no DiAdapter is configured (zero-config mode)</li>
     *   <li><b>Tier 2:</b> When a global DiAdapter is set via {@code FxmlKit.setDiAdapter()}</li>
     * </ul>
     *
     * <p>The FXML is loaded immediately during construction.
     */
    protected FxmlView() {
        this(FxmlKit.getDiAdapter(), null);
    }

    /**
     * Constructs the view with a resource bundle using global DiAdapter (Tier 1/2).
     *
     * @param resources the resource bundle for i18n (maybe null)
     */
    protected FxmlView(ResourceBundle resources) {
        this(FxmlKit.getDiAdapter(), resources);
    }

    /**
     * Constructs the view with a specific DiAdapter (Tier 3).
     *
     * <p>This constructor is used for Tier 3 (isolated DI) scenarios where
     * each user session has its own DiAdapter instance.
     *
     * <p>Typical usage with Guice:
     * <pre>{@code
     * public class HomeView extends FxmlView<HomeController> {
     *     @Inject
     *     public HomeView(DiAdapter diAdapter) {
     *         super(diAdapter);
     *     }
     * }
     * }</pre>
     *
     * @param diAdapter the DiAdapter for dependency injection (maybe null for zero-config)
     */
    protected FxmlView(DiAdapter diAdapter) {
        this(diAdapter, null);
    }

    /**
     * Constructs the view with a specific DiAdapter and resource bundle (Tier 3).
     *
     * @param diAdapter the DiAdapter for dependency injection (maybe null for zero-config)
     * @param resources the resource bundle for i18n (maybe null)
     */
    protected FxmlView(DiAdapter diAdapter, ResourceBundle resources) {
        this.diAdapter = diAdapter;
        this.resources = resources;
        this.setPickOnBounds(false);

        // Load FXML immediately - no delayed loading
        loadFxml();
    }

    /**
     * Gets the controller as an Optional.
     *
     * @return Optional containing the controller, or empty if FXML has no controller
     */
    public Optional<C> getController() {
        return Optional.ofNullable(controller);
    }

    /**
     * Gets the controller, throwing if not present.
     *
     * @return the controller (never null)
     * @throws IllegalStateException if FXML declares no controller
     */
    public C getRequiredController() {
        return getController().orElseThrow(() ->
                new IllegalStateException("No controller found in FXML for " + getClass().getName()));
    }

    /**
     * Gets the root node loaded from FXML.
     *
     * @return the loaded root node
     */
    public Parent getLoadedRoot() {
        return loadedRoot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFxmlResourcePath() {
        if (cachedResourcePath == null) {
            URL url = getFxmlUrl();
            cachedResourcePath = FxmlPathResolver.urlToResourcePath(url);
        }
        return cachedResourcePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URL getFxmlUrl() {
        if (cachedFxmlUrl == null) {
            cachedFxmlUrl = FxmlPathResolver.resolveFxmlUrl(getClass());
        }
        return cachedFxmlUrl;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reloads the view, discarding the cached FXML and controller.
     *
     * <p><b>Thread Safety:</b> Must be called from JavaFX Application Thread.
     */
    @Override
    public void reload() {
        reload(null);
    }

    /**
     * Reloads the view with a new resource bundle.
     *
     * @param resources the new resource bundle, or null to keep current
     */
    public void reload(ResourceBundle resources) {
        logger.log(Level.FINE, "Reloading FxmlView: {0}", getClass().getSimpleName());

        this.getChildren().clear();
        this.controller = null;
        this.loadedRoot = null;

        if (resources != null) {
            this.resources = resources;
        }

        loadFxml();
    }

    /**
     * Returns the DiAdapter used by this view.
     *
     * @return the DiAdapter, or null if in zero-config mode
     */
    protected DiAdapter getDiAdapter() {
        return diAdapter;
    }

    /**
     * Loads the FXML and initializes the view.
     */
    @SuppressWarnings("unchecked")
    private void loadFxml() {
        logger.log(Level.FINE, "Loading FXML for FxmlView: {0}", getClass().getName());

        // Hook for subclasses
        beforeLoad();

        URL fxmlUrl = getFxmlUrl();

        try {
            if (diAdapter != null) {
                // Tier 2/3: Use FxmlKitLoader for full DI support
                logger.log(Level.FINE, "Loading with DI ({0}) for {1}",
                        new Object[]{diAdapter.getClass().getSimpleName(), getClass().getSimpleName()});

                FxmlKitLoader.LoadResult<C> result = FxmlKitLoader.loadWithController(
                        diAdapter,
                        getClass(),
                        resources
                );

                this.loadedRoot = result.getView();
                this.controller = result.getController();

            } else {
                // Tier 1: Zero-config mode
                logger.log(Level.FINE, "Loading without DI (zero-config mode) for {0}",
                        getClass().getSimpleName());

                FXMLLoader loader = FxmlKitLoader.createBasicLoader(fxmlUrl, getClass(), resources);

                this.loadedRoot = loader.load();
                this.controller = (C) loader.getController();

                // Auto-attach stylesheets (including nested FXMLs)
                if (FxmlKit.isAutoAttachStyles()) {
                    FxmlPathResolver.autoAttachStylesheets(loadedRoot, fxmlUrl);
                }
            }

            // Wrap the loaded root in this StackPane
            this.getChildren().add(loadedRoot);

            logger.log(Level.FINE, "FxmlView loaded successfully: {0}", getClass().getName());

            // Register for hot reload after successful load
            registerForHotReload();

            // Hook for subclasses
            afterLoad();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load FXML for " + getClass().getName() + " from URL: " + fxmlUrl, e);
        }
    }

    /**
     * Registers this view for hot reload if enabled.
     */
    private void registerForHotReload() {
        if (!registeredForHotReload && HotReloadManager.getInstance().isEnabled()) {
            HotReloadManager.getInstance().register(this);
            registeredForHotReload = true;
            logger.log(Level.FINE, "Registered for hot reload: {0}", getClass().getSimpleName());
        }
    }

    /**
     * Hook: Called before FXML load. Override to customize.
     */
    protected void beforeLoad() {
    }

    /**
     * Hook: Called after FXML load. Override to customize.
     */
    protected void afterLoad() {
    }
}