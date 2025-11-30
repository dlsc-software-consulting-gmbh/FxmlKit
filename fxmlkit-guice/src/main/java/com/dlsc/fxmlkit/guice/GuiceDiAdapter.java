package com.dlsc.fxmlkit.guice;

import com.dlsc.fxmlkit.core.BaseDiAdapter;
import com.google.inject.Injector;

import java.util.Objects;

/**
 * Guice-based DiAdapter implementation.
 *
 * <p>This adapter integrates Google Guice with FxmlKit, providing full dependency
 * injection support for all three tiers.
 *
 * <h2>How It Works</h2>
 * <p>Guice performs full injection (constructor + fields + methods) in a single
 * {@code getInstance()} call. The {@code injectMembers()} method is used for
 * objects created externally (e.g., FXML nodes marked with @FxmlObject).
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Tier 2: Desktop Mode (Global DI)</h3>
 * <pre>{@code
 * Injector injector = Guice.createInjector(new AbstractModule() {
 *     @Override
 *     protected void configure() {
 *         bind(UserService.class).toInstance(new UserService());
 *         bind(ConfigService.class).to(ConfigServiceImpl.class);
 *     }
 * });
 *
 * GuiceDiAdapter adapter = new GuiceDiAdapter(injector);
 * FxmlKit.setDiAdapter(adapter);
 *
 * // Then use normally
 * MainView view = new MainView();  // Uses global adapter
 * Parent root = view.getView();
 * }</pre>
 *
 * <h3>Tier 3: JPro Multi-User Mode (Isolated DI)</h3>
 * <pre>{@code
 * // Each user gets their own Injector
 * Injector injector = Guice.createInjector(new AbstractModule() {
 *     @Override
 *     protected void configure() {
 *         bind(User.class).toInstance(currentUser);
 *         bind(UserService.class).toProvider(() -> new UserService(currentUser));
 *     }
 *
 *     @Provides
 *     @Singleton
 *     DiAdapter provideDiAdapter(Injector injector) {
 *         return new GuiceDiAdapter(injector);
 *     }
 * });
 *
 * // User view with constructor injection
 * public class MainView extends FxmlView<MainController> {
 *     @Inject
 *     public MainView(DiAdapter diAdapter) {
 *         super(diAdapter);
 *     }
 * }
 *
 * // Create view via Guice
 * MainView view = injector.getInstance(MainView.class);
 * Parent root = view;  // Ready to use
 * }</pre>
 *
 * <h2>Critical: @Provides DiAdapter Binding for Tier 3</h2>
 * <p>For Tier 3, you MUST provide a DiAdapter binding via @Provides. Without it,
 * Guice won't know how to inject DiAdapter into FxmlView subclasses.
 *
 * <pre>{@code
 * @Provides
 * @Singleton
 * DiAdapter provideDiAdapter(Injector injector) {
 *     return new GuiceDiAdapter(injector);
 * }
 * }</pre>
 *
 * @see BaseDiAdapter
 */
public class GuiceDiAdapter extends BaseDiAdapter {

    private final Injector injector;

    /**
     * Creates a new GuiceDiAdapter.
     *
     * @param injector the Guice injector (must not be null)
     * @throws NullPointerException if injector is null
     */
    public GuiceDiAdapter(Injector injector) {
        this.injector = Objects.requireNonNull(injector, "Injector cannot be null");
    }

    /**
     * Creates an instance using Guice.
     *
     * <p>Guice performs full injection (constructor + fields + methods) in one call.
     *
     * @param <T>  the type parameter
     * @param type the class to instantiate
     * @return the fully-injected instance
     */
    @Override
    protected <T> T doGetInstance(Class<T> type) {
        return injector.getInstance(type);
    }

    /**
     * Injects members using Guice.
     *
     * <p>Guice's injectMembers() is idempotent and safe to call multiple times.
     * This is used for FXML-created objects marked with @FxmlObject.
     *
     * @param target the object to inject (never null)
     */
    @Override
    protected void doInjectMembers(Object target) {
        injector.injectMembers(target);
    }

    /**
     * Gets the underlying Guice Injector.
     *
     * @return the Guice Injector instance
     */
    public Injector getInjector() {
        return injector;
    }

    @Override
    public String toString() {
        return "GuiceDiAdapter{injector=" + injector + "}";
    }
}