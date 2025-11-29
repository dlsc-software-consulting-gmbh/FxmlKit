package com.dlsc.fxmlkit.samples.tier2.fxmlobject;

import com.dlsc.fxmlkit.annotations.PostInject;
import javafx.fxml.FXML;

/**
 * Controller for the dashboard view.
 */
public class DashboardController {

    @FXML
    private StatusLabel statusLabel;

    @FXML
    public void initialize() {
        // Initial refresh
        statusLabel.refresh();
    }

    @FXML
    private void onRefresh() {
        statusLabel.refresh();
    }

    @PostInject
    private void afterInjection() {
        System.out.println("DashboardController: Post injection logic executed.");
    }
}