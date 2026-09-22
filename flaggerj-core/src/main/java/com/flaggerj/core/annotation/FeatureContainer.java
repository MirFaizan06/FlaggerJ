package com.flaggerj.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as a feature flag container.
 *
 * <p>Interfaces annotated with {@code @FeatureContainer} are scanned at compile time by the
 * {@code flaggerj-processor} annotation processor, which generates a concrete, reflection-free
 * implementation class (named {@code <InterfaceName>Impl}) in the same package. The generated
 * class implements every method annotated with {@link FeatureFlag} by delegating directly to a
 * {@code com.flaggerj.core.client.FlaggerClient} instance supplied through its constructor.
 *
 * <p>This annotation carries {@link RetentionPolicy#SOURCE} retention: it exists purely to drive
 * code generation and leaves no trace in compiled bytecode, so it never needs to be resolved via
 * reflection at runtime and is fully compatible with GraalVM Native Image.
 *
 * <pre>{@code
 * @FeatureContainer
 * public interface AppFeatures {
 *
 *     @FeatureFlag(key = "new-checkout", defaultValue = "false")
 *     boolean isNewCheckoutEnabled(FeatureContext context);
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface FeatureContainer {
}
