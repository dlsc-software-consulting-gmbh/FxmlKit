package com.dlsc.fxmlkit.hotreload;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Enumeration of supported build systems and their directory conventions.
 *
 * <p>This enum defines the configuration for each build system:
 * <ul>
 *   <li>Output directory markers for path matching</li>
 *   <li>Source directory mappings</li>
 *   <li>Test vs main configuration distinction</li>
 * </ul>
 *
 * <p>Supported build systems:
 * <ul>
 *   <li><b>Maven</b> - target/classes, target/test-classes</li>
 *   <li><b>Gradle</b> - build/classes/java/*, build/resources/*</li>
 *   <li><b>IntelliJ IDEA</b> - out/production/*, out/test/*</li>
 * </ul>
 *
 * <p>For path resolution operations, see {@link ProjectPathResolver}.
 *
 * @see ProjectPathResolver
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

    // ==================== Static Configuration ====================

    /**
     * All recognized source directory markers (with leading and trailing slashes).
     */
    private static final String[] SOURCE_MARKERS = {
            "/src/main/resources/",
            "/src/test/resources/",
            "/src/main/java/",
            "/src/test/java/",
            "/src/main/kotlin/",
            "/src/test/kotlin/"
    };

    /**
     * Main source directory paths (without slashes).
     */
    private static final String[] MAIN_SOURCE_PATHS = {
            "src/main/resources",
            "src/main/java",
            "src/main/kotlin"
    };

    /**
     * Project marker files that indicate a project root.
     */
    private static final String[] PROJECT_MARKER_FILES = {
            "pom.xml",
            "build.gradle",
            "build.gradle.kts"
    };

    // ==================== Instance Fields ====================

    /**
     * Marker string to identify this output directory in a path.
     * Includes leading and trailing slashes (e.g., "/target/classes/").
     */
    private final String outputMarker;

    /**
     * Relative path from project root to source directory.
     * No leading/trailing slashes (e.g., "src/main/resources").
     */
    private final String sourceDir;

    /**
     * Relative path from project root to output directory.
     * No leading/trailing slashes (e.g., "target/classes").
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

    // ==================== Instance Getters ====================

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

    // ==================== Static Query Methods ====================

    /**
     * Finds the matching build system from an output path.
     *
     * @param path the file path to check (may use forward or back slashes)
     * @return the matching BuildSystem, or null if not recognized
     */
    public static BuildSystem fromOutputPath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');

        // Ensure path ends with / for matching (outputMarker ends with /)
        String normalizedWithSlash = normalized.endsWith("/") ? normalized : normalized + "/";

        for (BuildSystem bs : values()) {
            if (normalizedWithSlash.contains(bs.outputMarker)) {
                return bs;
            }
        }
        return null;
    }

    /**
     * Returns all source directory markers.
     *
     * <p>Each marker includes leading and trailing slashes for pattern matching.
     *
     * @return array of source markers (e.g., "/src/main/resources/")
     */
    public static String[] getSourceMarkers() {
        return SOURCE_MARKERS.clone();
    }

    /**
     * Returns source directory paths without slashes.
     *
     * <p>Useful for path construction: {@code projectRoot.resolve(sourceDir)}.
     *
     * @return array of source directory paths (e.g., "src/main/resources")
     */
    public static String[] getSourcePaths() {
        String[] dirs = new String[SOURCE_MARKERS.length];
        for (int i = 0; i < SOURCE_MARKERS.length; i++) {
            // "/src/main/resources/" → "src/main/resources"
            String marker = SOURCE_MARKERS[i];
            dirs[i] = marker.substring(1, marker.length() - 1);
        }
        return dirs;
    }

    /**
     * Returns main source directory paths (non-test).
     *
     * @return array of main source paths
     */
    public static String[] getMainSourcePaths() {
        return MAIN_SOURCE_PATHS.clone();
    }

    /**
     * Returns project marker files that indicate a project root.
     *
     * @return array of marker file names (e.g., "pom.xml")
     */
    public static String[] getProjectMarkerFiles() {
        return PROJECT_MARKER_FILES.clone();
    }

    /**
     * Returns main (non-test) output directory paths.
     *
     * <p>Paths are derived from enum constants, without leading/trailing slashes.
     *
     * @return array of output paths (e.g., "target/classes")
     */
    public static String[] getMainOutputPaths() {
        Set<String> paths = new LinkedHashSet<>();  // Preserve order, avoid duplicates
        for (BuildSystem bs : values()) {
            if (!bs.isTest()) {
                // "/target/classes/" → "target/classes"
                String path = bs.outputMarker.substring(1, bs.outputMarker.length() - 1);
                paths.add(path);
            }
        }
        return paths.toArray(new String[0]);
    }

    /**
     * Returns output directory paths for either main or test.
     *
     * @param forTest true to get test output paths, false for main
     * @return array of output paths
     */
    public static String[] getOutputPaths(boolean forTest) {
        Set<String> paths = new LinkedHashSet<>();
        for (BuildSystem bs : values()) {
            if (bs.isTest() == forTest) {
                // "/target/classes/" → "target/classes"
                String path = bs.outputMarker.substring(1, bs.outputMarker.length() - 1);
                paths.add(path);
            }
        }
        return paths.toArray(new String[0]);
    }
}