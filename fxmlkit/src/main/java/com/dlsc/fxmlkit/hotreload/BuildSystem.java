package com.dlsc.fxmlkit.hotreload;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Enumeration of supported build systems and their directory conventions.
 *
 * <p>This enum centralizes all build system path configurations to ensure
 * consistency across HotReloadManager and StylesheetUriConverter.
 *
 * <p>Each enum constant represents a specific output directory type and
 * defines the mapping between output directories and source directories.
 *
 * <p>Supported build systems:
 * <ul>
 *   <li>Maven - target/classes, target/test-classes</li>
 *   <li>Gradle - build/classes/java/*, build/resources/*</li>
 *   <li>IntelliJ IDEA - out/production/*, out/test/*</li>
 * </ul>
 */
public enum BuildSystem {

    // ==================== Maven ====================

    /**
     * Maven main classes output directory.
     */
    MAVEN_MAIN(
            "/target/classes/",
            "src/main/resources",
            "target/classes",
            false
    ),

    /**
     * Maven test classes output directory.
     */
    MAVEN_TEST(
            "/target/test-classes/",
            "src/test/resources",
            "target/test-classes",
            true
    ),

    // ==================== Gradle Java ====================

    /**
     * Gradle Java main classes output directory.
     */
    GRADLE_JAVA_MAIN(
            "/build/classes/java/main/",
            "src/main/resources",
            "build/resources/main",
            false
    ),

    /**
     * Gradle Java test classes output directory.
     */
    GRADLE_JAVA_TEST(
            "/build/classes/java/test/",
            "src/test/resources",
            "build/resources/test",
            true
    ),

    // ==================== Gradle Kotlin ====================

    /**
     * Gradle Kotlin main classes output directory.
     */
    GRADLE_KOTLIN_MAIN(
            "/build/classes/kotlin/main/",
            "src/main/resources",
            "build/resources/main",
            false
    ),

    /**
     * Gradle Kotlin test classes output directory.
     */
    GRADLE_KOTLIN_TEST(
            "/build/classes/kotlin/test/",
            "src/test/resources",
            "build/resources/test",
            true
    ),

    // ==================== Gradle Resources ====================

    /**
     * Gradle main resources output directory.
     */
    GRADLE_RESOURCES_MAIN(
            "/build/resources/main/",
            "src/main/resources",
            "build/resources/main",
            false
    ),

    /**
     * Gradle test resources output directory.
     */
    GRADLE_RESOURCES_TEST(
            "/build/resources/test/",
            "src/test/resources",
            "build/resources/test",
            true
    ),

    // ==================== IntelliJ IDEA ====================

    /**
     * IntelliJ IDEA production classes output directory.
     */
    IDEA_PRODUCTION_CLASSES(
            "/out/production/classes/",
            "src/main/resources",
            "out/production/resources",
            false
    ),

    /**
     * IntelliJ IDEA production resources output directory.
     */
    IDEA_PRODUCTION_RESOURCES(
            "/out/production/resources/",
            "src/main/resources",
            "out/production/resources",
            false
    ),

    /**
     * IntelliJ IDEA test classes output directory.
     */
    IDEA_TEST_CLASSES(
            "/out/test/classes/",
            "src/test/resources",
            "out/test/resources",
            true
    ),

    /**
     * IntelliJ IDEA test resources output directory.
     */
    IDEA_TEST_RESOURCES(
            "/out/test/resources/",
            "src/test/resources",
            "out/test/resources",
            true
    );

    // ==================== Source Directory Markers ====================

    /**
     * All recognized source directory markers.
     */
    private static final String[] SOURCE_MARKERS = {
            "/src/main/resources/",
            "/src/test/resources/",
            "/src/main/java/",
            "/src/test/java/",
            "/src/main/kotlin/",
            "/src/test/kotlin/"
    };

    // ==================== Instance Fields ====================

