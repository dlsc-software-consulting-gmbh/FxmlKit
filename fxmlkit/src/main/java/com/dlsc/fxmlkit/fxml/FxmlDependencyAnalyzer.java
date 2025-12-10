package com.dlsc.fxmlkit.fxml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
 * <h2>Performance Consideration</h2>
 * <p>This class returns {@link URI} instead of {@link URL} because {@link URL#equals(Object)}
 * and {@link URL#hashCode()} perform DNS lookups, which can be orders of magnitude slower
 * than {@link URI}'s string-based comparison. This makes {@code Set<URI>} significantly
 * more efficient for collection operations.
 *
 * <p>Additionally, a fast string pre-check is performed before DOM parsing: if the FXML
 * content does not contain "fx:include", the expensive DOM parsing is skipped entirely.
 * This optimization significantly improves performance since most FXML files do not use
 * fx:include.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Recursive analysis of {@code <fx:include>} elements</li>
 *   <li>Circular dependency detection</li>
 *   <li>Efficient deduplication using {@link URI}</li>
 *   <li>Fast pre-check to skip unnecessary DOM parsing</li>
 *   <li>Thread-safe (stateless)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Set<URI> allFxmlUris = FxmlDependencyAnalyzer.findAllIncludedFxmls(rootUrl);
 *
 * for (URI uri : allFxmlUris) {
 *     URL url = uri.toURL();
 *     // ... use url for loading ...
 * }
 * }</pre>
 *
 * @see FxmlPathResolver
 * @see URI
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

    /**
     * Marker string to detect fx:include presence.
     */
    private static final String FX_INCLUDE_MARKER = "fx:include";

    private FxmlDependencyAnalyzer() {
    }

    /**
     * Finds all FXML files in the dependency tree (including the root).
     *
     * <p>This method recursively analyzes {@code <fx:include>} elements to discover
     * all nested FXML files. The result includes the root FXML and all its transitive
     * dependencies.
     *
     * <p>Circular dependencies are detected and logged as warnings.
     *
     * @param rootFxmlUrl the root FXML URL to analyze (must not be null)
     * @return set of all FXML URIs (including root), never null
     */
    public static Set<URI> findAllIncludedFxmls(URL rootFxmlUrl) {
        if (rootFxmlUrl == null) {
            logger.log(Level.WARNING, "Cannot analyze null FXML URL");
            return Collections.emptySet();
        }

        Set<URI> allFxmls = new LinkedHashSet<>();
        Set<URI> visited = new HashSet<>();

        try {
            URI rootUri = rootFxmlUrl.toURI();
            analyzeRecursive(rootUri, allFxmls, visited);

            logger.log(Level.FINE, "Found {0} FXML file(s) in dependency tree of: {1}",
                    new Object[]{allFxmls.size(), rootFxmlUrl});

        } catch (URISyntaxException e) {
            logger.log(Level.WARNING, "Invalid FXML URL, cannot convert to URI: " + rootFxmlUrl, e);
            return Collections.emptySet();
        }

        return allFxmls;
    }

    /**
     * Recursively analyzes FXML and collects all includes.
     *
     * <p>Uses two sets for tracking:
     * <ul>
     *   <li>{@code allFxmls}: All discovered FXML files (prevents redundant analysis)</li>
     *   <li>{@code visited}: Current recursion path (detects circular dependencies)</li>
     * </ul>
     *
     * <p>Performance optimization: Before DOM parsing, a fast string check is performed.
     * If the content does not contain "fx:include", DOM parsing is skipped entirely.
     *
     * @param fxmlUri  the FXML file URI
     * @param allFxmls collection of all discovered FXMLs
     * @param visited  tracking set for current recursion path
     */
    private static void analyzeRecursive(URI fxmlUri, Set<URI> allFxmls, Set<URI> visited) {
        // Circular dependency check
        if (visited.contains(fxmlUri)) {
            logger.log(Level.WARNING, "Circular dependency detected: {0}", fxmlUri);
            return;
        }

        // Skip if already analyzed
        if (allFxmls.contains(fxmlUri)) {
            logger.log(Level.FINEST, "Already analyzed, skipping: {0}", fxmlUri);
            return;
        }

        // Track this FXML in both sets
        visited.add(fxmlUri);
        allFxmls.add(fxmlUri);

        try {
            URL fxmlUrl = fxmlUri.toURL();

            // Single IO read - content will be reused for both pre-check and DOM parsing
            byte[] content = readContent(fxmlUrl);

            // Fast pre-check: skip DOM parsing if no fx:include present
            if (!containsFxInclude(content)) {
                logger.log(Level.FINEST, "No fx:include found, skipping DOM parse: {0}", fxmlUri);
                return;
            }

            // Has fx:include - perform DOM parsing (reuse already-read content)
            Document doc = parseXml(content);

            // Find all fx:include elements
            List<String> includePaths = findIncludePaths(doc);

            logger.log(Level.FINEST, "Found {0} include(s) in: {1}",
                    new Object[]{includePaths.size(), fxmlUri});

            // Recursively analyze each include
            for (String includePath : includePaths) {
                try {
                    URL includedUrl = new URL(fxmlUrl, includePath);
                    URI includedUri = includedUrl.toURI();

                    logger.log(Level.FINEST, "Resolving include: {0} -> {1}",
                            new Object[]{includePath, includedUri});

                    analyzeRecursive(includedUri, allFxmls, visited);

                } catch (Exception e) {
                    logger.log(Level.WARNING,
                            "Failed to resolve fx:include ''{0}'' in {1}: {2}",
                            new Object[]{includePath, fxmlUri, e.getMessage()});
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to analyze FXML: {0} - {1}",
                    new Object[]{fxmlUri, e.getMessage()});
        } finally {
            // Remove from current path
            visited.remove(fxmlUri);
        }
    }

    /**
     * Reads the entire content of a URL into a byte array.
     */
    private static byte[] readContent(URL url) throws Exception {
        try (InputStream input = url.openStream()) {
            return input.readAllBytes();
        }
    }

    /**
     * Fast check for fx:include presence in content.
     *
     * <p>This method performs a simple string search which is much faster than
     * DOM parsing. Most FXML files do not contain fx:include, so this optimization
     * skips expensive DOM parsing in the majority of cases.
     */
    private static boolean containsFxInclude(byte[] content) {
        // Convert to string for search
        // Note: FXML is typically UTF-8, but even if encoding differs,
        // "fx:include" as ASCII bytes will still be found
        String text = new String(content, StandardCharsets.UTF_8);
        return text.contains(FX_INCLUDE_MARKER);
    }

    /**
     * Finds all fx:include paths in the document.
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
     * Extracts source attributes from NodeList.
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
     * Parses XML document from byte array.
     *
     * <p>This method reuses already-read content to avoid duplicate IO operations.
     */
    private static Document parseXml(byte[] content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        // Security: enable secure processing (JDK built-in)
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        // Security: disable external entities
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        DocumentBuilder builder = factory.newDocumentBuilder();

        return builder.parse(new ByteArrayInputStream(content));
    }
}