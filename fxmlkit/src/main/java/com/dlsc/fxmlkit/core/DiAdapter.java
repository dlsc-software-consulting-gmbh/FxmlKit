package com.dlsc.fxmlkit.core;

/**
 * Adapter interface for dependency injection frameworks.
 *
 * <p>This interface provides a bridge between FxmlKit and various DI frameworks
 * (Guice, Spring, Jakarta CDI, etc.). It defines a two-phase injection model
 * to support JavaFX's FXML loading lifecycle.
 *
 * <h2>Two-Phase Injection Model</h2>
 * <p>FxmlKit uses a two-phase model to accommodate JavaFX's initialization sequence:
 * <ol>
 *   <li><b>Construction Phase:</b> {@link #getInstance(Class)} creates the object,
 *       optionally using constructor injection</li>
 *   <li><b>Member Injection Phase:</b> {@link #injectMembers(Object)} injects
 *       fields and methods into the created object</li>
 * </ol>
 *
 * <h2>Why Separate Phases?</h2>
 * <p>This separation is necessary for JavaFX FXML loading:
 * <ul>
 *   <li>Controller factory must return a controller instance</li>
 *   <li>JavaFX then initializes FXML fields (fx:id bindings)</li>
 *   <li>Then FxmlKit performs member injection</li>
 *   <li>Finally, lifecycle callbacks (@PostInject) are invoked</li>
 * </ul>
 *
 * <h2>Implementation Requirements</h2>
 * <ul>
 *   <li><b>getInstance():</b> MUST create instance, MAY use constructor injection</li>
 *   <li><b>injectMembers():</b> MUST be idempotent (safe to call multiple times)</li>
 *   <li><b>Both methods:</b> MUST handle null checks and provide clear error messages</li>
 * </ul>
 *
 * <h2>Framework-Specific Behavior</h2>
 * <p>Different DI frameworks handle the two phases differently:
 * <ul>
 *   <li><b>Guice:</b> {@code getInstance()} performs full injection in one call,
 *       {@code injectMembers()} is idempotent and can be called again</li>
 *   <li><b>LiteDiAdapter:</b> {@code getInstance()} only constructs,
 *       {@code injectMembers()} performs field/method injection</li>
 *   <li><b>Spring:</b> Typically uses {@code getBean()} for full injection,
 *       {@code autowireBean()} for member injection</li>
 * </ul>
 *
 * <h2>Three-Tier Integration</h2>
 * <p>DiAdapter is used in Tier 2 (Global DI) and Tier 3 (Isolated DI):
 * <ul>
 *   <li><b>Tier 1 (Zero-Config):</b> No DiAdapter configured, no injection occurs</li>
 *   <li><b>Tier 2 (Global DI):</b> Single DiAdapter set via {@code FxmlKit.setDiAdapter()}</li>
 *   <li><b>Tier 3 (Isolated DI):</b> Per-instance DiAdapter injected into ViewProvider</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Phase 1: Create instance (may include constructor injection)
 * MyController controller = di.getInstance(MyController.class);
 *
 * // ... JavaFX initializes FXML fields here ...
 *
 * // Phase 2: Inject members (fields and methods)
 * di.injectMembers(controller);
 *
 * // Safe to call again (idempotent)
 * di.injectMembers(controller);  // No-op or same effect
 * }</pre>
 *
 * <h2>Implementation Guide</h2>
 * <p>To integrate a new DI framework:
 * <ol>
 *   <li>Extend {@link BaseDiAdapter} for ViewProvider auto-injection support</li>
 *   <li>Implement {@code doGetInstance()} to create instances</li>
 *   <li>Implement {@code doInjectMembers()} if framework has separate member injection</li>
 *   <li>Ensure {@code injectMembers()} is idempotent</li>
 * </ol>
 *
 * @see BaseDiAdapter
 * @see LiteDiAdapter
 * @see com.dlsc.fxmlkit.GuiceDiAdapter
 */
public interface DiAdapter {

    /**
     * Creates a new instance of the specified type.
     *
     * <p>This method may perform constructor injection depending on the framework.
     * Field and method injection should be deferred to {@link #injectMembers(Object)}.
     *
     * <h3>Contract</h3>
     * <ul>
     *   <li>MUST return a non-null instance</li>
     *   <li>MAY use constructor injection (framework-dependent)</li>
     *   <li>SHOULD NOT inject fields or methods (defer to injectMembers)</li>
     *   <li>MUST throw clear exceptions if instantiation fails</li>
     * </ul>
     *
     * <h3>Framework Behavior</h3>
     * <ul>
     *   <li><b>Guice:</b> Uses {@code injector.getInstance()} which performs full injection
     *       (constructor + fields + methods) in one call</li>
     *   <li><b>LiteDiAdapter:</b> Creates instance with constructor injection only,
     *       fields and methods are NOT injected</li>
     *   <li><b>Spring:</b> Uses {@code beanFactory.createBean()} or similar,
     *       may perform full injection depending on configuration</li>
     * </ul>
     *
     * <p><b>Note:</b> Because different frameworks behave differently, callers should
     * always call {@link #injectMembers(Object)} after this method to ensure complete
     * injection, even if the framework may have already done it.
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
     * <p>This method is called after the object has been constructed and after
     * JavaFX has initialized FXML fields. It should inject all {@code @Inject}
     * annotated fields and methods.
     *
     * <h3>Idempotency Contract</h3>
     * <ul>
     *   <li>MUST be safe to call multiple times on the same object</li>
     *   <li>MUST NOT cause duplicate side effects</li>
     *   <li>MAY skip injection if already performed (optimization)</li>
     *   <li>MUST handle null target with NullPointerException</li>
     * </ul>
     *
     * <h3>Framework Behavior</h3>
     * <ul>
     *   <li><b>Guice:</b> Uses {@code injector.injectMembers()} which is naturally idempotent.
     *       Calling multiple times on the same object is safe and has no additional effect.</li>
     *   <li><b>LiteDiAdapter:</b> Tracks injected objects in a Set to ensure idempotency.
     *       Second call on the same object is a no-op.</li>
     *   <li><b>Spring:</b> Uses {@code autowireBean()} which may NOT be idempotent by default.
     *       Implementation must add tracking to ensure idempotency.</li>
     * </ul>
     *
     * <h3>Injection Order</h3>
     * <p>When this method is called, it should inject in the following order:
     * <ol>
     *   <li>All {@code @Inject} annotated fields (superclass first)</li>
     *   <li>All {@code @Inject} annotated methods (superclass first)</li>
     * </ol>
     *
     * <p><b>Note:</b> {@code @PostInject} methods are NOT called by this method.
     * They are called separately by FxmlKitLoader after member injection completes.
     *
     * @param target the object to inject (must not be null)
     * @throws NullPointerException if target is null
     * @throws RuntimeException     if injection fails
     */
    void injectMembers(Object target);
}