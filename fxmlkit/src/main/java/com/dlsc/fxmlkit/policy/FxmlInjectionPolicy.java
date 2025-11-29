package com.dlsc.fxmlkit.policy;

/**
 * Policy for determining which FXML-created nodes receive dependency injection.
 *
 * <p>This enum controls injection behavior for objects created during FXML parsing.
 * Controllers are always injected regardless of policy (in Tier 2/3 modes).
 *
 * <h2>Policy Overview</h2>
 * <table border="1">
 *   <tr>
 *     <th>Policy</th>
 *     <th>Description</th>
 *     <th>Performance</th>
 *     <th>Use Case</th>
 *   </tr>
 *   <tr>
 *     <td>EXPLICIT_ONLY</td>
 *     <td>Only inject explicitly marked nodes</td>
 *     <td>Best</td>
 *     <td>Production, precise control</td>
 *   </tr>
 *   <tr>
 *     <td>AUTO</td>
 *     <td>Auto-detect nodes with injection points</td>
 *     <td>Good</td>
 *     <td>Development, rapid prototyping</td>
 *   </tr>
 *   <tr>
 *     <td>DISABLED</td>
 *     <td>Disable all node injection</td>
 *     <td>Best</td>
 *     <td>Simple apps, maximum performance</td>
 *   </tr>
 * </table>
 *
 * <h2>Three-Tier Interaction</h2>
 * <ul>
 *   <li><b>Tier 1 (Zero-Config):</b> Policy is ignored (no injection occurs)</li>
 *   <li><b>Tier 2 (Global DI):</b> Policy controls which nodes receive injection</li>
 *   <li><b>Tier 3 (Isolated DI):</b> Policy controls which nodes receive injection</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Production: Explicit control
 * FxmlKit.setNodeInjectionPolicy(FxmlInjectionPolicy.EXPLICIT_ONLY);
 *
 * @FxmlObject
 * public class CustomButton extends Button {
 *     @Inject private StyleService styleService;
 * }
 *
 * // Development: Automatic detection
 * FxmlKit.setNodeInjectionPolicy(FxmlInjectionPolicy.AUTO);
 *
 * public class CustomButton extends Button {
 *     @Inject private StyleService styleService;  // Auto-detected
 * }
 *
 * // Simple apps: Disable node injection
 * FxmlKit.setNodeInjectionPolicy(FxmlInjectionPolicy.DISABLED);
 * // Only controllers are injected
 * }</pre>
 */
public enum FxmlInjectionPolicy {

    /**
     * Only inject nodes explicitly marked with @FxmlObject.
     *
     * <p>This is the default and most secure policy. Only nodes with @FxmlObject
     * annotation or in the include list receive injection.
     *
     * <p>Injection Rules:
     * <ul>
     *   <li>Nodes with @FxmlObject annotation: injected</li>
     *   <li>Nodes in include list: injected</li>
     *   <li>All other nodes: not injected (even with @Inject fields)</li>
     * </ul>
     *
     * <p>Advantages:
     * <ul>
     *   <li>Explicit control over injection</li>
     *   <li>Best performance (minimal reflection)</li>
     *   <li>Easy to audit injected nodes</li>
     *   <li>Prevents accidental third-party control injection</li>
     * </ul>
     */
    EXPLICIT_ONLY,

    /**
     * Automatically inject nodes with injection points.
     *
     * <p>Nodes are injected if they have any of:
     * <ul>
     *   <li>@Inject fields</li>
     *   <li>@Inject methods</li>
     *   <li>@Inject constructors</li>
     *   <li>Constructors with parameters</li>
     *   <li>@FxmlObject annotation</li>
     *   <li>Present in include list</li>
     * </ul>
     *
     * <p>Detection Process:
     * <ol>
     *   <li>Check exclude list (skip if matched)</li>
     *   <li>Check @SkipInjection (skip if present)</li>
     *   <li>Check include list or @FxmlObject (inject if matched)</li>
     *   <li>Scan for injection points via reflection</li>
     *   <li>Inject if any injection points found</li>
     * </ol>
     *
     * <p>Advantages:
     * <ul>
     *   <li>No need to annotate every custom control</li>
     *   <li>Works naturally with standard @Inject</li>
     *   <li>Good for rapid development</li>
     *   <li>Intuitive behavior</li>
     * </ul>
     *
     * <p>Disadvantages:
     * <ul>
     *   <li>Higher reflection overhead during loading</li>
     *   <li>May inject third-party controls unintentionally</li>
     *   <li>Less explicit</li>
     * </ul>
     */
    AUTO,

    /**
     * Disable all node injection, only inject controllers.
     *
     * <p>This policy provides maximum performance by bypassing node injection
     * logic entirely. Only FXML controllers receive dependency injection.
     *
     * <p>Injection Rules:
     * <ul>
     *   <li>FXML controller: always injected</li>
     *   <li>All FXML-created nodes: never injected</li>
     *   <li>Include/exclude lists: ignored</li>
     *   <li>@FxmlObject: ignored</li>
     * </ul>
     *
     * <p>Advantages:
     * <ul>
     *   <li>Maximum performance (zero node processing)</li>
     *   <li>Simplest and most predictable</li>
     *   <li>Minimal framework footprint</li>
     *   <li>No risk of unintended injection</li>
     * </ul>
     *
     * <p>Use When:
     * <ul>
     *   <li>Simple applications without custom controls</li>
     *   <li>All business logic in controllers</li>
     *   <li>Custom controls don't need DI</li>
     *   <li>Maximum performance required</li>
     * </ul>
     */
    DISABLED
}