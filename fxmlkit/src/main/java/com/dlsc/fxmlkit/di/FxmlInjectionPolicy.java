package com.dlsc.fxmlkit.di;

/**
 * Policy for determining which FXML-created nodes receive dependency injection.
 *
 * <p>This enum controls injection behavior for objects created during FXML parsing.
 * Controllers are always injected regardless of policy (in Tier 2/3 modes).
 *
 * <p>Available policies:
 * <ul>
 *   <li>{@link #EXPLICIT_ONLY} - Only inject nodes marked with @FxmlObject (default)
 *   <li>{@link #AUTO} - Auto-detect nodes with injection points
 *   <li>{@link #DISABLED} - Disable all node injection, controllers only
 * </ul>
 *
 * <p>In Tier 1 (zero-config mode), this policy is ignored as no injection occurs.
 *
 * <p>Example:
 * <pre>{@code
 * // Production: explicit control
 * FxmlKit.setNodeInjectionPolicy(FxmlInjectionPolicy.EXPLICIT_ONLY);
 *
 * @FxmlObject
 * public class CustomButton extends Button {
 *     @Inject private StyleService styleService;
 * }
 * }</pre>
 */
public enum FxmlInjectionPolicy {

    /**
     * Only inject nodes explicitly marked with @FxmlObject.
     *
     * <p>This is the default and recommended policy for production. Provides
     * explicit control over which nodes receive injection, avoiding unintended
     * injection of third-party controls.
     *
     * <p>Injection occurs when:
     * <ul>
     *   <li>Node has @FxmlObject annotation, or
     *   <li>Node type is in the include list
     * </ul>
     *
     * <p>All other nodes are skipped, even if they have @Inject fields.
     */
    EXPLICIT_ONLY,

    /**
     * Automatically inject nodes with injection points.
     *
     * <p>Nodes are injected if they have @Inject fields/methods/constructors,
     * non-default constructors, @FxmlObject annotation, or are in the include list.
     *
     * <p>Detection process: check exclude list and @SkipInjection first (skip if matched),
     * then check include list or @FxmlObject (inject if matched), otherwise scan for
     * injection points via reflection and inject if found.
     *
     * <p>This mode reduces boilerplate but has higher reflection overhead during loading.
     * Use for rapid development when convenience is more important than explicit control.
     */
    AUTO,

    /**
     * Disable all node injection, only inject controllers.
     *
     * <p>This policy provides maximum performance by bypassing node injection entirely.
     * Only FXML controllers receive dependency injection. All FXML-created nodes are
     * never injected, and include/exclude lists plus @FxmlObject are ignored.
     *
     * <p>Use when custom controls don't need DI, all business logic lives in controllers,
     * or when maximum performance is required.
     */
    DISABLED
}