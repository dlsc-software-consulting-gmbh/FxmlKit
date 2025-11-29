open module com.dlsc.fxmlkit {
    requires transitive javafx.controls;
    requires transitive javafx.fxml;

    requires java.xml;
    requires java.logging;

    exports com.dlsc.fxmlkit.annotations;
    exports com.dlsc.fxmlkit.core;
    exports com.dlsc.fxmlkit.fxml;
    exports com.dlsc.fxmlkit.policy;
}