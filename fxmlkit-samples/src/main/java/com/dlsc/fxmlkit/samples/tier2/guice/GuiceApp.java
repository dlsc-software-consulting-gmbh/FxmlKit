package com.dlsc.fxmlkit.samples.tier2.guice;

import com.dlsc.fxmlkit.fxml.FxmlKit;
import com.dlsc.fxmlkit.guice.GuiceDiAdapter;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Tier 2 Demo: Guice Integration.
 */
public class GuiceApp extends Application {

    @Override
    public void start(Stage stage) {
        // Setup Guice
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(UserService.class).toInstance(new UserService());
                bind(TimeService.class).toInstance(new TimeService());
            }
        });

        // Configure FxmlKit
        GuiceDiAdapter guiceDiAdapter = new GuiceDiAdapter(injector);
        FxmlKit.setDiAdapter(guiceDiAdapter);

        // Create view
        stage.setScene(new Scene(new GuiceDemoView()));
        stage.setTitle("FxmlKit + Guice Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}