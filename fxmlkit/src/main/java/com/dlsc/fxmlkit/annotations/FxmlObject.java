package com.dlsc.fxmlkit.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an FXML object that should receive dependency injection.
 *
 * <p>This annotation designates classes that should participate in the dependency
 * injection process when created by the FXML loader. It works with the configured
 * {@code FxmlInjectionPolicy} to determine which objects receive injection.
 *
 * <h2>Purpose</h2>
 * <p>Use this annotation on custom JavaFX components that need dependency injection:
 * <ul>
 *   <li>Custom controls (Button, TextField, etc. subclasses)</li>
 *   <li>Layout containers (Pane, HBox, VBox, etc. subclasses)</li>
 *   <li>Non-visual objects defined in FXML</li>
 * </ul>
 *
 * <h2>Three-Tier Behavior</h2>
 * <ul>
 *   <li><b>Tier 1 (Zero-Config):</b> Annotation is ignored, no injection occurs</li>
 *   <li><b>Tier 2 (Global DI):</b> Objects with this annotation receive injection</li>
 *   <li><b>Tier 3 (Isolated DI):</b> Objects with this annotation receive injection</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @FxmlObject
 * public class UserCard extends HBox {
 *     @Inject
 *     private UserService userService;
 *
 *     @PostInject
 *     private void initialize() {
 *         setText(userService.getCurrentUser().getName());
 *     }
 * }
 * }</pre>
 *
 * <h2>Injection Policy Interaction</h2>
 * <p>This annotation works differently depending on the configured policy:
 * <ul>
 *   <li><b>EXPLICIT_ONLY:</b> Only classes with this annotation are injected</li>
 *   <li><b>AUTO:</b> Classes with this annotation OR injection points are injected</li>
 *   <li><b>DISABLED:</b> No non-controller objects are injected (annotation ignored)</li>
 * </ul>
 *
 * @see com.dlsc.fxmlkit.fxml.FxmlKitLoader
 * @see com.dlsc.fxmlkit.policy.FxmlInjectionPolicy
 * @see com.dlsc.fxmlkit.annotations.PostInject
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FxmlObject {
}