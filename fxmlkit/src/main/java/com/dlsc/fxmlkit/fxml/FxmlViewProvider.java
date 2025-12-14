package com.dlsc.fxmlkit.fxml;

import com.dlsc.fxmlkit.di.DiAdapter;
import com.dlsc.fxmlkit.fxml.internal.FxmlKitLoader;
import com.dlsc.fxmlkit.fxml.internal.FxmlPathResolver;
import com.dlsc.fxmlkit.hotreload.HotReloadManager;
import com.dlsc.fxmlkit.hotreload.HotReloadable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for FXML-backed views with progressive DI support.
 *
 * <p>This class provides a HAS-A relationship with the FXML view (returns Parent via getView()),
 * as opposed to FxmlView which IS-A JavaFX node. When hot reload is enabled, providers
 * automatically register for file change monitoring.
 *
 * <p>Three-tier usage:
 *
 * <p>Tier 1 (Zero-Config):
 * <pre>{@code
 * public class MainViewProvider extends FxmlViewProvider<MainController> {}
 *
 * MainViewProvider provider = new MainViewProvider();
 * stage.setScene(new Scene(provider.getView()));
 * }</pre>
 *
 * <p>Tier 2 (Global DI):
 * <pre>{@code
 * LiteDiAdapter di = new LiteDiAdapter();
 * di.bindInstance(UserService.class, new UserService());
 * FxmlKit.setDiAdapter(di);
 *
 * MainViewProvider provider = new MainViewProvider();
 * Parent root = provider.getView();
 * }</pre>
 *
 * <p>Tier 3 (Isolated DI):
 * <pre>{@code
 * public class MainViewProvider extends FxmlViewProvider<MainController> {
 *     @Inject
 *     public MainViewProvider(DiAdapter diAdapter) {
 *         super(diAdapter);
 *     }
 * }
 *
 * Injector injector = Guice.createInjector(new UserModule(user));
 * MainViewProvider provider = injector.getInstance(MainViewProvider.class);
 * }</pre>
 *
 * <p>Tier 1 provides minimal FXML loading without DI. Controllers are created via no-arg
 * constructor with no field/method injection, @PostInject, or @FxmlObject processing.
 *
 * <p>Tier 2 adds field/method injection, @PostInject callbacks, and @FxmlObject node
 * processing (policy-dependent) using a single global DiAdapter.
 *
 * <p>Tier 3 provides per-instance DiAdapter for isolated dependency contexts, typically
 * used in multi-user scenarios where each user session has its own dependency graph.
 *
 * <p>This class has no dependency on any DI framework or annotations. Works with any
 * framework that provides a {@link DiAdapter}: Guice, Spring, Jakarta CDI, or the
 * built-in LiteDiAdapter.
 *
 * <p>Stylesheets are automatically attached for the entire FXML hierarchy (main FXML and
 * all nested fx:include files). Searches for .bss and .css in same directory as each FXML,
 * skipping if already declared in FXML.
 *
 * <h2>View and Controller Properties</h2>
 * <p>Both view and controller are exposed as read-only properties, enabling reactive updates
 * when the view is reloaded (e.g., during hot reload):
 * <pre>{@code
 * MainViewProvider provider = new MainViewProvider();
 *
 * // Subscribe to view changes - auto-update container on hot reload
 * provider.viewProperty().subscribe(view -> {
 *     if (view != null) {
 *         container.getChildren().setAll(view);
 *     }
 * });
 *
 * // Subscribe to controller changes
 * provider.controllerProperty().subscribe(controller -> {
 *     if (controller != null) {
 *         controller.refreshData();
 *     }
 * });
 *
 * // Trigger initial load
 * provider.getView();
 * }</pre>
 *
 * @param <T> the controller type
 */
public abstract class FxmlViewProvider<T> implements HotReloadable {

    private static final Logger logger = Logger.getLogger(FxmlViewProvider.class.getName());

