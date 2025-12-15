package com.dlsc.fxmlkit.di;

/**
 * Adapter interface for dependency injection frameworks.
 *
 * <p>This interface bridges FxmlKit with DI frameworks (Guice, Spring, Jakarta CDI, etc.)
 * using a two-phase injection model to accommodate JavaFX's FXML loading lifecycle:
 * <ol>
 *   <li>{@link #getInstance(Class)} creates the object (constructor injection)
 *   <li>{@link #injectMembers(Object)} injects fields and methods after FXML initialization
 * </ol>
 *
 * <p>The separation is necessary because JavaFX's controller factory must return a controller
 * instance before JavaFX initializes FXML fields (fx:id bindings), and only then can member
 * injection occur followed by lifecycle callbacks (@PostInject).
 *
 * <p>Implementation requirements: {@code getInstance} must create the instance and may use
 * constructor injection. {@code injectMembers} must be idempotent (safe to call multiple times).
 * Both methods must handle null inputs with clear exceptions.
 *
 * <p>Framework-specific behavior varies: Guice's {@code getInstance} performs full injection,
 * {@code injectMembers} is idempotent; LiteDiAdapter's {@code getInstance} only constructs,
 * {@code injectMembers} performs field/method injection; Spring typically uses {@code getBean}
 * for full injection, {@code autowireBean} for member injection.
 *
 * <p>Usage in FxmlKit's three-tier model:
 * <ul>
 *   <li>Tier 1 (Zero-Config): No DiAdapter, no injection
 *   <li>Tier 2 (Global DI): Single DiAdapter via {@code FxmlKit.setDiAdapter()}
 *   <li>Tier 3 (Isolated DI): Per-instance DiAdapter injected into ViewProvider
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * // Phase 1: create instance
 * MyController controller = di.getInstance(MyController.class);
 *
 * // JavaFX initializes FXML fields here...
 *
 * // Phase 2: inject members
 * di.injectMembers(controller);
 *
 * // Safe to call again (idempotent)
 * di.injectMembers(controller);
 * }</pre>
 *
 * <p>To integrate a new DI framework:
 * <ol>
 *   <li>Extend {@link BaseDiAdapter} for ViewProvider auto-injection support
 *   <li>Implement {@code doGetInstance} to create instances
 *   <li>Implement {@code doInjectMembers} if framework has separate member injection
 *   <li>Ensure {@code injectMembers} is idempotent
 * </ol>
 *
 * @see BaseDiAdapter
 * @see LiteDiAdapter
 */
public interface DiAdapter {

    /**
     * Creates a new instance of the specified type.
     *
     * <p>This method may perform constructor injection depending on the framework.
     * Field and method injection should be deferred to {@link #injectMembers(Object)}.
     *
     * <p>Contract: must return non-null; may use constructor injection; should not inject
     * fields or methods (defer to injectMembers); must throw clear exceptions on failure.
     *
     * <p>Framework behavior: Guice uses {@code injector.getInstance} which performs full
     * injection (constructor + fields + methods); LiteDiAdapter creates instance with
     * constructor injection only; Spring uses {@code beanFactory.createBean} which may
     * perform full injection depending on configuration.
     *
     * <p>Note: Because frameworks behave differently, callers should always call
     * {@link #injectMembers(Object)} after this method to ensure complete injection.
     *
     * @param <T>  the type to create
     * @param type the class object (must not be null)
     * @return a new instance (never null)
     * @throws NullPointerException if type is null
     * @throws RuntimeException     if instance creation fails
     */
    <T> T getInstance(Class<T> type);

    /**
     * Injects dependencies into fields and methods of an existing object.
     *
     * <p>Called after the object has been constructed and after JavaFX has initialized
     * FXML fields. Should inject all @Inject annotated fields and methods.
     *
     * <p>Idempotency contract: must be safe to call multiple times on the same object;
     * must not cause duplicate side effects; may skip injection if already performed
     * (optimization); must handle null target with NullPointerException.
     *
     * <p>Framework behavior: Guice's {@code injector.injectMembers} is naturally idempotent,
     * calling multiple times has no additional effect; LiteDiAdapter tracks injected objects
     * to ensure idempotency, second call is a no-op; Spring's {@code autowireBean} may not
     * be idempotent by default, implementation must add tracking.
     *
     * <p>Injection order: all @Inject fields (superclass first), then all @Inject methods
     * (superclass first). @PostInject methods are not called by this method, they are
     * called separately by FxmlKitLoader after member injection completes.
     *
     * @param target the object to inject (must not be null)
     * @throws NullPointerException if target is null
     * @throws RuntimeException     if injection fails
     */
    void injectMembers(Object target);
}