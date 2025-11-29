package com.dlsc.fxmlkit.samples.tier2.fxmlobject;

import com.dlsc.fxmlkit.fxml.FxmlView;

/**
 * Tier 2 Demo: @FxmlObject Injection.
 *
 * <p>This example demonstrates how custom components used in FXML
 * can receive dependency injection via the {@code @FxmlObject} annotation.
 *
 * <h2>The Problem</h2>
 * <p>When you use a custom component in FXML like {@code <StatusLabel/>},
 * the FXMLLoader creates it via reflection using the no-arg constructor.
 * Any {@code @Inject} fields remain {@code null}.
 *
 * <h2>The Solution</h2>
 * <p>Mark the component with {@code @FxmlObject}. FxmlKit will intercept
 * the creation and inject dependencies automatically.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Custom controls that need services</li>
 *   <li>Reusable components with dependencies</li>
 *   <li>Any class instantiated in FXML that needs DI</li>
 * </ul>
 *
 * @see StatusLabel
 */
public class DashboardView extends FxmlView<DashboardController> {
}