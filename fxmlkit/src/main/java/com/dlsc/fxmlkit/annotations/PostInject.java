package com.dlsc.fxmlkit.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be called after dependency injection completes.
 *
 * <p>This annotation is similar to {@code javax.annotation.PostConstruct}, but designed
 * specifically for FxmlKit. Methods marked with this annotation are invoked after all
 * dependency injection (constructor, field, and method injection) has completed.
 *
 * <h2>Three-Tier Behavior</h2>
 * <ul>
 *   <li><b>Tier 1 (Zero-Config):</b> Methods with this annotation are NOT called</li>
 *   <li><b>Tier 2 (Global DI):</b> Methods with this annotation ARE called after injection</li>
 *   <li><b>Tier 3 (Isolated DI):</b> Methods with this annotation ARE called after injection</li>
 * </ul>
 *
 * <h2>Method Requirements</h2>
 * <ul>
 *   <li>Must have no parameters</li>
 *   <li>Return type is ignored</li>
 *   <li>Can have any access modifier (public, protected, private, package-private)</li>
 *   <li>Multiple methods per class are allowed</li>
 * </ul>
 *
 * <h2>Execution Order</h2>
 * <p>When multiple {@code @PostInject} methods exist in a class hierarchy:
 * <ol>
 *   <li>Superclass methods are called before subclass methods</li>
 *   <li>Within the same class, methods are called in alphabetical order</li>
 * </ol>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * public class DashboardController {
 *
 *     @Inject
 *     private UserService userService;
 *
 *     @PostInject
 *     private void initialize() {
 *         // Called after userService is injected
 *         System.out.println("User: " + userService.getCurrentUser());
 *     }
 * }
 * }</pre>
 *
 * <h2>FXML Integration</h2>
 * <p><b>Important:</b> Do not access {@code @FXML} fields inside {@code @PostInject} methods,
 * as FXML field injection occurs after this callback. Use JavaFX's {@code Initializable.initialize()}
 * method for FXML field access.
 *
 * <pre>{@code
 * public class MyController implements Initializable {
 *
 *     @Inject
 *     private UserService userService;
 *
 *     @FXML
 *     private Label nameLabel;
 *
 *     @PostInject
 *     private void postInject() {
 *         // userService is available
 *         // nameLabel is null - don't use it here!
 *     }
 *
 *     @Override
 *     public void initialize(URL url, ResourceBundle rb) {
 *         // Both userService and nameLabel are available
 *         nameLabel.setText(userService.getCurrentUser().getName());
 *     }
 * }
 * }</pre>
 *
 * @see com.dlsc.fxmlkit.annotations.FxmlObject
 * @see com.dlsc.fxmlkit.di.DiAdapter
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PostInject {
}