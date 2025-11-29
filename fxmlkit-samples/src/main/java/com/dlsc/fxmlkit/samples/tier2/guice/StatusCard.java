package com.dlsc.fxmlkit.samples.tier2.guice;

import com.dlsc.fxmlkit.annotations.FxmlObject;
import com.dlsc.fxmlkit.annotations.PostInject;
import com.google.inject.Inject;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * A self-contained card that displays injection status.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>{@code @FxmlObject} - Enables DI when used in FXML</li>
 *   <li>{@code @Inject} - Field injection</li>
 *   <li>{@code @PostInject} - Post-injection initialization</li>
 * </ul>
 */
@FxmlObject
public class StatusCard extends VBox {

    @Inject
    private TimeService timeService;

    private final Label titleLabel;
    private final Label statusLabel;
    private final Label timeLabel;

    public StatusCard() {
        // Setup UI
        setAlignment(Pos.CENTER);
        setSpacing(8);
        setPadding(new Insets(15));
        setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 8;");

        titleLabel = new Label("@FxmlObject Card");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        statusLabel = new Label("Waiting for injection...");
        statusLabel.setStyle("-fx-font-size: 12px;");

        timeLabel = new Label("");
        timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        getChildren().addAll(titleLabel, statusLabel, timeLabel);
    }

    @PostInject
    private void onReady() {
        // This runs after injection - completely self-contained
        if (timeService != null) {
            statusLabel.setText("● TimeService injected!");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #18b518;");
            timeLabel.setText("Server time: " + timeService.getCurrentTime());
        } else {
            statusLabel.setText("● Injection failed!");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e01f1f;");
        }
    }
}