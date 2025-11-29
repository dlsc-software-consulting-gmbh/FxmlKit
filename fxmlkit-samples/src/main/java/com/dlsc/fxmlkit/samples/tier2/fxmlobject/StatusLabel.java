package com.dlsc.fxmlkit.samples.tier2.fxmlobject;

import com.dlsc.fxmlkit.annotations.FxmlObject;
import com.dlsc.fxmlkit.annotations.PostInject;
import com.google.inject.Inject;
import javafx.scene.control.Label;


/**
 * A custom Label that displays system status.
 *
 * <p>This component demonstrates {@link FxmlObject @FxmlObject} injection:
 * when this class is instantiated in FXML, FxmlKit will automatically
 * inject its dependencies.
 *
 * <h2>Key Points</h2>
 * <ul>
 *   <li>{@code @FxmlObject} marks this class for DI when used in FXML</li>
 *   <li>{@code @Inject} fields are populated by the DiAdapter</li>
 *   <li>Works seamlessly in FXML: {@code <StatusLabel />}</li>
 * </ul>
 *
 * <h2>Without @FxmlObject</h2>
 * <p>FXMLLoader would create this via no-arg constructor,
 * and {@code statusService} would be {@code null}.
 *
 * <h2>With @FxmlObject</h2>
 * <p>FxmlKit intercepts the creation and injects dependencies.
 */
@FxmlObject
public class StatusLabel extends Label {

    @Inject
    private StatusService statusService;

    public StatusLabel() {
        getStyleClass().add("status-label");
    }

    /**
     * Refreshes the status display.
     * Called from FXML or Controller.
     */
    public void refresh() {
        if (statusService != null) {
            setText("● " + statusService.getStatus() + " | v" + statusService.getVersion() + " | " + statusService.getServerTime());
            setStyle("-fx-text-fill: green;");
        } else {
            setText("● Service not injected!");
            setStyle("-fx-text-fill: red;");
        }
    }

    @PostInject
    private void afterInjection() {
        // Optionally refresh after injection
        refresh();
    }
}