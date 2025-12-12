package com.dlsc.fxmlkit.hotreload;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for resolving project paths in different build systems.
 *
 * <p>This class provides methods to:
 * <ul>
 *   <li>Extract project root from output/source paths</li>
 *   <li>Extract resource paths from full file paths</li>
 *   <li>Infer source/output directory mappings</li>
 *   <li>Detect project structure</li>
 * </ul>
 *
 * <p>Methods are organized into two categories:
 * <ul>
 *   <li><b>Pure operations:</b> String/Path manipulation only, no I/O</li>
 *   <li><b>I/O operations:</b> Access file system via {@link Files}</li>
 * </ul>
 *
 * @see BuildSystem
 */
public final class ProjectPathResolver {

    private ProjectPathResolver() {
        // Utility class
    }

    // ==================== Pure Operations (no I/O) ====================

    /**
     * Extracts the project root from an output path.
     *
     * <p><b>Pure:</b> String operations only, no I/O.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code /project/target/classes/com/example/App.class} → {@code /project}</li>
     *   <li>{@code /project/build/classes/java/main/com/example/App.class} → {@code /project}</li>
     * </ul>
     *
     * @param path the output path (may use forward or back slashes)
     * @return the project root path, or null if not recognized
     */
    public static String extractProjectRoot(String path) {
        BuildSystem bs = BuildSystem.fromOutputPath(path);
        if (bs == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');

        // Ensure path ends with / for matching (outputMarker ends with /)
        String normalizedWithSlash = normalized.endsWith("/") ? normalized : normalized + "/";

        int idx = normalizedWithSlash.indexOf(bs.getOutputMarker());
        if (idx >= 0) {
            return normalizedWithSlash.substring(0, idx);
        }
        return null;
    }

    /**
     * Extracts the project root from a source path.
     *
     * <p><b>Pure:</b> String operations only, no I/O.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code /project/src/main/resources/app.css} → {@code /project}</li>
     *   <li>{@code /project/src/main/java/com/example/App.java} → {@code /project}</li>
     * </ul>
     *
     * @param path the source path
     * @return the project root, or null if not recognized
     */
    public static String extractProjectRootFromSource(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');

        // Ensure path ends with / for matching
        String normalizedWithSlash = normalized.endsWith("/") ? normalized : normalized + "/";

        for (String marker : BuildSystem.getSourceMarkers()) {
            int idx = normalizedWithSlash.indexOf(marker);
            if (idx >= 0) {
                return normalizedWithSlash.substring(0, idx);
            }
        }
        return null;
    }

    /**
     * Extracts the resource path (classpath-relative) from a full path.
     *
     * <p><b>Pure:</b> String operations only, no I/O.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code /project/target/classes/com/example/app.css} → {@code com/example/app.css}</li>
     *   <li>{@code /project/src/main/resources/com/example/app.css} → {@code com/example/app.css}</li>
     * </ul>
     *
     * @param path the full file path
     * @return the resource path, or null if not recognized
     */
    public static String extractResourcePath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');

        // Try output markers first
        for (BuildSystem bs : BuildSystem.values()) {
            int idx = normalized.indexOf(bs.getOutputMarker());
            if (idx >= 0) {
                return normalized.substring(idx + bs.getOutputMarker().length());
            }
        }

        // Try source markers
        for (String marker : BuildSystem.getSourceMarkers()) {
            int idx = normalized.indexOf(marker);
            if (idx >= 0) {
                return normalized.substring(idx + marker.length());
            }
        }

        return null;
    }

    /**
     * Checks if a path is within a source directory.
     *
     * <p><b>Pure:</b> String operations only, no I/O.
     *
     * @param path the path to check
     * @return true if the path contains a source directory marker
     */
    public static boolean isSourcePath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/');

        // Ensure path ends with / for matching
        String normalizedWithSlash = normalized.endsWith("/") ? normalized : normalized + "/";

