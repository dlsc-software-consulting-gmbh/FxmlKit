package com.dlsc.fxmlkit.samples.tier1.theme;

import com.dlsc.fxmlkit.fxml.FxmlKit;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Demonstrates User Agent Stylesheet hot-reload support in FxmlKit.
 *
 * <p>JavaFX supports User Agent Stylesheets at multiple levels:</p>
 * <ul>
 *   <li><b>Application level</b> - {@link Application#setUserAgentStylesheet(String)}</li>
 *   <li><b>Scene level</b> - {@link Scene#setUserAgentStylesheet(String)}</li>
 *   <li><b>SubScene level</b> - {@link javafx.scene.SubScene#setUserAgentStylesheet(String)}</li>
 *   <li><b>Custom controls</b> - {@link javafx.scene.layout.Region#getUserAgentStylesheet()}</li>
 * </ul>
 *
 * <p><b>Hot-reload behavior:</b></p>
 * <ul>
 *   <li><b>Application level:</b> Use {@link FxmlKit#setApplicationUserAgentStylesheet(String)}
 *       instead of the native API. This enables hot-reload monitoring via a bridged StringProperty,
 *       since {@code Application.userAgentStylesheet} is a static getter/setter without Property support.</li>
 *   <li><b>Scene/SubScene levels:</b> Automatically monitored via their built-in
 *       {@code userAgentStylesheetProperty()}. Just use the native JavaFX API.</li>
 *   <li><b>Custom controls:</b> Requires explicit enablement via
 *       {@link FxmlKit#setControlUAHotReloadEnabled(boolean)} due to CSS priority implications.
 *       When enabled, FxmlKit promotes the UA stylesheet to {@code getStylesheets().add(0, ...)}
 *       to enable monitoring. See {@link VersionLabel} for an example custom control.</li>
 * </ul>
 *
 * <p><b>Note:</b> Custom control UA hot-reload is disabled by default because it changes
 * CSS cascade priority. Only enable during development when needed.
 *
 * @see ThemeTestController
 * @see VersionLabel
 * @see FxmlKit#setApplicationUserAgentStylesheet(String)
 * @see FxmlKit#setControlUAHotReloadEnabled(boolean)
 */
public class ThemeTestApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Enable FXML/CSS hot reload for development
        FxmlKit.enableDevelopmentMode();

        // Enable hot-reload for custom control UA stylesheets (e.g., VersionLabel)
        // Note: This is opt-in due to CSS priority changes - see FxmlKit docs for details
        FxmlKit.setControlUAHotReloadEnabled(true);

        Scene scene = new Scene(new ThemeTestView());

        primaryStage.setTitle("User Agent Stylesheet Test");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}