    /**
     * The DiAdapter used for dependency injection.
     * May be null in Tier 1 (zero-config mode).
     */
    private final DiAdapter diAdapter;

    /**
     * Whether the view has been loaded (for lazy loading control).
     */
    private boolean loaded = false;

    /**
     * Optional resource bundle for internationalization.
     */
    private ResourceBundle resources;

    /**
     * Whether this provider has been registered for hot reload.
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
     * Constructs the provider using global DiAdapter (Tier 1/2).
     *
     * <p>Used for Tier 1 when no DiAdapter is configured (zero-config mode),
     * or Tier 2 when a global DiAdapter is set via {@code FxmlKit.setDiAdapter()}.
     */
    protected FxmlViewProvider() {
        this(FxmlKit.getDiAdapter(), null);
    }

    /**
     * Constructs the provider with a resource bundle using global DiAdapter (Tier 1/2).
     *
     * @param resources the resource bundle for i18n (may be null)
     */
    protected FxmlViewProvider(ResourceBundle resources) {
        this(FxmlKit.getDiAdapter(), resources);
    }

    /**
     * Constructs the provider with a specific DiAdapter (Tier 3).
     *
     * <p>Used for Tier 3 (isolated DI) scenarios where each user session has its own
     * DiAdapter instance.
     *
     * @param diAdapter the DiAdapter for dependency injection (may be null for zero-config)
     */
    protected FxmlViewProvider(DiAdapter diAdapter) {
        this(diAdapter, null);
    }

    /**
     * Constructs the provider with a specific DiAdapter and resource bundle (Tier 3).
     *
     * @param diAdapter the DiAdapter for dependency injection (may be null for zero-config)
     * @param resources the resource bundle for i18n (may be null)
     */
    protected FxmlViewProvider(DiAdapter diAdapter, ResourceBundle resources) {
        this.diAdapter = diAdapter;
        this.resources = resources;
    }

    /**
     * View property - updated on each load/reload.
     */
    private final ReadOnlyObjectWrapper<Parent> view = new ReadOnlyObjectWrapper<>(this, "view");

    /**
     * Returns the view property.
     *
     * <p>This property is updated each time the view is loaded or reloaded.
     * Use this to react to view changes during hot reload, enabling automatic
     * UI updates:
     * <pre>{@code
     * provider.viewProperty().subscribe(view -> {
     *     if (view != null) {
     *         container.getChildren().setAll(view);
     *     }
     * });
     * }</pre>
     *
     * <p><b>Note:</b> The property value is null until {@link #getView()} is called
     * for the first time (lazy loading).
     *
     * @return the read-only view property
     */
    public ReadOnlyObjectProperty<Parent> viewProperty() {
        return view.getReadOnlyProperty();
    }

    /**
     * Lazily loads and returns the root node.
     *
     * <p>The FXML is loaded on first access and cached for subsequent calls.
     * After loading, the {@link #viewProperty()} will be updated, triggering
     * any registered listeners.
     *
     * @return the root node (never null after successful load)
     * @throws RuntimeException if FXML loading fails
     */
    public Parent getView() {
        if (!loaded) {
            load();
        }
        return view.get();
    }

    /**
     * Triggers FXML loading.
     *
     * <p>This is a convenience method equivalent to calling {@link #getView()}
     * when you don't need the returned view instance.
     *
     * <p><b>Typical use case:</b> When binding to {@link #viewProperty()},
     * use this method to trigger the initial load:
     * <pre>{@code
     * WelcomeViewProvider provider = new WelcomeViewProvider();
     * root.centerProperty().bind(provider.viewProperty());
     * provider.loadView();  // Trigger initial load
     * }</pre>
     *
     * <p><b>Note:</b> If you need the view instance directly, use {@link #getView()} instead.
     */
    public void loadView() {
        getView();
    }

