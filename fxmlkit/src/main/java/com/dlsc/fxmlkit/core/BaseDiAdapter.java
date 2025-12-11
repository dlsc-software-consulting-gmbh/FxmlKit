package com.dlsc.fxmlkit.core;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class for DiAdapter implementations.
 *
 * <p>Provides template method pattern for framework-specific DI adapter implementations.
 * Handles null-safety checks for getInstance and injectMembers, delegates to framework-specific
 * hook methods {@link #doGetInstance} and {@link #doInjectMembers}.
 *
 * <p>Template methods ({@link #getInstance} and {@link #injectMembers}) perform null checks,
 * hook methods ({@link #doGetInstance} and {@link #doInjectMembers}) contain framework-specific logic.
 *
 * <p>Implementation example:
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
 * }</pre>
 *
 * <p>In the three-tier model: Tier 1 (zero-config) uses no DiAdapter, Tier 2 (global DI)
 * uses DiAdapter via {@code FxmlKit.setDiAdapter()}, Tier 3 (isolated DI) uses per-instance
 * DiAdapter via constructor injection.
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
     * Should create and return an instance using the framework's instantiation mechanism.
     *
     * <p>Implementation examples: Guice uses {@code injector.getInstance(type)},
     * Spring uses {@code applicationContext.getBean(type)}, Jakarta CDI uses
     * {@code CDI.current().select(type).get()}, LiteDiAdapter creates instance
     * with constructor injection.
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
     * <p>Default implementation is no-op (assumes getInstance does everything).
     *
     * <p>Override examples: Guice uses {@code injector.injectMembers(target)},
     * Spring uses {@code beanFactory.autowireBean(target)}, LiteDiAdapter uses
     * custom field/method injection logic.
     *
     * @param target the object to inject (never null)
     * @throws RuntimeException if injection fails
     */
    protected void doInjectMembers(Object target) {
        // Default: No-op (assume getInstance does everything)
        // Subclasses override if they have separate member injection
    }

}