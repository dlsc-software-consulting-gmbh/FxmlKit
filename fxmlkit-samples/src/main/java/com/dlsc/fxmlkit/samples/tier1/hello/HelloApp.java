package com.dlsc.fxmlkit.samples.tier1.hello;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        HelloView helloView = new HelloView();
        // Optional<HelloController> controller = helloView.getController();

        primaryStage.setScene(new Scene(helloView, 600, 400));
        primaryStage.setTitle("Hello FXMLKit - FxmlView");
        primaryStage.show();
    }
}