        for (String marker : BuildSystem.getSourceMarkers()) {
            if (normalizedWithSlash.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a path is within a test source directory.
     *
     * <p><b>Pure:</b> String operations only, no I/O.
     *
     * @param path the path to check
     * @return true if the path is in a test source directory
     */
    public static boolean isTestSourcePath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        return normalized.contains("/src/test/");
    }

    /**
     * Finds the output root directory from a file path within an output directory.
     *
     * <p><b>Pure:</b> Path operations only, no I/O.
     *
     * <p>Example:
     * <ul>
     *   <li>{@code /project/target/classes/com/example/app.css} → {@code /project/target/classes}</li>
     * </ul>
     *
     * @param filePath the file path within an output directory
     * @return the output root directory, or null if not recognized
     */
    public static Path findOutputRoot(Path filePath) {
        if (filePath == null) {
            return null;
        }

        String pathStr = filePath.toString().replace('\\', '/');
        BuildSystem bs = BuildSystem.fromOutputPath(pathStr);

        if (bs != null) {
            // Ensure path ends with / for consistent matching
            String pathStrWithSlash = pathStr.endsWith("/") ? pathStr : pathStr + "/";
            int idx = pathStrWithSlash.indexOf(bs.getOutputMarker());
            if (idx >= 0) {
                // Return path up to and including the marker (minus trailing /)
                return Path.of(pathStrWithSlash.substring(0, idx + bs.getOutputMarker().length() - 1));
            }
        }

        return null;
    }

    /**
     * Infers the project root from an output directory.
     *
     * <p><b>Pure:</b> Path operations only, no I/O.
     *
     * @param outputDir the output directory (e.g., target/classes)
     * @return the project root, or null if cannot infer
     */
    public static Path inferProjectRoot(Path outputDir) {
        if (outputDir == null) {
            return null;
        }
        String projectRoot = extractProjectRoot(outputDir.toString());
        return projectRoot != null ? Path.of(projectRoot) : null;
    }

    // ==================== I/O Operations (access file system) ====================

    /**
     * Infers the source directory from an output directory.
     *
     * <p><b>I/O:</b> This method does NOT access file system directly,
     * but the returned path should be verified with {@link Files#exists}.
     *
     * @param outputPath the output directory path
     * @return the source directory path, or null if cannot infer
     */
    public static Path inferSourceDirectory(Path outputPath) {
        if (outputPath == null) {
            return null;
        }
        String projectRoot = extractProjectRoot(outputPath.toString());
        BuildSystem bs = BuildSystem.fromOutputPath(outputPath.toString());

        if (projectRoot != null && bs != null) {
            return Path.of(projectRoot, bs.getSourceDir());
        }
        return null;
    }

    /**
     * Infers the output directory from a source directory.
     *
     * <p><b>I/O:</b> Accesses file system via {@link Files#exists} to detect
     * which build system is in use.
     *
     * @param sourcePath the source directory path
     * @return the output directory path, or null if cannot infer or not found
     */
    public static Path inferOutputDirectory(Path sourcePath) {
        if (sourcePath == null) {
            return null;
        }

        String projectRoot = extractProjectRootFromSource(sourcePath.toString());
        if (projectRoot == null) {
            return null;
        }

        boolean isTest = isTestSourcePath(sourcePath.toString());
        Path project = Path.of(projectRoot);

        // Check each build system's output directory
        for (String outputPath : BuildSystem.getOutputPaths(isTest)) {
            Path output = project.resolve(outputPath);
            if (Files.exists(output)) {
                return output;
            }
        }

        return null;
    }

    /**
     * Finds the first existing output directory for a project.
     *
     * <p><b>I/O:</b> Accesses file system via {@link Files#exists}.
     *
     * @param projectRoot the project root directory
     * @return the first existing output directory, or null if none found
     */
    public static Path findExistingOutputDirectory(Path projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        for (String outputPath : BuildSystem.getMainOutputPaths()) {
            Path dir = projectRoot.resolve(outputPath);
            if (Files.exists(dir)) {
                return dir;
            }
        }
        return null;
    }

    /**
     * Checks if a directory looks like a project root.
     *
     * <p><b>I/O:</b> Accesses file system via {@link Files#exists} and
     * {@link Files#isDirectory}.
     *
     * <p>A directory is considered a project root if it contains:
     * <ul>
     *   <li>A build file (pom.xml, build.gradle, build.gradle.kts), or</li>
     *   <li>A source directory (src/main/resources, src/main/java, src/main/kotlin)</li>
     * </ul>
     *
     * @param dir the directory to check
     * @return true if the directory looks like a project root
     */
    public static boolean looksLikeProjectRoot(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }

        // Check for build marker files
        for (String marker : BuildSystem.getProjectMarkerFiles()) {
            if (Files.exists(dir.resolve(marker))) {
                return true;
            }
        }

        // Check for source directories
        for (String sourceDir : BuildSystem.getMainSourcePaths()) {
            if (Files.exists(dir.resolve(sourceDir))) {
                return true;
            }
        }

        return false;
    }
}