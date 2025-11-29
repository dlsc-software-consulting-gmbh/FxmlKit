package com.dlsc.fxmlkit.fxml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Analyzer for FXML dependencies (fx:include elements).
 *
 * <p>This class recursively analyzes FXML files to discover all nested includes,
 * enabling automatic stylesheet attachment for the entire FXML hierarchy.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Recursive analysis of {@code <fx:include>} elements</li>
 *   <li>Circular dependency detection</li>
 *   <li>Minimal overhead (uses standard DOM parser)</li>
 *   <li>Thread-safe (stateless)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * URL mainFxml = getClass().getResource("Main.fxml");
 * Set<URL> allFxmls = FxmlDependencyAnalyzer.findAllIncludedFxmls(mainFxml);
 *
 * // allFxmls contains:
 * // - Main.fxml
 * // - ChildA.fxml (included by Main)
 * // - ChildA1.fxml (included by ChildA)
 * }</pre>
 */
public final class FxmlDependencyAnalyzer {

    private static final Logger logger = Logger.getLogger(FxmlDependencyAnalyzer.class.getName());

    /**
     * FXML namespace URI.
     */
    private static final String FXML_NAMESPACE = "http://javafx.com/fxml/1";

    /**
     * Alternative FXML namespace (older versions).
     */
    private static final String FXML_NAMESPACE_ALT = "http://javafx.com/fxml";

    private FxmlDependencyAnalyzer() {
    }

    /**
     * Finds all FXML files in the dependency tree (including the root).
     *
     * <p>This method recursively analyzes {@code <fx:include>} elements to discover
     * all nested FXML files. The result includes the root FXML and all its transitive
     * dependencies.
     *
     * <p><b>Circular dependencies:</b> Detected and logged as warnings. Each FXML
     * is visited only once.
     *
     * @param rootFxmlUrl the root FXML URL to analyze (must not be null)
     * @return set of all FXML URLs (including root), never null
     */
    public static Set<URL> findAllIncludedFxmls(URL rootFxmlUrl) {
        if (rootFxmlUrl == null) {
            logger.log(Level.WARNING, "Cannot analyze null FXML URL");
            return Collections.emptySet();
        }

        Set<URL> allFxmls = new LinkedHashSet<>();
        Set<URL> visited = new HashSet<>();

        analyzeRecursive(rootFxmlUrl, allFxmls, visited);

        logger.log(Level.FINE, "Found {0} FXML file(s) in dependency tree of: {1}",
                new Object[]{allFxmls.size(), rootFxmlUrl});

        return allFxmls;
    }

    /**
     * Recursively analyze FXML and collect all includes.
     */
    private static void analyzeRecursive(URL fxmlUrl, Set<URL> allFxmls, Set<URL> visited) {
        // Add current FXML
        allFxmls.add(fxmlUrl);

        // Check for circular dependency
        if (visited.contains(fxmlUrl)) {
            logger.log(Level.WARNING, "Circular dependency detected: {0}", fxmlUrl);
            return;
        }
        visited.add(fxmlUrl);

        try {
            // Parse FXML
            Document doc = parseXml(fxmlUrl);

            // Find all fx:include elements
            List<String> includePaths = findIncludePaths(doc);

            logger.log(Level.FINEST, "Found {0} include(s) in: {1}",
                    new Object[]{includePaths.size(), fxmlUrl});

            // Recursively analyze each include
            for (String includePath : includePaths) {
                try {
                    URL includedUrl = new URL(fxmlUrl, includePath);

                    logger.log(Level.FINEST, "Resolving include: {0} -> {1}",
                            new Object[]{includePath, includedUrl});

                    analyzeRecursive(includedUrl, allFxmls, visited);

                } catch (Exception e) {
                    logger.log(Level.WARNING,
                            "Failed to resolve fx:include ''{0}'' in {1}: {2}",
                            new Object[]{includePath, fxmlUrl, e.getMessage()});
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to analyze FXML: {0} - {1}",
                    new Object[]{fxmlUrl, e.getMessage()});
        } finally {
            // This allows the same FXML to appear in different branches
            visited.remove(fxmlUrl);
        }
    }

    /**
     * Find all fx:include paths in the document.
     */
    private static List<String> findIncludePaths(Document doc) {
        List<String> paths = new ArrayList<>();

        // Try both namespace variants
        NodeList includes1 = doc.getElementsByTagNameNS(FXML_NAMESPACE, "include");
        NodeList includes2 = doc.getElementsByTagNameNS(FXML_NAMESPACE_ALT, "include");

        // Also try without namespace (for lenient parsing)
        NodeList includes3 = doc.getElementsByTagName("fx:include");

        extractPaths(includes1, paths);
        extractPaths(includes2, paths);
        extractPaths(includes3, paths);

        return paths;
    }

    /**
     * Extract source attributes from NodeList.
     */
    private static void extractPaths(NodeList includes, List<String> paths) {
        for (int i = 0; i < includes.getLength(); i++) {
            Element include = (Element) includes.item(i);
            // If source attribute is missing, this will return empty string
            String source = include.getAttribute("source");

            if (!source.isEmpty()) {
                paths.add(source);
            }
        }
    }

    /**
     * Parse XML document from URL.
     */
    private static Document parseXml(URL url) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        // Security: disable external entities
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        DocumentBuilder builder = factory.newDocumentBuilder();

        try (InputStream input = url.openStream()) {
            return builder.parse(input);
        }
    }
}