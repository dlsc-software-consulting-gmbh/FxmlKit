package com.dlsc.fxmlkit.hotreload;

import javafx.util.Callback;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Built-in source path converters for common build systems.
 *
 * <p>Provides converters for:
 * <ul>
 *   <li><b>Maven</b> - {@code target/classes} → {@code src/main/resources}</li>
 *   <li><b>Gradle</b> - {@code build/classes/java/main} → {@code src/main/resources}</li>
 *   <li><b>IntelliJ IDEA</b> - {@code out/production/classes} → {@code src/main/resources}</li>
 * </ul>
 *
 * <h2>How It Works</h2>
 * <p>Each converter performs simple string replacement on the URI, then checks
 * if the resulting file exists. This approach:
 * <ul>
 *   <li>Requires no project root detection</li>
 *   <li>Works with multi-module projects automatically</li>
 *   <li>Is stateless and thread-safe</li>
 * </ul>
 *
 * <h2>Extension</h2>
 * <p>For non-standard project layouts, register a custom converter:
 * <pre>{@code
 * FxmlKit.addSourcePathConverter(uri -> {
 *     if (uri.contains("my-output")) {
 *         String sourceUri = uri.replace("my-output", "my-source");
 *         Path path = Path.of(URI.create(sourceUri));
 *         return Files.exists(path) ? path : null;
 *     }
 *     return null;
 * });
 * }</pre>
 */
public final class SourcePathConverters {

    private static final Logger logger = Logger.getLogger(SourcePathConverters.class.getName());

    private SourcePathConverters() {
        // Utility class
    }

    /**
     * Build output directory markers.
     * Used to extract resource paths from compiled class locations.
     */
    private static final String[] OUTPUT_MARKERS = {
            // Maven
            "target/classes/",
            "target/test-classes/",
            // Gradle Java
            "build/classes/java/main/",
            "build/classes/java/test/",
            "build/resources/main/",
            "build/resources/test/",
            // Gradle Kotlin
            "build/classes/kotlin/main/",
            "build/classes/kotlin/test/",
            // IntelliJ IDEA
            "out/production/classes/",
            "out/production/resources/",
            "out/test/classes/",
            "out/test/resources/",
    };

    /**
     * Source directory markers.
     * Used to extract resource paths from source file locations.
     */
    private static final String[] SOURCE_MARKERS = {
            "src/main/resources/",
            "src/main/java/",
            "src/main/kotlin/",
            "src/test/resources/",
            "src/test/java/",
            "src/test/kotlin/",
    };

    /**
     * Maven project converter.
     *
     * <p>Converts:
     * <ul>
     *   <li>{@code target/classes} → {@code src/main/resources}, {@code src/main/java}</li>
     *   <li>{@code target/test-classes} → {@code src/test/resources}, {@code src/test/java}</li>
     * </ul>
     */
    public static final Callback<String, Path> MAVEN = uri -> {
        if (uri == null || !uri.startsWith("file:")) {
            return null;
        }

        return tryConvert(uri, new String[][]{
                {"target/classes", "src/main/resources"},
                {"target/classes", "src/main/java"},
                {"target/test-classes", "src/test/resources"},
                {"target/test-classes", "src/test/java"}
        });
    };

    /**
     * Gradle project converter.
     *
     * <p>Converts:
     * <ul>
     *   <li>{@code build/resources/main} → {@code src/main/resources}</li>
     *   <li>{@code build/classes/java/main} → {@code src/main/resources}, {@code src/main/java}</li>
     *   <li>{@code build/classes/kotlin/main} → {@code src/main/resources}, {@code src/main/kotlin}</li>
     *   <li>Similar for test directories</li>
     * </ul>
     */
    public static final Callback<String, Path> GRADLE = uri -> {
        if (uri == null || !uri.startsWith("file:")) {
            return null;
        }

        return tryConvert(uri, new String[][]{
                {"build/resources/main", "src/main/resources"},
                {"build/classes/java/main", "src/main/resources"},
                {"build/classes/java/main", "src/main/java"},
                {"build/classes/kotlin/main", "src/main/resources"},
                {"build/classes/kotlin/main", "src/main/kotlin"},
                {"build/resources/test", "src/test/resources"},
                {"build/classes/java/test", "src/test/resources"},
                {"build/classes/java/test", "src/test/java"},
                {"build/classes/kotlin/test", "src/test/kotlin"}
        });
    };

