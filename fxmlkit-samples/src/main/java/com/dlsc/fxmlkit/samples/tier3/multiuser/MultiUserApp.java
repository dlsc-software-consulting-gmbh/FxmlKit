package com.dlsc.fxmlkit.samples.tier3.multiuser;


import com.dlsc.fxmlkit.core.DiAdapter;
import com.dlsc.fxmlkit.guice.GuiceDiAdapter;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Tier 3 Demo: Multi-User Session Isolation.
 *
 * <p>This application demonstrates per-instance dependency injection
 * by simulating multiple user sessions in separate tabs.
 *
 * <h2>What This Demo Shows</h2>
 * <ul>
 *   <li>Each tab represents a different user session</li>
 *   <li>Each session has its own DiAdapter with isolated services</li>
 *   <li>Actions in one tab don't affect other tabs</li>
 * </ul>
 *
 * <h2>JPro Relevance</h2>
 * <p>In a real JPro application, each browser connection would
 * create a new session with its own DiAdapter. This demo simulates
 * that concept using TabPane for visualization.
 *
 * <h2>Key Difference from Tier 2</h2>
 * <pre>
 * // Tier 2: Global DiAdapter
 * FxmlKit.setDiAdapter(adapter);
 * new MyView();
 *
 * // Tier 3: Per-Instance DiAdapter
 * new MyView(sessionAdapter);
 * </pre>
 */
public class MultiUserApp extends Application {

    @Override
    public void start(Stage stage) {
        // Create tabs for different "users" - each with isolated session
        Tab aliceTab = createUserTab("Alice");
        Tab bobTab = createUserTab("Bob");
        Tab charlieTab = createUserTab("Charlie");

        TabPane tabPane = new TabPane(aliceTab, bobTab, charlieTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Header
        Label header = new Label("FxmlKit - Tier 3: Per-Instance DI");
        header.setStyle("-fx-font-size: 14px; -fx-text-fill: gray; -fx-padding: 10;");

        Label subHeader = new Label("Each tab has its own DiAdapter and SessionService");
        subHeader.setStyle("-fx-font-size: 11px; -fx-text-fill: #999; -fx-padding: 0 0 10 10;");

        VBox root = new VBox(header, subHeader, tabPane);

        stage.setScene(new Scene(root, 600, 380));
        stage.setTitle("FxmlKit - Tier 3 Demo");
        stage.show();
    }

    /**
     * Creates a tab with an isolated user session.
     *
     * <p>Each call creates:
     * <ul>
     *   <li>A new SessionService with the given username</li>
     *   <li>A new Guice Injector with that SessionService</li>
     *   <li>A new DiAdapter wrapping the Injector</li>
     *   <li>A new UserSessionView using that DiAdapter</li>
     * </ul>
     *
     * @param username the username for this session
     * @return a Tab containing the user's session view
     */
    private Tab createUserTab(String username) {
        // Create session-specific service
        SessionService sessionService = new SessionService(username);

        // Create isolated Guice Injector for this session
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(SessionService.class).toInstance(sessionService);
            }
        });

        // Create per-instance DiAdapter
        DiAdapter diAdapter = new GuiceDiAdapter(injector);

        // Create View with its own DiAdapter (Tier 3 pattern!)
        UserSessionView view = new UserSessionView(diAdapter);

        return new Tab(username, view);
    }

    public static void main(String[] args) {
        launch(args);
    }
}