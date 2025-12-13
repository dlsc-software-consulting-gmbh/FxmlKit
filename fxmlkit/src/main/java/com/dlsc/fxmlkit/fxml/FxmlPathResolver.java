package com.dlsc.fxmlkit.fxml;

import com.dlsc.fxmlkit.annotations.FxmlPath;
import com.dlsc.fxmlkit.hotreload.HotReloadManager;
import javafx.scene.Parent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified FXML and stylesheet path resolution utility for FxmlKit framework.
 *
 * <p>This class provides consistent resolution strategies for:
 * <ul>
 *   <li>FXML files (@FxmlPath annotation, exact match, suffix stripping)</li>
 *   <li>Stylesheets (.bss/.css files relative to FXML location)</li>
 * </ul>
 *
 * <h2>FXML Resolution Algorithm</h2>
 * <p>For a given class, FXML files are resolved in this order:
 * <ol>
 *   <li><b>@FxmlPath annotation:</b> If present, use the specified path directly</li>
 *   <li><b>Exact match:</b> Try {@code ClassName.fxml}</li>
 *   <li><b>Suffix stripping:</b> Strip suffix and try {@code BaseName.fxml}
 *       (ViewProvider > Provider > View)</li>
 * </ol>
 *
 * <h2>Stylesheet Resolution</h2>
 * <p>Stylesheets are resolved relative to the FXML file location, not the Java class.
 * This allows flexible organization of FXML and CSS resources independent of Java code structure.
 *
 * <p>Search order:
 * <ol>
 *   <li>{@code BaseName.bss} (binary stylesheet - higher priority)</li>
 *   <li>{@code BaseName.css} (text stylesheet - fallback)</li>
 * </ol>
 *
 * <h2>Automatic Stylesheet Attachment</h2>
 * <p>The {@link #autoAttachStylesheets(Parent, URL)} method automatically attaches
 * the stylesheet for the root FXML only. For {@code <fx:include>} nested FXMLs,
 * stylesheets should be declared explicitly in the child FXML to ensure proper
 * CSS scope isolation.
 *
 * <h2>Thread Safety</h2>
 * <p>This class is stateless and thread-safe. All methods can be called concurrently.
 *
 * @see FxmlPath
 * @see FxmlView
 * @see FxmlViewProvider
 * @see FxmlKitLoader
 */
public final class FxmlPathResolver {

    private static final Logger logger = Logger.getLogger(FxmlPathResolver.class.getName());

    /**
     * Suffix stripping order: longest to shortest to avoid ambiguity.
     */
    private static final String[] SUFFIXES_ORDERED = {
            "ViewProvider",  // Must be first (longest)
            "Provider",
            "View"
    };

    /**
     * Private constructor to prevent instantiation.
     */
    private FxmlPathResolver() {
    }

    /**
     * Resolves the FXML file URL for a given class.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>@FxmlPath annotation (if present)</li>
     *   <li>ClassName.fxml (exact match)</li>
     *   <li>BaseName.fxml (after stripping suffix)</li>
     * </ol>
     *
     * @param ownerClass the class to resolve FXML for (must not be null)
     * @return the resolved FXML URL (never null)
     * @throws IllegalArgumentException if FXML file cannot be found
     */
    public static URL resolveFxmlUrl(Class<?> ownerClass) {
        FxmlPath annotation = findFxmlPathAnnotation(ownerClass);
        if (annotation != null) {
            return resolveFromAnnotation(ownerClass, annotation);
        }

        return resolveDefault(ownerClass);
    }

    /**
     * Resolves FXML URL from @FxmlPath annotation.
     */
    private static URL resolveFromAnnotation(Class<?> ownerClass, FxmlPath annotation) {
        String path = annotation.value();

        logger.log(Level.FINE, "Found @FxmlPath annotation on {0}: {1}",
                new Object[]{ownerClass.getSimpleName(), path});

        URL url = ownerClass.getResource(path);

        if (url != null) {
            logger.log(Level.FINE, "Resolved FXML from @FxmlPath: {0}", url);
            return url;
        }

        throw new IllegalArgumentException(
                "Cannot find FXML file specified in @FxmlPath annotation.\n" +
                        "  Class: " + ownerClass.getName() + "\n" +
                        "  Annotated path: " + path + "\n" +
                        "  Tip: Ensure the path is correct and the resource exists.\n" +
                        "       - Relative paths (no leading /): relative to class package\n" +
                        "       - Absolute paths (leading /): relative to classpath root"
        );
    }

    /**
     * Resolves FXML URL using default resolution (exact match + suffix stripping).
     */
    private static URL resolveDefault(Class<?> ownerClass) {
        String simpleName = ownerClass.getSimpleName();

        checkNamingConvention(ownerClass, simpleName);

        // Try exact match first
        String exactPath = simpleName + ".fxml";
        URL url = ownerClass.getResource(exactPath);
        if (url != null) {
            logger.log(Level.FINE, "Resolved FXML (exact match): {0} → {1}",
                    new Object[]{exactPath, url});
            return url;
        }

        // Try suffix stripping
        String baseName = stripKnownSuffix(simpleName);
        String conventionalPath = baseName + ".fxml";
        url = ownerClass.getResource(conventionalPath);
        if (url != null) {
            logger.log(Level.FINE, "Resolved FXML (suffix stripped): {0} → {1}",
                    new Object[]{conventionalPath, url});
            return url;
        }

        throw new IllegalArgumentException(
                "Cannot find FXML file for " + ownerClass.getName() + ".\n" +
                        "  Tried:\n" +
                        "    1. " + exactPath + " (exact class name)\n" +
                        "    2. " + conventionalPath + " (suffix stripped)\n" +
                        "  \n" +
                        "  Solutions:\n" +
                        "    - Place " + conventionalPath + " next to your class\n" +
                        "    - Use @FxmlPath(\"YourFile.fxml\") to specify custom path\n" +
                        "  \n" +
                        "  Examples:\n" +
                        "    @FxmlPath(\"../fxml/Home.fxml\")      // Parent directory\n" +
                        "    @FxmlPath(\"/fxml/Home.fxml\")        // Absolute from classpath root"
        );
    }

    /**
     * Auto-attaches stylesheets to a Parent node for the root FXML only.
     *
     * <p>This method attaches the stylesheet for the root FXML to the root node.
     * For {@code <fx:include>} nested FXMLs, stylesheets should be declared
     * explicitly in the child FXML using {@code stylesheets="@ChildName.css"}
     * to ensure proper CSS scope isolation.
     *
     * <p><b>Why only root FXML?</b>
     * <ul>
     *   <li>CSS added to root affects ALL descendants, including fx:include children</li>
     *   <li>If child FXML's CSS is added to root, it may pollute parent's styles</li>
     *   <li>Example: Both parent and child have {@code .title-label} - adding child's
     *       CSS to root would override parent's styling</li>
     * </ul>
     *
     * <p><b>Recommended Pattern for fx:include:</b>
     * <pre>{@code
     * <!-- Child.fxml - declare stylesheet explicitly -->
     * <VBox stylesheets="@Child.css" ...>
     *     ...
     * </VBox>
     * }</pre>
     *
     * @param root    the root node to attach stylesheets to (must not be null)
     * @param fxmlUrl the root FXML file URL
     * @return true if a stylesheet was attached, false otherwise
     */
    public static boolean autoAttachStylesheets(Parent root, URL fxmlUrl) {
        if (root == null) {
            logger.log(Level.WARNING, "Cannot attach stylesheets: root node is null");
            return false;
        }

        if (fxmlUrl == null) {
            logger.log(Level.FINE, "Cannot attach stylesheets: FXML URL is null");
            return false;
        }

        // Only attach stylesheet for the root FXML to avoid style pollution
        // fx:include children should declare their own stylesheets in their FXML files
        return autoAttachStylesheetForSingleFxml(root, fxmlUrl);
    }

    /**
     * Auto-attaches stylesheet for a single FXML file with optimization.
     */
    private static boolean autoAttachStylesheetForSingleFxml(Parent root, URL fxmlUrl) {
        if (root == null || fxmlUrl == null) {
            return false;
        }

        String baseName = extractBaseName(fxmlUrl);
        if (baseName == null) {
            return false;
        }

        // Optimization: Check if same-name stylesheet already declared
        if (isStylesheetAlreadyDeclared(root, baseName)) {
            logger.log(Level.FINE,
                    "Stylesheet already declared for ''{0}'', skipping auto-attachment",
                    baseName);
            return false;
        }

        // Try .bss first, then .css
        URL bss = resolveStylesheet(fxmlUrl, baseName + ".bss");
        URL css = (bss == null) ? resolveStylesheet(fxmlUrl, baseName + ".css") : null;

        URL stylesheet = (bss != null) ? bss : css;
        if (stylesheet == null) {
            logger.log(Level.FINE, "No stylesheet (.bss/.css) found for: {0}", baseName);
            return false;
        }

        // In development mode, use source URI to load the latest file
        // (avoids ClassLoader cache returning stale content)
        String uri = stylesheet.toExternalForm();
        if (FxmlKit.isCssHotReloadEnabled()) {
            Path sourcePath = HotReloadManager.getInstance().toSourcePath(uri);
            if (sourcePath != null) {
                uri = sourcePath.toUri().toString();
            }
        }

        if (!root.getStylesheets().contains(uri)) {
            root.getStylesheets().add(uri);
            logger.log(Level.FINE, "Auto-attached stylesheet: {0}", uri);
            return true;
        }

        return false;
    }

    /**
     * Checks if a same-name stylesheet is already declared in the root node.
     *
     * <p><b>Simple Rule:</b> Only checks filename, not path. If root declares
     * {@code Main.css} or {@code Main.bss} (in any path), we skip auto-attachment
     * for {@code Main.fxml}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code Main.css} → matches</li>
     *   <li>{@code Main.bss} → matches</li>
     *   <li>{@code css/Main.css} → matches (we only check filename)</li>
     *   <li>{@code theme.css} → no match</li>
     * </ul>
     *
     * @param root     the root node to check
     * @param baseName the FXML base name (without extension, e.g., "Main")
     * @return true if same-name stylesheet is declared
     */
    private static boolean isStylesheetAlreadyDeclared(Parent root, String baseName) {
        String cssFileName = baseName + ".css";
        String bssFileName = baseName + ".bss";

        for (String stylesheetUrl : root.getStylesheets()) {
            // Extract filename from URL
            String fileName = extractFileNameFromUrl(stylesheetUrl);

            // Check if filename matches (ignoring path)
            if (cssFileName.equalsIgnoreCase(fileName) ||
                    bssFileName.equalsIgnoreCase(fileName)) {

                logger.log(Level.FINEST,
                        "Found declared stylesheet ''{0}'' matching base name ''{1}''",
                        new Object[]{fileName, baseName});
                return true;
            }
        }

        return false;
    }

    /**
     * Extracts filename from a stylesheet URL.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code file:///path/to/Main.css} → {@code Main.css}</li>
     *   <li>{@code css/Main.css} → {@code Main.css}</li>
     *   <li>{@code Main.css} → {@code Main.css}</li>
     * </ul>
     */
    private static String extractFileNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }

        // Find last separator (/ or \)
        int lastSlash = Math.max(url.lastIndexOf('/'), url.lastIndexOf('\\'));

        if (lastSlash >= 0) {
            return url.substring(lastSlash + 1);
        }

        return url;
    }

    /**
     * Resolves a stylesheet URL relative to the FXML URL.
     *
     * <p>This method creates a URL for the stylesheet in the same directory
     * as the FXML file and verifies that it exists.
     *
     * @param fxmlUrl        the FXML file URL (must not be null)
     * @param stylesheetName the stylesheet file name (e.g., "Login.css")
     * @return the stylesheet URL, or null if it doesn't exist
     */
    public static URL resolveStylesheet(URL fxmlUrl, String stylesheetName) {
        if (fxmlUrl == null || stylesheetName == null) {
            return null;
        }

        try {
            URL stylesheetUrl = new URL(fxmlUrl, stylesheetName);
            if (urlExists(stylesheetUrl)) {
                logger.log(Level.FINEST, "Found stylesheet: {0}", stylesheetUrl);
                return stylesheetUrl;
            }

            logger.log(Level.FINEST, "Stylesheet not found: {0}", stylesheetUrl);
            return null;
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Failed to resolve stylesheet {0} relative to {1}: {2}",
                    new Object[]{stylesheetName, fxmlUrl, e.getMessage()});
            return null;
        }
    }

    /**
     * Checks if a URL exists with minimal I/O overhead.
     */
    private static boolean urlExists(URL url) {
        String protocol = url.getProtocol();

        // Fast path: file:// protocol (no I/O)
        if ("file".equals(protocol)) {
            try {
                File file = new File(url.toURI());
                return file.exists() && file.isFile();
            } catch (Exception e) {
                return false;
            }
        }

        // Minimal I/O: open stream and check for IOException
        try (InputStream stream = url.openStream()) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Extracts the base name from an FXML URL.
     *
     * <p>Example: {@code .../view/Login.fxml} → {@code "Login"}
     *
     * @param fxmlUrl the FXML URL
     * @return the base name (without .fxml extension), or null if cannot be determined
     */
    private static String extractBaseName(URL fxmlUrl) {
        if (fxmlUrl == null) {
            return null;
        }

        String fxmlPath = fxmlUrl.getPath();
        int slash = fxmlPath.lastIndexOf('/');
        String fileName = (slash >= 0) ? fxmlPath.substring(slash + 1) : fxmlPath;

        if (fileName.isEmpty()) {
            logger.log(Level.FINE, "Cannot derive base name from FXML URL: {0}", fxmlUrl);
            return null;
        }

        return fileName.endsWith(".fxml")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
    }

    /**
     * Strips known suffixes from class name with priority order.
     *
     * <p>Suffixes are tried longest first to prevent ambiguity.
     * For example, "HomeViewProvider" strips "ViewProvider", not "Provider".
     */
    private static String stripKnownSuffix(String simpleName) {
        for (String suffix : SUFFIXES_ORDERED) {
            if (simpleName.endsWith(suffix) && simpleName.length() > suffix.length()) {
                String baseName = simpleName.substring(0, simpleName.length() - suffix.length());
                logger.log(Level.FINE, "Stripped suffix ''{0}'' from ''{1}'' → ''{2}''",
                        new Object[]{suffix, simpleName, baseName});
                return baseName;
            }
        }

        logger.log(Level.FINE, "No suffix to strip from ''{0}''", simpleName);
        return simpleName;
    }

    /**
     * Finds @FxmlPath annotation on class or its superclasses.
     */
    private static FxmlPath findFxmlPathAnnotation(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            FxmlPath annotation = current.getAnnotation(FxmlPath.class);
            if (annotation != null) {
                return annotation;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Checks naming conventions and logs warnings if violated.
     */
    private static void checkNamingConvention(Class<?> ownerClass, String simpleName) {
        if (FxmlViewProvider.class.isAssignableFrom(ownerClass)) {
            if (simpleName.endsWith("View") && !simpleName.endsWith("ViewProvider")) {
                logger.log(Level.WARNING,
                        "Naming Convention: ''{0}'' extends FxmlViewProvider but uses ''View'' suffix. " +
                                "Consider using ''Provider'' or ''ViewProvider'' suffix for clarity " +
                                "(e.g., ''{1}Provider'' or ''{1}ViewProvider'')",
                        new Object[]{simpleName, stripSuffix(simpleName, "View")});
            }
        }

        if (FxmlView.class.isAssignableFrom(ownerClass)
                && !FxmlViewProvider.class.isAssignableFrom(ownerClass)) {
            if (simpleName.endsWith("Provider")) {
                logger.log(Level.WARNING,
                        "Naming Convention: ''{0}'' extends FxmlView but uses ''Provider'' suffix. " +
                                "Consider using ''View'' suffix for clarity (e.g., ''{1}View'')",
                        new Object[]{simpleName, stripSuffix(simpleName, "Provider")});
            }
        }
    }

    /**
     * Helper to strip a specific suffix.
     */
    private static String stripSuffix(String name, String suffix) {
        if (name.endsWith(suffix) && name.length() > suffix.length()) {
            return name.substring(0, name.length() - suffix.length());
        }
        return name;
    }

    /**
     * Converts a URL to a classpath-relative resource path.
     */
    public static String urlToResourcePath(URL url) {
        if (url == null) {
            return null;
        }

        String path = url.getPath();

        // Handle JAR URLs
        int bangIndex = path.indexOf("!/");
        if (bangIndex >= 0) {
            path = path.substring(bangIndex + 2);
        }

        // Remove /classes/ prefix (Maven target/classes)
        int classesIndex = path.indexOf("/classes/");
        if (classesIndex >= 0) {
            return path.substring(classesIndex + "/classes/".length());
        }

        // Remove /build/classes/java/main/ prefix (Gradle)
        int gradleIndex = path.indexOf("/build/classes/java/main/");
        if (gradleIndex >= 0) {
            return path.substring(gradleIndex + "/build/classes/java/main/".length());
        }

        // Remove /build/resources/main/ prefix (Gradle)
        int resourcesIndex = path.indexOf("/build/resources/main/");
        if (resourcesIndex >= 0) {
            return path.substring(resourcesIndex + "/build/resources/main/".length());
        }

        // Remove leading slash
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        return path;
    }
}