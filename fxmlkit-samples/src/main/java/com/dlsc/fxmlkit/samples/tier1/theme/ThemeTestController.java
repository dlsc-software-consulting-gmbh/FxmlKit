package com.dlsc.fxmlkit.samples.tier1.theme;

import com.dlsc.fxmlkit.fxml.FxmlKit;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;

import java.net.URL;
import java.util.ResourceBundle;

public class ThemeTestController implements Initializable {

    @FXML
    private Label currentThemeLabel;

    private Scene scene;

    private static final String LIGHT_THEME = "com/dlsc/fxmlkit/samples/tier1/theme/light-theme.css";
    private static final String DARK_THEME = "com/dlsc/fxmlkit/samples/tier1/theme/dark-theme.css";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        FxmlKit.setApplicationUserAgentStylesheet(LIGHT_THEME);
    }

    @FXML
    public void switchToLight() {
        if (getScene() != null) {
            FxmlKit.setApplicationUserAgentStylesheet(LIGHT_THEME);
            currentThemeLabel.setText("Current: Light Theme");
            System.out.println("Switched to Light Theme (User Agent Stylesheet)");
        }
    }

    @FXML
    public void switchToDark() {
        if (getScene() != null) {
            FxmlKit.setApplicationUserAgentStylesheet((DARK_THEME));
            currentThemeLabel.setText("Current: Dark Theme");
            System.out.println("Switched to Dark Theme (User Agent Stylesheet)");
        }
    }

    private Scene getScene() {
        if (scene == null) {
            scene = currentThemeLabel.getScene();
        }
        return scene;
    }

}