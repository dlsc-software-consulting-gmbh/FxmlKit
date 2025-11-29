package com.dlsc.fxmlkit.core;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class for DiAdapter implementations.
 *
 * <p>This class provides a template method pattern for framework-specific
 * DI adapter implementations.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Template method pattern for framework-specific implementations</li>
 *   <li>Null-safety checks for getInstance and injectMembers</li>
 *   <li>Framework-neutral design</li>
 * </ul>
 *
 * <h2>Template Method Pattern</h2>
 * <ul>
 *   <li><b>Template method:</b> {@link #getInstance(Class)} - handles null checks</li>
 *   <li><b>Hook method:</b> {@link #doGetInstance(Class)} - framework-specific instance creation</li>
 *   <li><b>Template method:</b> {@link #injectMembers(Object)} - handles null checks</li>
 *   <li><b>Hook method:</b> {@link #doInjectMembers(Object)} - framework-specific member injection</li>
 * </ul>
 *
 * <h2>Implementation Guide</h2>
 * <pre>{@code
 * public class GuiceDiAdapter extends BaseDiAdapter {
 *     private final Injector injector;
 *
 *     public GuiceDiAdapter(Injector injector) {
 *         this.injector = injector;
 *     }
 *
 *     @Override
 *     protected <T> T doGetInstance(Class<T> type) {
 *         return injector.getInstance(type);
 *     }
 *
 *     @Override
 *     protected void doInjectMembers(Object target) {
 *         injector.injectMembers(target);
 *     }
 * }
 *
 * public class LiteDiAdapter extends BaseDiAdapter {
 *     @Override
 *     protected <T> T doGetInstance(Class<T> type) {
 *         return createInstance(type);
 *     }
 *
 *     @Override
 *     protected void doInjectMembers(Object target) {
 *         injectFieldsAndMethods(target);
 *     }
 * }
 * }</pre>
 *
 * <h2>Three-Tier Integration</h2>
 * <p>In the three-tier model, DiAdapter is passed to FxmlView/FxmlViewProvider:
 * <ul>
 *   <li><b>Tier 1 (Zero-Config):</b> No DiAdapter, views use no-arg constructor</li>
 *   <li><b>Tier 2 (Global DI):</b> Global DiAdapter via {@code FxmlKit.setDiAdapter()}</li>
 *   <li><b>Tier 3 (Isolated DI):</b> Per-instance DiAdapter via constructor injection</li>
 * </ul>
 *
 * @see DiAdapter
 * @see LiteDiAdapter
 */
public abstract class BaseDiAdapter implements DiAdapter {

    private static final Logger logger = Logger.getLogger(BaseDiAdapter.class.getName());

    /**
     * Gets an instance of the specified type.
     *
     * <p>This template method performs null checking and delegates to
     * {@link #doGetInstance(Class)} for framework-specific instantiation.
     *
     * @param <T>  the type parameter
     * @param type the class to instantiate (must not be null)
     * @return the created instance (never null)
     * @throws NullPointerException if type is null
     * @throws RuntimeException     if instantiation fails
     */
    @Override
    public final <T> T getInstance(Class<T> type) {
        if (type == null) {
            throw new NullPointerException("Type cannot be null");
        }

        logger.log(Level.FINE, "Creating instance of: {0}", type.getName());
        return doGetInstance(type);
    }

    /**
     * Injects members into an existing object.
     *
     * <p>This template method performs null checking and delegates to
     * {@link #doInjectMembers(Object)} for framework-specific member injection.
     *
     * @param target the object to inject (must not be null)
     * @throws NullPointerException if target is null
     * @throws RuntimeException     if injection fails
     */
    @Override
    public final void injectMembers(Object target) {
        if (target == null) {
            throw new NullPointerException("Cannot inject into null target");
        }

        logger.log(Level.FINE, "Injecting members into: {0}", target.getClass().getName());
        doInjectMembers(target);
    }

    /**
     * Creates an instance using the DI framework.
     *
     * <p>This is the primary hook method that subclasses must implement.
     * It should create and return an instance using the framework's
     * instantiation mechanism.
     *
     * <p><b>Implementation Examples:</b>
     * <ul>
     *   <li><b>Guice:</b> {@code return injector.getInstance(type);}</li>
     *   <li><b>Spring:</b> {@code return applicationContext.getBean(type);}</li>
     *   <li><b>Jakarta CDI:</b> {@code return CDI.current().select(type).get();}</li>
     *   <li><b>LiteDiAdapter:</b> Create instance with constructor injection</li>
     * </ul>
     *
     * @param <T>  the type parameter
     * @param type the class to instantiate (never null)
     * @return the created instance (never null)
     * @throws RuntimeException if instantiation fails
     */
    protected abstract <T> T doGetInstance(Class<T> type);

    /**
     * Injects dependencies into an existing object.
     *
     * <p>This is an optional hook method. Subclasses should override this if their
     * framework has a separate member injection method.
     *
     * <p><b>Default Implementation:</b> No-op (assumes getInstance does everything).
     *
     * <p><b>Override Examples:</b>
     * <ul>
     *   <li><b>Guice:</b> {@code injector.injectMembers(target);}</li>
     *   <li><b>Spring:</b> {@code beanFactory.autowireBean(target);}</li>
     *   <li><b>LiteDiAdapter:</b> Custom field/method injection logic</li>
     * </ul>
     *
     * @param target the object to inject (never null)
     * @throws RuntimeException if injection fails
     */
    protected void doInjectMembers(Object target) {
        // Default: No-op (assume getInstance does everything)
        // Subclasses override if they have separate member injection
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{}";
    }
}