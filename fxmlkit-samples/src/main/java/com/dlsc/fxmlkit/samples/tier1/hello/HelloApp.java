package com.dlsc.fxmlkit.samples.tier1.hello;

import com.dlsc.fxmlkit.fxml.FxmlKit;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Enable FXML/CSS hot reload - edit files and see changes instantly without restart
        // Recommended for development only; comment out or remove for production
        FxmlKit.enableDevelopmentMode();

        HelloView helloView = new HelloView();
        // Optional<HelloController> controller = helloView.getController();

        Scene scene = new Scene(helloView, 600, 400);
        scene.getStylesheets().add(getClass().getResource("hello-app.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("Hello FXMLKit - FxmlView");
        primaryStage.show();
    }
}
