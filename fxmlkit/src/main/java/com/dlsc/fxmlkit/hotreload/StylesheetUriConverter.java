package com.dlsc.fxmlkit.hotreload;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility for converting CSS URI formats to resource paths and source file paths.
 *
 * <p>Supported URI formats:
 * <ul>
 *   <li>{@code file:///path/to/target/classes/com/example/app.css}</li>
 *   <li>{@code jar:file:///path/to/app.jar!/com/example/app.css}</li>
 *   <li>{@code com/example/app.css} (classpath relative)</li>
 *   <li>{@code /com/example/app.css} (classpath absolute)</li>
 * </ul>
 *
 * <p>Supported build systems: Maven, Gradle, IntelliJ IDEA.
 *
 * <p>Thread-safe.
 *
 * @see HotReloadManager
 */
public final class StylesheetUriConverter {

    private static final Logger logger = Logger.getLogger(StylesheetUriConverter.class.getName());

    private StylesheetUriConverter() {
    }

    /**
     * Converts a stylesheet URI to a classpath-relative resource path.
     *
     * <p>This method normalizes various URI formats to a consistent resource path format
     * that can be used for matching file changes.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code file:///project/target/classes/com/example/app.css} → {@code com/example/app.css}</li>
     *   <li>{@code jar:file:///path/app.jar!/com/example/app.css} → {@code com/example/app.css}</li>
     *   <li>{@code /com/example/app.css} → {@code com/example/app.css}</li>
     * </ul>
     *
     * @param uri the stylesheet URI (may be null)
     * @return the resource path, or null if conversion fails
     */
    public static String toResourcePath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }

        try {
            // Handle file:// URI
            if (uri.startsWith("file:")) {
                return extractResourcePathFromFileUri(uri);
            }

            // Handle jar:// URI
            if (uri.startsWith("jar:")) {
                return extractResourcePathFromJarUri(uri);
            }

            // Handle @ prefix (FXML relative path) - already converted by JavaFX
            if (uri.startsWith("@")) {
                logger.log(Level.FINE, "Relative path URI not supported for global monitoring: {0}", uri);
                return null;
            }

            // Handle classpath paths (with or without leading slash)
            String path = uri;
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            // Remove query parameters if present
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) {
                path = path.substring(0, queryIndex);
            }

            return path;

        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to convert URI to resource path: {0}", uri);
            return null;
        }
    }

    /**
     * Extracts the resource path from a file:// URI.
     *
     * <p>Handles various build system output directories:
     * <ul>
     *   <li>Maven: target/classes/, target/test-classes/</li>
     *   <li>Gradle: build/resources/main/, build/classes/java/main/</li>
     *   <li>IntelliJ: out/production/resources/, out/production/classes/,
     *       out/test/resources/, out/test/classes/</li>
     *   <li>Source: src/main/resources/, src/test/resources/</li>
     * </ul>
     */
    private static String extractResourcePathFromFileUri(String uri) {
        try {
            Path path = Path.of(new URI(uri));
            String pathStr = path.toString().replace('\\', '/');

            // Maven: target/classes/
            int idx = pathStr.indexOf("/target/classes/");
            if (idx >= 0) {
                return pathStr.substring(idx + "/target/classes/".length());
            }

            // Maven: target/test-classes/
            idx = pathStr.indexOf("/target/test-classes/");
            if (idx >= 0) {
                return pathStr.substring(idx + "/target/test-classes/".length());
            }

            // Gradle: build/resources/main/
            idx = pathStr.indexOf("/build/resources/main/");
            if (idx >= 0) {
                return pathStr.substring(idx + "/build/resources/main/".length());
            }

            // Gradle: build/resources/test/
            idx = pathStr.indexOf("/build/resources/test/");
            if (idx >= 0) {
                return pathStr.substring(idx + "/build/resources/test/".length());
            }

            // Gradle: build/classes/java/main/
            idx = pathStr.indexOf("/build/classes/java/main/");
            if (idx >= 0) {
                return pathStr.substring(idx + "/build/classes/java/main/".length());
            }

            // Gradle: build/classes/java/test/
            idx = pathStr.indexOf("/build/classes/java/test/");
            if (idx >= 0) {
                return pathStr.substring(idx + "/build/classes/java/test/".length());
            }

            // IntelliJ: out/production/resources/
            idx = pathStr.indexOf("/out/production/resources/");
            if (idx >= 0) {
                return pathStr.substring(idx + "/out/production/resources/".length());
            }

            // IntelliJ: out/production/classes/
            idx = pathStr.indexOf("/out/production/classes/");
            if (idx >= 0) {
                return pathStr.substring(idx + "/out/production/classes/".length());
            }

            // IntelliJ: out/test/resources/
            idx = pathStr.indexOf("/out/test/resources/");
            if (idx >= 0) {
                return pathStr.substring(idx + "/out/test/resources/".length());
            }

            // IntelliJ: out/test/classes/
            idx = pathStr.indexOf("/out/test/classes/");
            if (idx >= 0) {
                return pathStr.substring(idx + "/out/test/classes/".length());
            }

            // Source directory: src/main/resources/
            idx = pathStr.indexOf("/src/main/resources/");
            if (idx >= 0) {
                return pathStr.substring(idx + "/src/main/resources/".length());
            }

            // Source directory: src/test/resources/
            idx = pathStr.indexOf("/src/test/resources/");
            if (idx >= 0) {
                return pathStr.substring(idx + "/src/test/resources/".length());
            }

            logger.log(Level.FINE, "Could not determine resource path from file URI: {0}", uri);
            return null;

        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to parse file URI: {0}", uri);
            return null;
        }
    }

    /**
     * Extracts the resource path from a jar:// URI.
     *
     * <p>Format: {@code jar:file:///path/to/app.jar!/com/example/app.css}
     */
    private static String extractResourcePathFromJarUri(String uri) {
        int bangIndex = uri.indexOf("!/");
        if (bangIndex >= 0 && bangIndex + 2 < uri.length()) {
            return uri.substring(bangIndex + 2);
        }
        return null;
    }

    /**
     * Finds the source file path for a given resource path.
     *
     * <p>Searches for the source file in standard source directories relative to
     * the project root.
     *
     * @param resourcePath the classpath-relative resource path (e.g., "com/example/app.css")
     * @param projectRoot  the project root directory
     * @return the source file path, or null if not found
     */
    public static Path findSourceFile(String resourcePath, Path projectRoot) {
        if (resourcePath == null || projectRoot == null) {
            return null;
        }

        // Standard source directories to search
        String[] sourceDirs = {
                "src/main/resources",
                "src/main/java",
                "src/test/resources",
                "src/test/java"
        };

        for (String sourceDir : sourceDirs) {
            Path candidate = projectRoot.resolve(sourceDir).resolve(resourcePath);
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                logger.log(Level.FINE, "Found source file: {0}", candidate);
                return candidate;
            }
        }

        logger.log(Level.FINE, "Source file not found for: {0}", resourcePath);
        return null;
    }

    /**
     * Converts a stylesheet URI to a file:// URI pointing to the source file.
     *
     * <p>Replaces the classpath URI with a file:// URI, bypassing JavaFX
     * stylesheet cache.
     *
     * @param originalUri the original stylesheet URI
     * @param projectRoot the project root directory
     * @return the file:// URI string, or null if source file not found
     */
    public static String toSourceFileUri(String originalUri, Path projectRoot) {
        String resourcePath = toResourcePath(originalUri);
        if (resourcePath == null) {
            return null;
        }

        Path sourceFile = findSourceFile(resourcePath, projectRoot);
        if (sourceFile != null) {
            return sourceFile.toUri().toString();
        }

        return null;
    }

    /**
     * Checks if a stylesheet URI matches a given resource path.
     *
     * <p>Performs exact match after normalizing the URI to a resource path.
     * Filename-only matching is not performed.
     *
     * @param stylesheetUri  the stylesheet URI
     * @param resourcePath   the resource path to match
     * @return true if they refer to the same resource
     */
    public static boolean matchesResourcePath(String stylesheetUri, String resourcePath) {
        if (stylesheetUri == null || resourcePath == null) {
            return false;
        }

        String uriResourcePath = toResourcePath(stylesheetUri);
        if (uriResourcePath == null) {
            return false;
        }

        // Exact match only - no filename fallback to avoid false positives
        return uriResourcePath.equals(resourcePath);
    }

    /**
     * Removes query parameters from a URI string.
     *
     * @param uri the URI string
     * @return the URI without query parameters
     */
    public static String removeQueryString(String uri) {
        if (uri == null) {
            return null;
        }
        int queryIndex = uri.indexOf('?');
        return (queryIndex >= 0) ? uri.substring(0, queryIndex) : uri;
    }

    /**
     * Infers the project root directory from a target/build directory path.
     *
     * @param targetDir a path within the target/build directory
     * @return the project root, or null if cannot be determined
     */
    public static Path inferProjectRoot(Path targetDir) {
        if (targetDir == null) {
            return null;
        }

        String pathStr = targetDir.toString().replace('\\', '/');

        // Maven: target/classes -> project root
        int idx = pathStr.indexOf("/target/classes");
        if (idx >= 0) {
            return Path.of(pathStr.substring(0, idx));
        }

        idx = pathStr.indexOf("/target/test-classes");
        if (idx >= 0) {
            return Path.of(pathStr.substring(0, idx));
        }

        // Gradle: build/resources/main -> project root
        idx = pathStr.indexOf("/build/resources/main");
        if (idx >= 0) {
            return Path.of(pathStr.substring(0, idx));
        }

        idx = pathStr.indexOf("/build/classes/java/main");
        if (idx >= 0) {
            return Path.of(pathStr.substring(0, idx));
        }

        // IntelliJ: out/production -> project root
        idx = pathStr.indexOf("/out/production");
        if (idx >= 0) {
            return Path.of(pathStr.substring(0, idx));
        }

        // IntelliJ: out/test -> project root
        idx = pathStr.indexOf("/out/test");
        if (idx >= 0) {
            return Path.of(pathStr.substring(0, idx));
        }

        return null;
    }
}