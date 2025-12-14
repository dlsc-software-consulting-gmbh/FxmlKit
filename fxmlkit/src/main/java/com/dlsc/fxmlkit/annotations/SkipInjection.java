package com.dlsc.fxmlkit.annotations;

import com.dlsc.fxmlkit.di.FxmlInjectionPolicy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes the annotated type from dependency injection.
 *
 * <p>When this annotation is present, the object will not be processed
 * for dependency injection, even if the current injection policy or
 * include list would normally allow it.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * @SkipInjection
 * public class StaticBanner extends Label {
 *     // Never injected, even if AUTO mode is enabled
 * }
 * }</pre>
 *
 * @see FxmlInjectionPolicy
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipInjection {
}
