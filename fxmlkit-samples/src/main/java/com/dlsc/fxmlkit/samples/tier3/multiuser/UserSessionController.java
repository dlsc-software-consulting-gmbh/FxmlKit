package com.dlsc.fxmlkit.samples.tier3.multiuser;

import com.google.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controller for user session view.
 *
 * <p>Each instance of this controller receives its own SessionService
 * via the per-instance DiAdapter, demonstrating session isolation.
 */
public class UserSessionController {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label sessionIdLabel;

    @FXML
    private Label loginTimeLabel;

    @FXML
    private Label actionCountLabel;

    @Inject
    private SessionService sessionService;

    @FXML
    public void initialize() {
        updateDisplay();
    }

    @FXML
    private void onPerformAction() {
        sessionService.incrementAction();
        updateDisplay();
    }

    private void updateDisplay() {
        welcomeLabel.setText(sessionService.getWelcomeMessage());
        sessionIdLabel.setText("Session: " + sessionService.getSessionId());
        loginTimeLabel.setText("Login: " + sessionService.getLoginTime());
        actionCountLabel.setText("Actions: " + sessionService.getActionCount());
    }
}