    /**
     * Marker string to identify this output directory in a path.
     * Used for pattern matching (e.g., "/target/classes/").
     */
    private final String outputMarker;

    /**
     * Relative path from project root to source directory.
     * Used when inferring source from output (e.g., "src/main/resources").
     */
    private final String sourceDir;

    /**
     * Relative path from project root to output directory for resources.
     * Used when syncing source to output (e.g., "target/classes").
     */
    private final String outputDir;

    /**
     * Whether this is a test configuration.
     */
    private final boolean test;

    // ==================== Constructor ====================

    BuildSystem(String outputMarker, String sourceDir, String outputDir, boolean test) {
        this.outputMarker = outputMarker;
        this.sourceDir = sourceDir;
        this.outputDir = outputDir;
        this.test = test;
    }

    // ==================== Getters ====================

    /**
     * Returns the marker string used to identify this output directory.
     *
     * @return the output marker (e.g., "/target/classes/")
     */
    public String getOutputMarker() {
        return outputMarker;
    }

    /**
     * Returns the relative path to the source directory.
     *
     * @return the source directory path (e.g., "src/main/resources")
     */
    public String getSourceDir() {
        return sourceDir;
    }

    /**
     * Returns the relative path to the output directory.
     *
     * @return the output directory path (e.g., "target/classes")
     */
    public String getOutputDir() {
        return outputDir;
    }

    /**
     * Returns whether this is a test configuration.
     *
     * @return true if this is a test configuration
     */
    public boolean isTest() {
        return test;
    }

    // ==================== Static Methods ====================

    /**
     * Gets source directory paths without leading/trailing slashes.
     * Useful for path construction (e.g., projectRoot.resolve(sourceDir)).
     *
     * @return array of source directory paths
     */
    public static String[] getSourceDirectories() {
        String[] dirs = new String[SOURCE_MARKERS.length];
        for (int i = 0; i < SOURCE_MARKERS.length; i++) {
            // "/src/main/resources/" → "src/main/resources"
            String marker = SOURCE_MARKERS[i];
            dirs[i] = marker.substring(1, marker.length() - 1);
        }
        return dirs;
    }

    /**
     * Finds the build system from an output path.
     *
     * @param path the file path to check
     * @return the matching BuildSystem, or null if not found
     */
    public static BuildSystem fromOutputPath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');

        // Ensure path ends with / for matching (outputMarker all end with /)
        String normalizedWithSlash = normalized.endsWith("/") ? normalized : normalized + "/";

