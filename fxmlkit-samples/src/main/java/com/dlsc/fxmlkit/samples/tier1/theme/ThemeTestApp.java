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
 *       instead of the native API. This is because {@code Application.userAgentStylesheet} is a
 *       plain String (no Property), so FxmlKit provides a bridged StringProperty for monitoring.</li>
 *   <li><b>Scene/SubScene levels:</b> Automatically monitored via their built-in
 *       {@code userAgentStylesheetProperty()}. Just use the native JavaFX API.</li>
 *   <li><b>Custom controls:</b> Automatically detected and monitored. FxmlKit promotes the
 *       stylesheet to {@code getStylesheets().add(0, ...)} at development time to enable
 *       hot-reload. See {@link VersionLabel} for an example.</li>
 * </ul>
 *
 * @see ThemeTestController
 * @see VersionLabel
 * @see FxmlKit#setApplicationUserAgentStylesheet(String)
 */
public class ThemeTestApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FxmlKit.enableDevelopmentMode();

        Scene scene = new Scene(new ThemeTestView());

        primaryStage.setTitle("User Agent Stylesheet Test");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}