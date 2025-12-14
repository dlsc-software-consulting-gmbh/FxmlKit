package com.dlsc.fxmlkit.samples.tier2.login;

import com.dlsc.fxmlkit.di.LiteDiAdapter;
import com.dlsc.fxmlkit.fxml.FxmlKit;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Tier 2 Demo Application: Global Dependency Injection.
 */
public class LoginApp extends Application {

    @Override
    public void start(Stage stage) {
        // Step 1: Create DiAdapter
        LiteDiAdapter diAdapter = new LiteDiAdapter();

        // Step 2: Register services
        diAdapter.bindInstance(AuthService.class, new AuthService());

        // Step 3: Configure FxmlKit globally (one-time setup)
        FxmlKit.setDiAdapter(diAdapter);

        // Step 4: Now all Views will use this DiAdapter
        LoginView loginView = new LoginView();

        stage.setScene(new Scene(loginView));
        stage.setTitle("FxmlKit - Tier 2: Global DI");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}