    /**
     * Controller property - updated on each load/reload.
     */
    private final ReadOnlyObjectWrapper<T> controller = new ReadOnlyObjectWrapper<>(this, "controller");

    /**
     * Returns the controller property.
     *
     * <p>This property is updated each time the view is loaded or reloaded.
     * Use this to react to controller changes during hot reload:
     * <pre>{@code
     * provider.controllerProperty().subscribe(controller -> {
     *     if (controller != null) {
     *         controller.refreshData();
     *     }
     * });
     * }</pre>
     *
     * <p><b>Note:</b> The property value is null until {@link #getView()} is called
     * for the first time (lazy loading).
     *
     * @return the read-only controller property
     */
    public ReadOnlyObjectProperty<T> controllerProperty() {
        return controller.getReadOnlyProperty();
    }

    /**
     * Gets the current controller instance.
     *
     * <p>Returns the controller specified in the FXML's {@code fx:controller} attribute,
     * or null if no controller is declared or if the view has not been loaded yet.
     *
     * <p><b>Note:</b> This method does NOT trigger lazy loading.
     * Call {@link #getView()} first if you need to ensure the view is loaded.
     *
     * @return the controller instance, or null if not loaded or FXML has no controller
     */
    public T getController() {
        return controller.get();
    }

    /**
     * Checks if the view has been loaded.
     *
     * @return true if the view has been loaded
     */
    public boolean isLoaded() {
        return loaded;
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
     * Returns the loaded view for stylesheet refresh operations.
     *
     * @return the loaded view, or null if not yet loaded
     */
    @Override
    public Parent getRootForStyleRefresh() {
        return view.get();
    }

    /**
     * Reloads the view, discarding the cached instance.
     *
     * <p>After reload, both {@link #viewProperty()} and {@link #controllerProperty()}
     * will be updated with new instances, triggering any registered listeners.
     */
    @Override
    public void reload() {
        reload(null);
    }

    /**
     * Reloads the view with a new resource bundle.
     *
     * <p>The view and controller properties will be updated directly with new instances
     * (no intermediate null values), so listeners will receive exactly one notification
     * per reload for each property.
     *
     * @param resources the new resource bundle, or null to keep current
     */
    public void reload(ResourceBundle resources) {
        logger.log(Level.FINE, "Reloading FxmlViewProvider: {0}", getClass().getSimpleName());

        this.loaded = false;

        if (resources != null) {
            this.resources = resources;
        }

        load();
    }

    /**
     * Returns the DiAdapter used by this provider.
     *
     * @return the DiAdapter, or null if in zero-config mode
     */
    protected DiAdapter getDiAdapter() {
        return diAdapter;
    }

    /**
     * Loads FXML and wires DI based on the configured adapter.
     */
    private void load() {
        // Prevent re-entry (e.g., if beforeLoad() triggers another getView())
        if (loaded) {
            return;
        }
        loaded = true;

        logger.log(Level.FINE, "Loading view for: {0}", getClass().getName());

        try {
            // Hook for subclasses
            beforeLoad();

            Parent loadedView;
            T loadedController;

            if (diAdapter == null) {
                // Tier 1: Zero-configuration mode
                logger.log(Level.FINE, "Tier 1: Loading without DI (zero-config mode) for {0}",
                        getClass().getSimpleName());
                LoadResult<T> result = loadWithoutDI();
                loadedView = result.view;
                loadedController = result.controller;
            } else {
                // Tier 2/3: DI-enabled mode
                logger.log(Level.FINE, "Tier 2/3: Loading with DI ({0}) for {1}",
                        new Object[]{diAdapter.getClass().getSimpleName(), getClass().getSimpleName()});
                FxmlKitLoader.LoadResult<T> result = loadWithDI();
                loadedView = result.getView();
                loadedController = result.getController();
            }

            // Update properties - triggers listeners
            this.view.set(loadedView);
            this.controller.set(loadedController);

            logger.log(Level.FINE, "View loaded successfully for: {0}", getClass().getName());

            // Register for hot reload after successful load
            registerForHotReload();

            // Hook for subclasses
            afterLoad();

        } catch (Exception e) {
            // Reset loaded flag to allow retry after fixing the error
            loaded = false;
            throw e;
        }
    }

    /**
     * Internal result holder for Tier 1 loading.
     */
    private static class LoadResult<T> {
        final Parent view;
        final T controller;

        LoadResult(Parent view, T controller) {
            this.view = view;
            this.controller = controller;
        }
    }

    /**
     * Tier 1: Load FXML without dependency injection.
     *
     * @return the load result containing view and controller
     */
    @SuppressWarnings("unchecked")
    private LoadResult<T> loadWithoutDI() {
        try {
            URL url = getFxmlUrl();

            FXMLLoader loader = FxmlKitLoader.createBasicLoader(url, getClass(), resources);

            Parent root = loader.load();

            // Auto-attach stylesheets (including nested FXMLs)
            if (FxmlKit.isAutoAttachStyles()) {
                FxmlPathResolver.autoAttachStylesheets(root, url);
            }

            return new LoadResult<>(root, (T) loader.getController());

        } catch (Exception e) {
            throw new RuntimeException("Failed to load FXML for " + getClass().getName(), e);
        }
    }

    /**
     * Tier 2/3: Load FXML with full dependency injection support.
     *
     * @return the load result containing view and controller
     */
    private FxmlKitLoader.LoadResult<T> loadWithDI() {
        return FxmlKitLoader.loadWithController(
                diAdapter,
                getClass(),
                resources
        );
    }

    /**
     * Registers this provider for hot reload if enabled.
     */
    private void registerForHotReload() {
        if (!registeredForHotReload && HotReloadManager.getInstance().isEnabled()) {
            HotReloadManager.getInstance().register(this);
            registeredForHotReload = true;
            logger.log(Level.FINE, "Registered for hot reload: {0}", getClass().getSimpleName());
        }
    }

    /**
     * Convenience method to create and load a view using reflection (Tier 1/2 only).
     *
     * <p>For Tier 3, prefer the DI framework ({@code injector.getInstance(...)}).
     *
     * @param viewProviderClass the view provider class
     * @return the loaded view
     */
    public static Parent of(Class<? extends FxmlViewProvider<?>> viewProviderClass) {
        try {
            FxmlViewProvider<?> provider = viewProviderClass.getDeclaredConstructor().newInstance();
            return provider.getView();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create view provider: " + viewProviderClass.getName(), e);
        }
    }

    /**
     * Like {@link #of(Class)} but also returns the controller if present.
     *
     * <p>Suitable for Tier 1/2. In Tier 3, use the DI framework instead.
     *
     * @param <T>               the controller type
     * @param viewProviderClass the view provider class
     * @return a ViewResult containing both view and controller
     */
    public static <T> ViewResult<T> withController(Class<? extends FxmlViewProvider<T>> viewProviderClass) {
        try {
            FxmlViewProvider<T> provider = viewProviderClass.getDeclaredConstructor().newInstance();
            Parent view = provider.getView();
            T controller = provider.getController();
            return new ViewResult<>(view, controller);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create view provider: " + viewProviderClass.getName(), e);
        }
    }

    /**
     * Holder for a loaded view and its controller.
     *
     * @param <T> the controller type
     */
    public static class ViewResult<T> {
        // Instead of using a record, we use a regular class for compatibility with older versions.

        /**
         * The root node of the view (never null)
         */
        private final Parent view;

        /**
         * The controller instance (maybe null if FXML has no controller)
         */
        private final T controller;

        public ViewResult(Parent view, T controller) {
            this.view = view;
            this.controller = controller;
        }

        public Parent getView() {
            return view;
        }

        public T getController() {
            return controller;
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