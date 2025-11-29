package com.dlsc.fxmlkit.samples.tier1.viewpath;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class CustomPathApp extends Application {

    @Override
    public void start(Stage stage) {
        CustomPathView view = new CustomPathView();

        stage.setScene(new Scene(view));
        stage.setTitle("FxmlKit - Custom Path Demo");
        stage.show();
    }
}
