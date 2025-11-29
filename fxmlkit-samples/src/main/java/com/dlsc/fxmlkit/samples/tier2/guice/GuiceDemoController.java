package com.dlsc.fxmlkit.samples.tier2.guice;

import com.google.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class GuiceDemoController {

    @FXML
    private Label userLabel;

    @FXML
    private Label roleLabel;

    @Inject
    private UserService userService;

    @FXML
    public void initialize() {
        if (userService != null) {
            userLabel.setText("● " + userService.getUserName());
            userLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: green;");
            roleLabel.setText("Role: " + userService.getRole());
        } else {
            userLabel.setText("● Injection failed!");
            userLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: red;");
        }
    }
}