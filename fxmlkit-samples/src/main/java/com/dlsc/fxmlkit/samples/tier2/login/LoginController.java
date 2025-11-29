package com.dlsc.fxmlkit.samples.tier2.login;

import com.dlsc.fxmlkit.annotations.PostInject;
import com.google.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;


/**
 * Controller for the login view.
 *
 * <p>This controller demonstrates Tier 2 dependency injection:
 * the {@link AuthService} is automatically injected by FxmlKit
 * using the globally configured {@link com.dlsc.fxmlkit.core.DiAdapter}.</p>
 *
 * <h2>Key Points</h2>
 * <ul>
 *   <li>{@code @Inject} annotation marks fields for injection</li>
 *   <li>No manual instantiation of AuthService</li>
 *   <li>Service is provided by the global DiAdapter</li>
 * </ul>
 */
public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label messageLabel;

    @Inject
    private AuthService authService;

    @FXML
    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage("Please enter username and password", false);
            return;
        }

        if (authService.authenticate(username, password)) {
            showMessage(authService.getWelcomeMessage(username), true);
        } else {
            showMessage("Invalid credentials. Try admin/password", false);
        }
    }

    private void showMessage(String message, boolean success) {
        messageLabel.setText(message);
        messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + (success ? "#3d7b3d" : "#aa4646") + ";");
    }

    @PostInject
    private void afterInjection() {
        // Optional initialization after dependencies are injected
        System.out.println("AuthService injected: " + (authService != null));
    }
}