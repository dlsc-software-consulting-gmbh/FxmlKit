package com.dlsc.fxmlkit.hotreload;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for converting stylesheet URIs between different formats.
 *
 * <p>Handles conversion between classpath URIs, file URIs, and resource paths
 * to support CSS hot reload functionality.
 *
 * @see BuildSystem
 */
public final class StylesheetUriConverter {

    private static final Logger logger = Logger.getLogger(StylesheetUriConverter.class.getName());

    private StylesheetUriConverter() {
    }

    /**
     * Converts a stylesheet URI to a classpath-relative resource path.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code file:/path/target/classes/com/example/app.css} → {@code com/example/app.css}</li>
     *   <li>{@code jar:file:/path/app.jar!/com/example/app.css} → {@code com/example/app.css}</li>
     * </ul>
     *
     * @param uri the stylesheet URI
     * @return the resource path, or null if conversion fails
     */
    public static String toResourcePath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }

        try {
            // Strip query parameters (e.g., ?t=1234567890 for cache busting)
            String cleanUri = stripQueryParams(uri);

            // Handle file:// URIs
            if (cleanUri.startsWith("file:")) {
                return extractResourcePathFromFileUri(cleanUri);
            }

            // Handle jar:// URIs
            if (cleanUri.startsWith("jar:")) {
                int bangIndex = cleanUri.indexOf("!/");
                if (bangIndex >= 0) {
                    return cleanUri.substring(bangIndex + 2);
                }
            }

            // Handle classpath-relative paths (already resource path format)
            if (!cleanUri.contains(":") && !cleanUri.startsWith("/")) {
                return cleanUri;
            }

            return null;
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to convert URI to resource path: {0}", uri);
            return null;
        }
    }

    /**
     * Strips query parameters from a URI string.
     * Used to remove cache-busting timestamps like ?t=1234567890.
     *
     * @param uri the URI string
     * @return the URI without query parameters
     */
    private static String stripQueryParams(String uri) {
        int questionMarkIndex = uri.indexOf('?');
        return (questionMarkIndex >= 0) ? uri.substring(0, questionMarkIndex) : uri;
    }

    /**
     * Strips timestamp query parameter from URI.
     * Handles URIs like "file:///path/style.css?t=1234567890".
     *
     * <p>This is used for cache-busting: JavaFX's StyleManager caches stylesheets
     * by URI string, so we add timestamps to force reload. This method removes
     * old timestamps before adding new ones.
     *
     * @param uri the URI string (may contain ?t=timestamp)
     * @return the URI without timestamp parameter
     */
    public static String stripTimestamp(String uri) {
        if (uri == null) {
            return null;
        }
        int timestampIndex = uri.indexOf("?t=");
        return (timestampIndex >= 0) ? uri.substring(0, timestampIndex) : uri;
    }

    /**
     * Extracts the resource path from a file:// URI.
     */
    private static String extractResourcePathFromFileUri(String uri) {
        try {
            // Use URI class for proper parsing and decoding
            Path path = Path.of(URI.create(uri));
            String pathStr = path.toString().replace('\\', '/');

            // Use BuildSystem to extract resource path
            return BuildSystem.extractResourcePath(pathStr);
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to parse file URI: {0} - {1}",
                    new Object[]{uri, e.getMessage()});
            return null;
        }
    }

    /**
     * Finds the source file corresponding to a resource path.
     *
     * @param resourcePath the resource path (e.g., "com/example/app.css")
     * @param projectRoot  the project root directory
     * @return the source file path, or null if not found
     */
    public static Path findSourceFile(String resourcePath, Path projectRoot) {
        if (resourcePath == null || projectRoot == null) {
            return null;
        }

        // Try all source locations
        for (String sourceDir : BuildSystem.getSourceDirectories()) {
            Path sourcePath = projectRoot.resolve(sourceDir).resolve(resourcePath);
            if (Files.exists(sourcePath)) {
                return sourcePath;
            }
        }

        return null;
    }

    /**
     * Converts a classpath URI to a file:// URI pointing to the source file.
     *
     * <p>This is the key to making hot reload work - JavaFX caches stylesheets
     * by URI, so we need to switch from classpath to file:// to see changes.
     *
     * @param originalUri the original stylesheet URI
     * @param projectRoot the project root directory
     * @return the file:// URI, or null if source file not found
     */
    public static String toSourceFileUri(String originalUri, Path projectRoot) {
        if (projectRoot == null) {
            return null;
        }

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
     * Checks if a URI matches a given resource path.
     *
     * @param uri          the stylesheet URI
     * @param resourcePath the resource path to match
     * @return true if the URI corresponds to the resource path
     */
    public static boolean matchesResourcePath(String uri, String resourcePath) {
        if (uri == null || resourcePath == null) {
            return false;
        }

        String uriResourcePath = toResourcePath(uri);
        return resourcePath.equals(uriResourcePath);
    }

}