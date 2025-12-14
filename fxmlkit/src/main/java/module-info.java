open module com.dlsc.fxmlkit {
    requires transitive javafx.controls;
    requires transitive javafx.fxml;

    requires java.xml;
    requires java.logging;

    exports com.dlsc.fxmlkit.annotations;
    exports com.dlsc.fxmlkit.di;
    exports com.dlsc.fxmlkit.fxml;
}