    /**
     * IntelliJ IDEA project converter.
     *
     * <p>Converts:
     * <ul>
     *   <li>{@code out/production/resources} → {@code src/main/resources}</li>
     *   <li>{@code out/production/classes} → {@code src/main/resources}, {@code src/main/java}</li>
     *   <li>Similar for test directories</li>
     * </ul>
     */
    public static final Callback<String, Path> INTELLIJ = uri -> {
        if (uri == null || !uri.startsWith("file:")) {
            return null;
        }

        return tryConvert(uri, new String[][]{
                {"out/production/resources", "src/main/resources"},
                {"out/production/classes", "src/main/resources"},
                {"out/production/classes", "src/main/java"},
                {"out/test/resources", "src/test/resources"},
                {"out/test/classes", "src/test/resources"},
                {"out/test/classes", "src/test/java"}
        });
    };

    /**
     * Plain IntelliJ IDEA project converter (non-Maven/Gradle).
     *
     * <p>Converts:
     * <ul>
     *   <li>{@code out/production/{ModuleName}/} → {@code resources/}, {@code src/}</li>
     *   <li>{@code out/test/{ModuleName}/} → {@code resources/}, {@code src/}</li>
     * </ul>
     *
     * <p>Priority: {@code resources/} is tried before {@code src/}, following
     * the convention that resource files (FXML, CSS) should be in a separate
     * resources directory.
     *
     * <h2>Supported Project Structures</h2>
     * <pre>
     * Project with separate resources:
     *   out/production/MyModule/com/example/View.fxml
     *   → resources/com/example/View.fxml ✓
     *
     * Project with mixed src:
     *   out/production/MyModule/com/example/View.fxml
     *   → src/com/example/View.fxml ✓
     * </pre>
     *
     * <p>Note: This converter handles plain IntelliJ IDEA projects that don't use
     * Maven or Gradle. For Maven/Gradle projects run within IDEA, use {@link #INTELLIJ}.
     *
     * @see #INTELLIJ
     */
    public static final Callback<String, Path> INTELLIJ_PLAIN = uri -> {
        if (uri == null || !uri.startsWith("file:")) {
            return null;
        }
        String[] markers = {"out/production/", "out/test/"};
        for (String marker : markers) {
            int markerIndex = uri.indexOf(marker);
            if (markerIndex < 0) continue;
            String prefix = uri.substring(0, markerIndex);
            String afterMarker = uri.substring(markerIndex + marker.length());
            int slashIndex = afterMarker.indexOf('/');
            if (slashIndex < 0) continue;
            String resourcePath = afterMarker.substring(slashIndex + 1);
            String[] sourceDirs = {"resources/", "src/"};
            for (String sourceDir : sourceDirs) {
                String sourceUri = prefix + sourceDir + resourcePath;
                try {
                    Path sourcePath = Paths.get(new URI(sourceUri));
                    if (Files.exists(sourcePath)) {
                        logger.log(Level.FINE, "Plain IDEA: {0} → {1}",
                                new Object[]{uri, sourcePath});
                        return sourcePath;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    };

    /**
     * Plain Eclipse project converter (non-Maven/Gradle).
     */
    public static final Callback<String, Path> ECLIPSE = uri -> {
        if (uri == null || !uri.startsWith("file:")) {
            return null;
        }

        String marker = "/bin/";
        int markerIndex = uri.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }

        // prefix: "file:/C:/Users/.../HelloFxmlKit/"
        String prefix = uri.substring(0, markerIndex + 1);
        // resourcePath: "com/abc/Hello.css"
        String resourcePath = uri.substring(markerIndex + marker.length());

        String[] sourceDirs = {"resources/", "src/"};
        for (String sourceDir : sourceDirs) {
            String sourceUri = prefix + sourceDir + resourcePath;
            try {
                Path sourcePath = Paths.get(new URI(sourceUri));
                if (Files.exists(sourcePath)) {
                    logger.log(Level.FINE, "Eclipse: {0} → {1}",
                            new Object[]{uri, sourcePath});
                    return sourcePath;
                }
            } catch (Exception ignored) {}
        }
        return null;
    };

    /**
     * Plain NetBeans project converter (non-Maven/Gradle).
     */
    public static final Callback<String, Path> NETBEANS = uri -> {
        if (uri == null || !uri.startsWith("file:")) {
            return null;
        }

        // exclude Gradle paths
        if (uri.contains("build/classes/java/") || uri.contains("build/classes/kotlin/")) {
            return null;
        }

        String marker = "/build/classes/";
        int markerIndex = uri.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }

        // prefix: "file:/C:/Users/.../HelloFxmlKit/"
        String prefix = uri.substring(0, markerIndex + 1);
        // resourcePath: "com/abc/Hello.css"
        String resourcePath = uri.substring(markerIndex + marker.length());

        String[] sourceDirs = {"resources/", "src/"};
        for (String sourceDir : sourceDirs) {
            String sourceUri = prefix + sourceDir + resourcePath;
            try {
                Path sourcePath = Paths.get(new URI(sourceUri));
                if (Files.exists(sourcePath)) {
                    logger.log(Level.FINE, "NetBeans: {0} → {1}",
                            new Object[]{uri, sourcePath});
                    return sourcePath;
                }
            } catch (Exception ignored) {}
        }
        return null;
    };
    /**
     * Default converters (tried in order: Maven, Gradle, IntelliJ).
     */
    public static final List<Callback<String, Path>> DEFAULTS = List.of(
            MAVEN, GRADLE, INTELLIJ, INTELLIJ_PLAIN, ECLIPSE, NETBEANS
    );

    /**
     * Extracts the resource path from a file system path.
     *
     * <p>This method looks for common build output and source directory markers
     * and extracts the resource-relative path. For example:
     * <ul>
     *   <li>{@code /project/target/classes/com/example/View.fxml} → {@code com/example/View.fxml}</li>
     *   <li>{@code /project/src/main/resources/com/example/style.css} → {@code com/example/style.css}</li>
     * </ul>
     *
     * <p>This method is used by both HotReloadManager and GlobalCssMonitor
     * to normalize paths for comparison and tracking.
     *
     * @param filePath the file system path (not a URI)
     * @return the resource path, or null if no marker found
     */
    public static String extractResourcePath(String filePath) {
        if (filePath == null) {
            return null;
        }

        // Check output directory markers first
        for (String marker : OUTPUT_MARKERS) {
            int index = filePath.indexOf(marker);
            if (index >= 0) {
                return filePath.substring(index + marker.length());
            }
        }

        // Then check source directory markers
        for (String marker : SOURCE_MARKERS) {
            int index = filePath.indexOf(marker);
            if (index >= 0) {
                return filePath.substring(index + marker.length());
            }
        }

        return null;
    }

    /**
     * Creates a converter that chains multiple converters.
     *
     * <p>Returns the first successful conversion result.
     *
     * @param converters the converters to chain
     * @return a combined converter
     */
    @SafeVarargs
    public static Callback<String, Path> chain(Callback<String, Path>... converters) {
        return uri -> {
            for (Callback<String, Path> converter : converters) {
                Path result = converter.call(uri);
                if (result != null) {
                    return result;
                }
            }
            return null;
        };
    }

    /**
     * Returns a converter that tries all default converters.
     *
     * @return the default converter chain
     */
    public static Callback<String, Path> defaults() {
        return uri -> {
            for (Callback<String, Path> converter : DEFAULTS) {
                Path result = converter.call(uri);
                if (result != null) {
                    return result;
                }
            }
            return null;
        };
    }

    /**
     * Tries multiple path replacements and returns the first existing path.
     *
     * @param uri          the URI to convert
     * @param replacements array of [from, to] replacement pairs
     * @return the source path if found, null otherwise
     */
    private static Path tryConvert(String uri, String[][] replacements) {
        for (String[] pair : replacements) {
            String from = pair[0];
            String to = pair[1];

            if (uri.contains(from)) {
                String sourceUri = uri.replace(from, to);
                try {
                    Path sourcePath = Paths.get(new URI(sourceUri));
                    if (Files.exists(sourcePath)) {
                        logger.log(Level.FINE, "Converted: {0} → {1}",
                                new Object[]{uri, sourcePath});
                        return sourcePath;
                    }
                } catch (Exception e) {
                    logger.log(Level.FINE, "Conversion failed for {0}: {1}",
                            new Object[]{uri, e.getMessage()});
                }
            }
        }
        return null;
    }
}