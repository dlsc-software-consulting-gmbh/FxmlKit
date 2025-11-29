package com.dlsc.fxmlkit.samples.tier1.hello;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

public class HelloController implements Initializable {

    @FXML
    private TextField nameField;
    @FXML
    private Label greetingLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("HelloController initialized");
    }

    @FXML
    private void onSayHello() {
        String name = nameField.getText().trim();
        greetingLabel.setText(name.isEmpty() ? "Hello, World!" : "Hello, " + name + "!");
    }

}
