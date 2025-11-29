package com.dlsc.fxmlkit.samples.tier1.viewpath;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class CustomPathController {

    @FXML
    private Label messageLabel;

    private int clickCount = 0;

    @FXML
    private void onClick() {
        clickCount++;
        messageLabel.setText("Clicked " + clickCount + " time(s)!");
    }
}