package com.dlsc.fxmlkit.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a custom FXML file path for a view.
 *
 * <p>By default, FXML files are resolved by convention: first try ClassName.fxml,
 * then try BaseName.fxml after stripping View/Provider/ViewProvider suffixes.
 * Use this annotation to override the default resolution.
 *
 * <p>Supports relative paths (resolved via {@link Class#getResource}):
 * <ul>
 *   <li>{@code @FxmlPath("Home.fxml")} - same directory as the class
 *   <li>{@code @FxmlPath("fxml/Home.fxml")} - subdirectory
 *   <li>{@code @FxmlPath("../fxml/Home.fxml")} - parent directory
 * </ul>
 *
 * <p>And absolute paths (from classpath root):
 * <ul>
 *   <li>{@code @FxmlPath("/fxml/Home.fxml")} - root-relative
 *   <li>{@code @FxmlPath("/com/example/shared/Common.fxml")} - package-qualified
 * </ul>
 *
 * <p>Relative paths are resolved relative to the class's package location, making
 * them refactoring-friendly as they move with the class. Absolute paths start with
 * {@code /} and are resolved from the classpath root, useful for shared resources.
 *
 * <p>Example:
 * <pre>{@code
 * // Code and resources separated
 * // src/main/java/com/example/view/HomeView.java
 * // src/main/resources/com/example/fxml/Home.fxml
 *
 * package com.example.view;
 *
 * @FxmlPath("../fxml/Home.fxml")
 * public class HomeView extends FxmlView<HomeController> {}
 * }</pre>
 *
 * <p>All path types work correctly after packaging into JAR files, as resources
 * are resolved using {@link Class#getResource}, which works consistently in both
 * development and production environments.
 *
 * @see com.dlsc.fxmlkit.fxml.FxmlView
 * @see com.dlsc.fxmlkit.fxml.FxmlViewProvider
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FxmlPath {
    
    /**
     * The path to the FXML file.
     *
     * <p>Can be either relative ({@code "Home.fxml"}, {@code "fxml/Home.fxml"},
     * {@code "../fxml/Home.fxml"}) or absolute ({@code "/fxml/Home.fxml"},
     * {@code "/com/example/shared/Common.fxml"}).
     *
     * <p>Paths without leading {@code /} are relative to the annotated class's package.
     * Paths with leading {@code /} are relative to the classpath root.
     *
     * @return the FXML file path
     */
    String value();
}