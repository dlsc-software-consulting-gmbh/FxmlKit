package com.dlsc.fxmlkit.samples.tier3.multiuser;

import com.dlsc.fxmlkit.core.DiAdapter;
import com.dlsc.fxmlkit.fxml.FxmlView;

/**
 * Tier 3 Demo: Per-Instance Dependency Injection.
 *
 * <p>This View demonstrates the key Tier 3 concept: each instance
 * receives its own DiAdapter via constructor injection.
 *
 * <h2>Tier 2 vs Tier 3</h2>
 * <table border="1">
 *   <tr>
 *     <th>Tier 2</th>
 *     <th>Tier 3</th>
 *   </tr>
 *   <tr>
 *     <td>{@code new MyView()}</td>
 *     <td>{@code new MyView(diAdapter)}</td>
 *   </tr>
 *   <tr>
 *     <td>Global DiAdapter via FxmlKit.setDiAdapter()</td>
 *     <td>Per-instance DiAdapter via constructor</td>
 *   </tr>
 *   <tr>
 *     <td>Single-user desktop app</td>
 *     <td>Multi-user (JPro) or multi-session app</td>
 *   </tr>
 * </table>
 *
 * <h2>JPro Use Case</h2>
 * <p>In JPro, each browser session creates a new View with its own
 * DiAdapter containing user-specific services. This demo simulates
 * that with TabPane - each tab represents a different user session.
 *
 * @see SessionService
 */
public class UserSessionView extends FxmlView<UserSessionController> {

    /**
     * Creates a new UserSessionView with its own DiAdapter.
     *
     * <p>This is the Tier 3 pattern: the DiAdapter is passed in,
     * not retrieved from a global location.
     *
     * @param diAdapter the session-specific DiAdapter
     */
    public UserSessionView(DiAdapter diAdapter) {
        super(diAdapter);
    }
}