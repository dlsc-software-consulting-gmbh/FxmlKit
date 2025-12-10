package com.dlsc.fxmlkit.fxml;

import com.dlsc.fxmlkit.core.DiAdapter;
import com.dlsc.fxmlkit.hotreload.HotReloadManager;
import com.dlsc.fxmlkit.hotreload.HotReloadable;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.net.URL;
import java.util.Optional;
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
     * Cached root node of the loaded view.
     */
    private Parent view;

    /**
     * Cached controller instance (may be null if FXML has no controller).
     */
    private T controller;

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
     * Lazily loads and returns the root node.
     *
     * <p>The FXML is loaded on first access and cached for subsequent calls.
     *
     * @return the root node (never null)
     * @throws RuntimeException if FXML loading fails
     */
    public Parent getView() {
        if (view == null) {
            load();
        }
        return view;
    }

    /**
     * Gets the controller as an Optional.
     *
     * @return Optional containing the controller, or empty if FXML has no controller
     */
    public Optional<T> getController() {
        if (view == null) {
            getView();
        }
        return Optional.ofNullable(controller);
    }

    /**
     * Gets the controller, throwing if not present.
     *
     * @return the controller (never null)
     * @throws IllegalStateException if FXML declares no controller
     */
    public T getRequiredController() {
        return getController().orElseThrow(() ->
                new IllegalStateException("No controller found in FXML for " + getClass().getName()));
    }

    /**
     * Checks if the view has been loaded.
     *
     * @return true if the view has been loaded
     */
    public boolean isLoaded() {
        return view != null;
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
        return view;
    }

    /**
     * Reloads the view, discarding the cached instance.
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
        logger.log(Level.FINE, "Reloading FxmlViewProvider: {0}", getClass().getSimpleName());

        this.view = null;
        this.controller = null;

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
    @SuppressWarnings("unchecked")
    private void load() {
        logger.log(Level.FINE, "Loading view for: {0}", getClass().getName());

        // Hook for subclasses
        beforeLoad();

        if (diAdapter == null) {
            // Tier 1: Zero-configuration mode
            logger.log(Level.FINE, "Tier 1: Loading without DI (zero-config mode) for {0}",
                    getClass().getSimpleName());
            loadWithoutDI();
        } else {
            // Tier 2/3: DI-enabled mode
            logger.log(Level.FINE, "Tier 2/3: Loading with DI ({0}) for {1}",
                    new Object[]{diAdapter.getClass().getSimpleName(), getClass().getSimpleName()});
            loadWithDI();
        }

        logger.log(Level.FINE, "View loaded successfully for: {0}", getClass().getName());

        // Register for hot reload after successful load
        registerForHotReload();

        // Hook for subclasses
        afterLoad();
    }

    /**
     * Tier 1: Load FXML without dependency injection.
     */
    @SuppressWarnings("unchecked")
    private void loadWithoutDI() {
        try {
            URL url = getFxmlUrl();

            FXMLLoader loader = FxmlKitLoader.createBasicLoader(url, getClass(), resources);

            Parent root = loader.load();

            // Auto-attach stylesheets (including nested FXMLs)
            if (FxmlKit.isAutoAttachStyles()) {
                FxmlPathResolver.autoAttachStylesheets(root, url);
            }

            this.view = root;
            this.controller = (T) loader.getController();

        } catch (Exception e) {
            throw new RuntimeException("Failed to load FXML for " + getClass().getName(), e);
        }
    }

    /**
     * Tier 2/3: Load FXML with full dependency injection support.
     */
    private void loadWithDI() {
        FxmlKitLoader.LoadResult<T> result = FxmlKitLoader.loadWithController(
                diAdapter,
                getClass(),
                resources
        );

        this.view = result.getView();
        this.controller = result.getController();
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
            T controller = provider.getController().orElse(null);
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