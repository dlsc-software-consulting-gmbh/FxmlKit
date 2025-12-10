package com.dlsc.fxmlkit.samples.tier1.theme;

import com.dlsc.fxmlkit.fxml.FxmlKit;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Demonstrates User Agent Stylesheet hot-reload support in FxmlKit.
 *
 * <p>JavaFX supports User Agent Stylesheets at three levels:</p>
 * <ul>
 *   <li><b>Application level</b> - {@link Application#setUserAgentStylesheet(String)}</li>
 *   <li><b>Scene level</b> - {@link Scene#setUserAgentStylesheet(String)}</li>
 *   <li><b>SubScene level</b> - {@link javafx.scene.SubScene#setUserAgentStylesheet(String)}</li>
 * </ul>
 *
 * <p><b>Hot-reload behavior:</b></p>
 * <ul>
 *   <li><b>Application level:</b> Requires {@link FxmlKit#setApplicationUserAgentStylesheet(String)}
 *       for hot-reload support. This is because {@code Application.userAgentStylesheet} is a plain
 *       String (getter/setter only, no Property), so FxmlKit provides a bridged
 *       {@link javafx.beans.property.StringProperty} to enable monitoring and reactive binding.</li>
 *   <li><b>Scene/SubScene levels:</b> Automatically monitored when CSS hot-reload is enabled.
 *       Just use the native JavaFX API ({@code scene.setUserAgentStylesheet()}) and FxmlKit
 *       will detect changes via their built-in {@code userAgentStylesheetProperty()}.</li>
 * </ul>
 *
 * @see ThemeTestController
 * @see FxmlKit#setApplicationUserAgentStylesheet(String)
 * @see FxmlKit#applicationUserAgentStylesheetProperty()
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