package com.dlsc.fxmlkit.samples.tier1.provider;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class WelcomeApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        WelcomeViewProvider viewProvider = new WelcomeViewProvider();
        Parent view = viewProvider.getView();
        // WelcomeController controller = viewProvider.getController();

        primaryStage.setScene(new Scene(view, 600, 400));
        primaryStage.setTitle("FXMLKit Demo - FxmlViewProvider");
        primaryStage.show();
    }
}