        for (BuildSystem bs : values()) {
            if (normalizedWithSlash.contains(bs.outputMarker)) {
                return bs;
            }
        }
        return null;
    }

    /**
     * Checks if a path is a source directory.
     *
     * @param path the path to check
     * @return true if the path contains a source directory marker
     */
    public static boolean isSourcePath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/');

        // Ensure path ends with / for matching (SOURCE_MARKERS all end with /)
        String normalizedWithSlash = normalized.endsWith("/") ? normalized : normalized + "/";

        for (String marker : SOURCE_MARKERS) {
            if (normalizedWithSlash.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a path is a test source directory.
     *
     * @param path the path to check
     * @return true if the path is a test source directory
     */
    public static boolean isTestSourcePath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        return normalized.contains("/src/test/");
    }

    /**
     * Extracts the project root from an output path.
     *
     * @param path the output path
     * @return the project root path, or null if not found
     */
    public static String extractProjectRoot(String path) {
        BuildSystem bs = fromOutputPath(path);
        if (bs == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');

        // Ensure path ends with / for matching (outputMarker all end with /)
        String normalizedWithSlash = normalized.endsWith("/") ? normalized : normalized + "/";

        int idx = normalizedWithSlash.indexOf(bs.outputMarker);
        if (idx >= 0) {
            return normalizedWithSlash.substring(0, idx);
        }
        return null;
    }

    /**
     * Extracts the resource path (classpath-relative) from a full path.
     *
     * @param path the full file path
     * @return the resource path, or null if not found
     */
    public static String extractResourcePath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');

        // Try output markers first
        for (BuildSystem bs : values()) {
            int idx = normalized.indexOf(bs.outputMarker);
            if (idx >= 0) {
                return normalized.substring(idx + bs.outputMarker.length());
            }
        }

        // Try source markers
        for (String marker : SOURCE_MARKERS) {
            int idx = normalized.indexOf(marker);
            if (idx >= 0) {
                return normalized.substring(idx + marker.length());
            }
        }

        return null;
    }

    /**
     * Extracts the project root from a source path.
     *
     * @param path the source path
     * @return the project root, or null if not found
     */
    public static String extractProjectRootFromSource(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');

        // Ensure path ends with / for matching (SOURCE_MARKERS all end with /)
        String normalizedWithSlash = normalized.endsWith("/") ? normalized : normalized + "/";

        for (String marker : SOURCE_MARKERS) {
            int idx = normalizedWithSlash.indexOf(marker);
            if (idx >= 0) {
                return normalizedWithSlash.substring(0, idx);
            }
        }
        return null;
    }

    /**
     * Infers the source directory from an output directory.
     *
     * @param outputPath the output directory path
     * @return the source directory path, or null if cannot infer
     */
    public static Path inferSourceDirectory(Path outputPath) {
        if (outputPath == null) {
            return null;
        }
        String projectRoot = extractProjectRoot(outputPath.toString());
        BuildSystem bs = fromOutputPath(outputPath.toString());

        if (projectRoot != null && bs != null) {
            return Path.of(projectRoot, bs.sourceDir);
        }
        return null;
    }

    /**
     * Infers the output directory from a source directory.
     * Checks which build system is actually in use.
     *
     * @param sourcePath the source directory path
     * @return the output directory path, or null if cannot infer
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

        // Check Gradle first (more specific paths)
        Path gradleOutput = isTest
                ? project.resolve("build/resources/test")
                : project.resolve("build/resources/main");
        if (Files.exists(gradleOutput)) {
            return gradleOutput;
        }

        // Check Maven
        Path mavenOutput = isTest
                ? project.resolve("target/test-classes")
                : project.resolve("target/classes");
        if (Files.exists(mavenOutput)) {
            return mavenOutput;
        }

        // Check IntelliJ IDEA
        Path ideaOutput = isTest
                ? project.resolve("out/test/resources")
                : project.resolve("out/production/resources");
        if (Files.exists(ideaOutput)) {
            return ideaOutput;
        }

        // No output directory found
        // Caller should handle null (e.g., skip sync operation)
        return null;
    }

    /**
     * Finds the output root directory from a file path within an output directory.
     *
     * @param filePath the file path within an output directory
     * @return the output root directory, or null if not found
     */
    public static Path findOutputRoot(Path filePath) {
        if (filePath == null) {
            return null;
        }

        String pathStr = filePath.toString().replace('\\', '/');
        BuildSystem bs = fromOutputPath(pathStr);

        if (bs != null) {
            // Ensure path ends with / for consistent matching
            String pathStrWithSlash = pathStr.endsWith("/") ? pathStr : pathStr + "/";
            int idx = pathStrWithSlash.indexOf(bs.outputMarker);
            if (idx >= 0) {
                // Return path up to and including the marker (minus trailing /)
                return Path.of(pathStrWithSlash.substring(0, idx + bs.outputMarker.length() - 1));
            }
        }

        return null;
    }

    /**
     * Infers the project root from an output directory.
     *
     * @param outputDir the output directory
     * @return the project root, or null if cannot infer
     */
    public static Path inferProjectRoot(Path outputDir) {
        String projectRoot = extractProjectRoot(outputDir.toString());
        return projectRoot != null ? Path.of(projectRoot) : null;
    }
}