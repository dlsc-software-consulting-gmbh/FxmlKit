package com.dlsc.fxmlkit.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a custom FXML file path for a {@link com.dlsc.fxmlkit.fxml.FxmlView}
 * or {@link com.dlsc.fxmlkit.fxml.FxmlViewProvider}.
 *
 * <p>By default, FXML files are resolved using a convention-based approach:
 * <ol>
 *   <li>Try {@code ClassName.fxml} (exact match)</li>
 *   <li>Try {@code BaseName.fxml} (after stripping suffix: ViewProvider > Provider > View)</li>
 * </ol>
 *
 * <p>Use this annotation to override the default resolution and specify a custom path.
 *
 * <h2>Path Types</h2>
 *
 * <h3>Relative Paths (Recommended for Refactoring)</h3>
 * <p>Relative paths are resolved using {@link Class#getResource(String)}, making them
 * relative to the class's package location. This makes them refactoring-friendly as they
 * move with the class.
 *
 * <ul>
 *   <li><b>Same directory:</b> {@code @FxmlPath("Home.fxml")}
 *       <br>Example: {@code com/example/view/Home.fxml}</li>
 *
 *   <li><b>Subdirectory:</b> {@code @FxmlPath("fxml/Home.fxml")}
 *       <br>Example: {@code com/example/view/fxml/Home.fxml}</li>
 *
 *   <li><b>Parent directory:</b> {@code @FxmlPath("../fxml/Home.fxml")}
 *       <br>Example: {@code com/example/fxml/Home.fxml}
 *       <br><i>Useful for separating code and resources</i></li>
 *
 *   <li><b>Grandparent directory:</b> {@code @FxmlPath("../../fxml/Home.fxml")}
 *       <br>Example: {@code com/fxml/Home.fxml}</li>
 * </ul>
 *
 * <h3>Absolute Paths (From Classpath Root)</h3>
 * <p>Absolute paths start with {@code /} and are resolved from the classpath root.
 * Use for shared resources or cross-package references.
 *
 * <ul>
 *   <li><b>Root-relative:</b> {@code @FxmlPath("/fxml/Home.fxml")}
 *       <br>Example: {@code fxml/Home.fxml} (from classpath root)</li>
 *
 *   <li><b>Package-qualified:</b> {@code @FxmlPath("/com/example/shared/Common.fxml")}
 *       <br>Example: {@code com/example/shared/Common.fxml}</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Example 1: Code and Resources Separated</h3>
 * <pre>{@code
 * // Directory structure:
 * src/main/java/
 *   └── com/example/
 *       └── view/
 *           └── HomeView.java
 *
 * src/main/resources/
 *   └── com/example/
 *       └── fxml/              // Same level as view/
 *           └── Home.fxml
 *
 * // Code:
 * package com.example.view;
 *
 * @FxmlPath("../fxml/Home.fxml")  //  Relative path
 * public class HomeView extends FxmlView<HomeController> {}
 * }</pre>
 *
 * <h3>Example 2: Fully Independent Resource Directory</h3>
 * <pre>{@code
 * // Directory structure:
 * src/main/java/
 *   └── com/example/
 *       └── view/
 *           └── HomeView.java
 *
 * src/main/resources/
 *   └── fxml/                  // Independent directory
 *       └── Home.fxml
 *
 * // Code:
 * package com.example.view;
 *
 * @FxmlPath("/fxml/Home.fxml")  //  Absolute path
 * public class HomeView extends FxmlView<HomeController> {}
 * }</pre>
 *
 * <h3>Example 3: Subdirectory Organization</h3>
 * <pre>{@code
 * // Directory structure:
 * src/main/resources/
 *   └── com/example/view/
 *       └── home/
 *           ├── HomeView.java
 *           └── fxml/
 *               └── Home.fxml
 *
 * // Code:
 * @FxmlPath("fxml/Home.fxml")  //  Subdirectory
 * public class HomeView extends FxmlView<HomeController> {}
 * }</pre>
 *
 * <h3>Example 4: Shared FXML Across Modules</h3>
 * <pre>{@code
 * @FxmlPath("/shared/layouts/Common.fxml")  //  Shared resource
 * public class SharedView extends FxmlView<SharedController> {}
 * }</pre>
 *
 * <h2>Packaging and JAR Files</h2>
 * <p>All path types work correctly after packaging into JAR files. Resources are
 * resolved using {@link Class#getResource(String)}, which works consistently in
 * both development and production environments.
 *
 * <h2>Refactoring Considerations</h2>
 * <ul>
 *   <li><b>Relative paths:</b> Move with the class during refactoring. Recommended
 *       for resources that logically belong with the class.</li>
 *   <li><b>Absolute paths:</b> Fixed location regardless of class package. Use for
 *       truly shared resources that shouldn't move with classes.</li>
 * </ul>
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Use relative paths ({@code ../fxml/Home.fxml}) for better refactoring support</li>
 *   <li>Use absolute paths ({@code /fxml/Home.fxml}) only for truly shared resources</li>
 *   <li>Keep path strings consistent across related views</li>
 *   <li>Avoid deep relative paths ({@code ../../..}) - consider absolute instead</li>
 * </ul>
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
     * <p>Can be either:
     * <ul>
     *   <li><b>Relative:</b> {@code "Home.fxml"}, {@code "fxml/Home.fxml"}, {@code "../fxml/Home.fxml"}</li>
     *   <li><b>Absolute:</b> {@code "/fxml/Home.fxml"}, {@code "/com/example/shared/Common.fxml"}</li>
 * </ul>
     *
     * <p>The path is resolved using {@link Class#getResource(String)}:
     * <ul>
     *   <li>Paths <b>without</b> leading {@code /} are relative to the annotated class's package</li>
     *   <li>Paths <b>with</b> leading {@code /} are relative to the classpath root</li>
     * </ul>
     *
     * @return the FXML file path
     */
    String value();
}