package com.dlsc.fxmlkit.samples.tier1.theme;

import javafx.scene.control.Label;

public class VersionLabel extends Label {

    public VersionLabel() {
        super("Version 1.0.0");

        getStyleClass().add("version-label");
    }

    @Override
    public String getUserAgentStylesheet() {
        return VersionLabel.class.getResource("version-label.css").toExternalForm();
    }
}
