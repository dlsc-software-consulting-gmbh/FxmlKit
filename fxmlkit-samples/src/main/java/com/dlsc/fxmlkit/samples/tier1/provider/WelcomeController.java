package com.dlsc.fxmlkit.samples.tier1.provider;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class WelcomeController {

    @FXML
    private Label greetingLabel;

    @FXML
    private TextField nameField;

    @FXML
    private void onGreet() {
        String name = nameField.getText().trim();
        greetingLabel.setText(name.isEmpty() ? "Welcome!" : "Welcome, " + name + "!");
    }
}