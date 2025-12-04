package com.dlsc.fxmlkit.samples.tier1.i18n;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Locale;
import java.util.ResourceBundle;

public class I18nApp extends Application {

    private static final String BUNDLE_BASE_NAME = "com.dlsc.fxmlkit.samples.tier1.messages";

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load in English
        I18nView englishView = new I18nView(getBundle(Locale.ENGLISH));

        // Load in German
        I18nView germanView = new I18nView(getBundle(Locale.GERMAN));

        // Initially load in English, then switch to Chinese
        I18nView chineseView = new I18nView(getBundle(Locale.ENGLISH));
        // reload to Chinese
        chineseView.reload(getBundle(Locale.SIMPLIFIED_CHINESE));

        VBox root = new VBox(20, englishView, new Separator(), germanView, new Separator(), chineseView);
        root.setStyle("-fx-padding: 20; -fx-alignment: center;");
        primaryStage.setScene(new Scene(root, 500, 290));
        primaryStage.setTitle("FxmlKit I18n 示例");
        primaryStage.show();
    }

    private ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
    }
}
