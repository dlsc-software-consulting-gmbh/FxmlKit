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

    // ==================== Built-in Converters ====================

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
     * Default converters (tried in order: Maven, Gradle, IntelliJ).
     */
    public static final List<Callback<String, Path>> DEFAULTS = List.of(
            MAVEN, GRADLE, INTELLIJ
    );

    // ==================== Utility Methods ====================

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

    // ==================== Internal Methods ====================

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