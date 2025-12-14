package com.dlsc.fxmlkit.samples.tier2.fxmlobject;

import com.dlsc.fxmlkit.di.LiteDiAdapter;
import com.dlsc.fxmlkit.fxml.FxmlKit;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Tier 2 Demo: @FxmlObject Injection.
 *
 * <p>Demonstrates how custom components in FXML can receive
 * dependency injection via {@code @FxmlObject} annotation.
 */
public class FxmlObjectApp extends Application {

    @Override
    public void start(Stage stage) {
        // Setup DI
        LiteDiAdapter diAdapter = new LiteDiAdapter();
        diAdapter.bindInstance(StatusService.class, new StatusService());
        FxmlKit.setDiAdapter(diAdapter);

        // Create view - StatusLabel inside will have StatusService injected!
        DashboardView view = new DashboardView();

        stage.setScene(new Scene(view));
        stage.setTitle("FxmlKit - @FxmlObject Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}