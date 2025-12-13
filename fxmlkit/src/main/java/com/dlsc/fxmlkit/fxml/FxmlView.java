package com.dlsc.fxmlkit.fxml;

import com.dlsc.fxmlkit.core.DiAdapter;
import com.dlsc.fxmlkit.fxml.internal.FxmlKitLoader;
import com.dlsc.fxmlkit.fxml.internal.FxmlPathResolver;
import com.dlsc.fxmlkit.hotreload.HotReloadManager;
import com.dlsc.fxmlkit.hotreload.HotReloadable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for FXML-backed views that extend JavaFX nodes directly.
 *
 * <p>This class wraps FXML content in a StackPane, allowing views to be used as
 * direct JavaFX nodes while supporting the full three-tier DI model. When hot reload
 * is enabled, views automatically register for file change monitoring.
 *
 * <p>Three-tier usage:
 *
 * <p>Tier 1 (Zero-Config):
 * <pre>{@code
 * public class MainView extends FxmlView<MainController> {}
 *
 * MainView view = new MainView();
 * stage.setScene(new Scene(view));
 * }</pre>
 *
 * <p>Tier 2 (Global DI):
 * <pre>{@code
 * LiteDiAdapter di = new LiteDiAdapter();
 * di.bindInstance(UserService.class, new UserService());
 * FxmlKit.setDiAdapter(di);
 *
 * MainView view = new MainView();
 * }</pre>
 *
 * <p>Tier 3 (Isolated DI):
 * <pre>{@code
 * public class MainView extends FxmlView<MainController> {
 *     @Inject
 *     public MainView(DiAdapter diAdapter) {
 *         super(diAdapter);
 *     }
 * }
 *
 * Injector injector = Guice.createInjector(new UserModule(user));
 * MainView view = injector.getInstance(MainView.class);
 * }</pre>
 *
 * <p>Key difference from FxmlViewProvider: FxmlView IS-A JavaFX node (extends StackPane),
 * while FxmlViewProvider HAS-A JavaFX node (returns Parent).
 *
 * <p>Stylesheets are automatically attached for the entire FXML hierarchy (main FXML and
 * all nested fx:include files). Searches for .bss and .css in same directory as each FXML,
 * skipping if already declared in FXML.
 *
 * <h2>Controller Property</h2>
 * <p>The controller is exposed as a read-only property, enabling reactive updates
 * when the view is reloaded (e.g., during hot reload):
 * <pre>{@code
 * MainView view = new MainView();
 *
 * // Subscribe to controller changes (useful for hot reload)
 * view.controllerProperty().subscribe(controller -> {
 *     if (controller != null) {
 *         System.out.println("Controller loaded: " + controller);
 *     }
 * });
 *
 * // Or use traditional listener
 * view.controllerProperty().addListener((obs, oldController, newController) -> {
 *     // React to controller change
 * });
 * }</pre>
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
     * <p>Used for Tier 1 when no DiAdapter is configured (zero-config mode),
     * or Tier 2 when a global DiAdapter is set via {@code FxmlKit.setDiAdapter()}.
     *
     * <p>The FXML is loaded immediately during construction.
     */
    protected FxmlView() {
        this(FxmlKit.getDiAdapter(), null);
    }

    /**
     * Constructs the view with a resource bundle using global DiAdapter (Tier 1/2).
     *
     * @param resources the resource bundle for i18n (may be null)
     */
    protected FxmlView(ResourceBundle resources) {
        this(FxmlKit.getDiAdapter(), resources);
    }

    /**
     * Constructs the view with a specific DiAdapter (Tier 3).
     *
     * <p>Used for Tier 3 (isolated DI) scenarios where each user session has its own
     * DiAdapter instance.
     *
     * @param diAdapter the DiAdapter for dependency injection (may be null for zero-config)
     */
    protected FxmlView(DiAdapter diAdapter) {
        this(diAdapter, null);
    }

    /**
     * Constructs the view with a specific DiAdapter and resource bundle (Tier 3).
     *
     * @param diAdapter the DiAdapter for dependency injection (may be null for zero-config)
     * @param resources the resource bundle for i18n (may be null)
     */
    protected FxmlView(DiAdapter diAdapter, ResourceBundle resources) {
        this.diAdapter = diAdapter;
        this.resources = resources;
        this.setPickOnBounds(false);

        // Load FXML immediately - no delayed loading
        loadFxml();
    }

    /**
     * Controller property - updated on each load/reload.
     */
    private final ReadOnlyObjectWrapper<C> controller = new ReadOnlyObjectWrapper<>(this, "controller");

    /**
     * Returns the controller property.
     *
     * <p>This property is updated each time the view is loaded or reloaded.
     * Use this to react to controller changes during hot reload:
     * <pre>{@code
     * view.controllerProperty().subscribe(controller -> {
     *     // Called on initial load and each reload
     * });
     * }</pre>
     *
     * @return the read-only controller property
     */
    public ReadOnlyObjectProperty<C> controllerProperty() {
        return controller.getReadOnlyProperty();
    }

    /**
     * Gets the current controller instance.
     *
     * <p>Returns the controller specified in the FXML's {@code fx:controller} attribute,
     * or null if no controller is declared.
     *
     * @return the controller instance, or null if FXML has no controller
     */
    public C getController() {
        return controller.get();
    }

    /**
     * Gets the root node loaded from FXML.
     *
     * @return the loaded root node
     */
    public Parent getLoadedRoot() {
        return loadedRoot;
    }

    @Override
    public String getFxmlResourcePath() {
        if (cachedResourcePath == null) {
            URL url = getFxmlUrl();
            cachedResourcePath = FxmlPathResolver.urlToResourcePath(url);
        }
        return cachedResourcePath;
    }

    @Override
    public URL getFxmlUrl() {
        if (cachedFxmlUrl == null) {
            cachedFxmlUrl = FxmlPathResolver.resolveFxmlUrl(getClass());
        }
        return cachedFxmlUrl;
    }

    /**
     * Reloads the view, discarding the cached FXML and controller.
     *
     * <p>Must be called from JavaFX Application Thread.
     *
     * <p>After reload, the {@link #controllerProperty()} will be updated with
     * the new controller instance, triggering any registered listeners.
     */
    @Override
    public void reload() {
        reload(null);
    }

    /**
     * Reloads the view with a new resource bundle.
     *
     * <p>The controller property will be updated directly with the new controller
     * instance (no intermediate null value), so listeners will receive exactly
     * one notification per reload.
     *
     * @param resources the new resource bundle, or null to keep current
     */
    public void reload(ResourceBundle resources) {
        logger.log(Level.FINE, "Reloading FxmlView: {0}", getClass().getSimpleName());

        this.getChildren().clear();
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
            C loadedController;

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
                loadedController = result.getController();

            } else {
                // Tier 1: Zero-config mode
                logger.log(Level.FINE, "Loading without DI (zero-config mode) for {0}",
                        getClass().getSimpleName());

                FXMLLoader loader = FxmlKitLoader.createBasicLoader(fxmlUrl, getClass(), resources);

                this.loadedRoot = loader.load();
                loadedController = (C) loader.getController();

                // Auto-attach stylesheets (including nested FXMLs)
                if (FxmlKit.isAutoAttachStyles()) {
                    FxmlPathResolver.autoAttachStylesheets(loadedRoot, fxmlUrl);
                }
            }

            // Update controller property - triggers listeners
            this.controller.set(loadedController);

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