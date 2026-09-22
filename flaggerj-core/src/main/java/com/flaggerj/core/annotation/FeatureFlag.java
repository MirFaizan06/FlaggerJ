package com.flaggerj.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method within a {@link FeatureContainer} interface as a single feature flag lookup.
 *
 * <p>The annotated method's return type determines how the flag value is evaluated and must be
 * one of {@code boolean}, {@code int}, {@code double}, or {@link String}. The method may either
 * take no arguments, in which case an empty evaluation context is used, or a single
 * {@code com.flaggerj.core.context.FeatureContext} argument used to evaluate targeting rules.
 *
 * <p>{@code defaultValue} is always expressed as a {@link String} literal; the annotation
 * processor parses it into the correct primitive/String literal for the method's return type at
 * compile time, so no runtime parsing or reflection is required.
 *
 * <p>Like {@link FeatureContainer}, this annotation uses {@link RetentionPolicy#SOURCE} retention
 * and is discarded after compilation.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface FeatureFlag {

    /**
     * The unique key used to look up this flag's configuration in the {@code FlaggerClient}.
     */
    String key();

    /**
     * The default value to fall back to when the flag is not configured, is disabled, or matches
     * no targeting rule. Always specified as a string; parsed according to the method's return
     * type at compile time.
     */
    String defaultValue() default "";
}
