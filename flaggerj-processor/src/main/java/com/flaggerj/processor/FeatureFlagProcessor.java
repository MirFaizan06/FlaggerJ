package com.flaggerj.processor;

import com.flaggerj.core.annotation.FeatureContainer;
import com.flaggerj.core.annotation.FeatureFlag;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Compile-time annotation processor that turns {@code @FeatureContainer} interfaces into concrete,
 * zero-reflection implementation classes.
 *
 * <p>For every interface annotated with {@link FeatureContainer}, this processor:
 * <ol>
 *   <li>validates that the annotated element really is an interface,</li>
 *   <li>collects every method annotated with {@link FeatureFlag}, validating its return type
 *       ({@code boolean}, {@code int}, {@code double}, or {@link String}) and its parameter list
 *       (either empty, or a single {@code com.flaggerj.core.context.FeatureContext}), and</li>
 *   <li>writes a new top-level class {@code <InterfaceName>Impl}, in the same package, that
 *       implements the interface and resolves each flag by delegating directly to a
 *       {@code com.flaggerj.core.client.FlaggerClient} field set through its constructor.</li>
 * </ol>
 *
 * <p>Because dispatch to {@code getBoolean}/{@code getString}/{@code getInt}/{@code getDouble} is
 * chosen once, at compile time, based on the method's declared return type, the generated code
 * contains no reflection, no dynamic proxies, and no runtime code generation, making it fully
 * compatible with GraalVM Native Image.
 */
@SupportedAnnotationTypes("com.flaggerj.core.annotation.FeatureContainer")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class FeatureFlagProcessor extends AbstractProcessor {

    private static final String CONTEXT_TYPE = "com.flaggerj.core.context.FeatureContext";
    private static final String CLIENT_TYPE = "com.flaggerj.core.client.FlaggerClient";

    private Messager messager;
    private Filer filer;
    private Elements elementUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elementUtils = processingEnv.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(FeatureContainer.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@FeatureContainer can only be applied to interfaces", element);
                continue;
            }
            try {
                generateImplementation((TypeElement) element);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Failed to generate implementation for " + element + ": " + e.getMessage(), element);
            }
        }
        return true;
    }

    private void generateImplementation(TypeElement interfaceElement) throws IOException {
        PackageElement packageElement = elementUtils.getPackageOf(interfaceElement);
        String packageName = packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
        String interfaceName = interfaceElement.getSimpleName().toString();
        String implName = interfaceName + "Impl";
        String qualifiedInterfaceName = interfaceElement.getQualifiedName().toString();

        List<FlagMethod> methods = new ArrayList<>();
        for (Element enclosed : interfaceElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosed;
            FeatureFlag annotation = method.getAnnotation(FeatureFlag.class);
            if (annotation == null) {
                continue;
            }
            FlagMethod flagMethod = validateAndBuild(method, annotation);
            if (flagMethod != null) {
                methods.add(flagMethod);
            }
        }

        String qualifiedImplName = packageName.isEmpty() ? implName : packageName + "." + implName;
        JavaFileObject fileObject = filer.createSourceFile(qualifiedImplName, interfaceElement);
        try (PrintWriter writer = new PrintWriter(fileObject.openWriter())) {
            if (!packageName.isEmpty()) {
                writer.println("package " + packageName + ";");
                writer.println();
            }
            writer.println("import " + CONTEXT_TYPE + ";");
            writer.println("import " + CLIENT_TYPE + ";");
            writer.println();
            writer.println("/**");
            writer.println(" * Generated by flaggerj-processor from {@link " + qualifiedInterfaceName + "}.");
            writer.println(" * Do not edit by hand; changes will be overwritten on the next build.");
            writer.println(" */");
            writer.println("public final class " + implName + " implements " + qualifiedInterfaceName + " {");
            writer.println();
            writer.println("    private final FlaggerClient client;");
            writer.println();
            writer.println("    public " + implName + "(FlaggerClient client) {");
            writer.println("        if (client == null) {");
            writer.println("            throw new IllegalArgumentException(\"client must not be null\");");
            writer.println("        }");
            writer.println("        this.client = client;");
            writer.println("    }");
            writer.println();
            for (FlagMethod method : methods) {
                writeMethod(writer, method);
            }
            writer.println("}");
        }
    }

    private FlagMethod validateAndBuild(ExecutableElement method, FeatureFlag annotation) {
        boolean valid = true;

        TypeMirror returnType = method.getReturnType();
        SupportedType supportedType = SupportedType.fromTypeMirror(returnType);
        if (supportedType == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "@FeatureFlag method must return boolean, int, double, or String", method);
            valid = false;
        }

        List<? extends VariableElement> parameters = method.getParameters();
        boolean hasContextParam = false;
        if (parameters.size() == 1 && parameters.get(0).asType().toString().equals(CONTEXT_TYPE)) {
            hasContextParam = true;
        } else if (!parameters.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "@FeatureFlag method must take either no arguments or a single " + CONTEXT_TYPE + " argument",
                    method);
            valid = false;
        }

        if (method.getModifiers().contains(Modifier.STATIC) || method.isDefault()) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "@FeatureFlag cannot be applied to static or default methods", method);
            valid = false;
        }

        String key = annotation.key();
        if (key == null || key.isBlank()) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@FeatureFlag key must not be blank", method);
            valid = false;
        }

        if (!valid) {
            return null;
        }

        return new FlagMethod(
                method.getSimpleName().toString(),
                key,
                annotation.defaultValue(),
                supportedType,
                hasContextParam);
    }

    private void writeMethod(PrintWriter writer, FlagMethod method) {
        String paramList = method.hasContextParam ? "FeatureContext context" : "";
        String contextExpr = method.hasContextParam ? "context" : "FeatureContext.empty()";

        writer.println("    @Override");
        writer.println("    public " + method.returnType.javaTypeName + " " + method.methodName + "(" + paramList
                + ") {");
        switch (method.returnType) {
            case BOOLEAN:
                writer.println("        return client.getBoolean(\"" + escape(method.key) + "\", " + contextExpr
                        + ", " + defaultBooleanLiteral(method.defaultValue) + ");");
                break;
            case INT:
                writer.println("        return client.getInt(\"" + escape(method.key) + "\", " + contextExpr
                        + ", " + defaultIntLiteral(method.defaultValue) + ");");
                break;
            case DOUBLE:
                writer.println("        return client.getDouble(\"" + escape(method.key) + "\", " + contextExpr
                        + ", " + defaultDoubleLiteral(method.defaultValue) + ");");
                break;
            case STRING:
                writer.println("        return client.getString(\"" + escape(method.key) + "\", " + contextExpr
                        + ", \"" + escape(method.defaultValue) + "\");");
                break;
            default:
                throw new IllegalStateException("Unsupported type: " + method.returnType);
        }
        writer.println("    }");
        writer.println();
    }

    private String defaultBooleanLiteral(String raw) {
        return Boolean.toString(raw != null && Boolean.parseBoolean(raw));
    }

    private String defaultIntLiteral(String raw) {
        try {
            return Integer.toString(raw == null || raw.isBlank() ? 0 : Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "defaultValue '" + raw + "' is not a valid int literal");
            return "0";
        }
    }

    private String defaultDoubleLiteral(String raw) {
        try {
            return Double.toString(raw == null || raw.isBlank() ? 0.0 : Double.parseDouble(raw.trim()));
        } catch (NumberFormatException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "defaultValue '" + raw + "' is not a valid double literal");
            return "0.0";
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private enum SupportedType {
        BOOLEAN("boolean"),
        INT("int"),
        DOUBLE("double"),
        STRING("String");

        final String javaTypeName;

        SupportedType(String javaTypeName) {
            this.javaTypeName = javaTypeName;
        }

        static SupportedType fromTypeMirror(TypeMirror typeMirror) {
            TypeKind kind = typeMirror.getKind();
            if (kind == TypeKind.BOOLEAN) {
                return BOOLEAN;
            }
            if (kind == TypeKind.INT) {
                return INT;
            }
            if (kind == TypeKind.DOUBLE) {
                return DOUBLE;
            }
            if (kind == TypeKind.DECLARED && typeMirror.toString().equals("java.lang.String")) {
                return STRING;
            }
            return null;
        }
    }

    private static final class FlagMethod {
        final String methodName;
        final String key;
        final String defaultValue;
        final SupportedType returnType;
        final boolean hasContextParam;

        FlagMethod(String methodName, String key, String defaultValue, SupportedType returnType,
                   boolean hasContextParam) {
            this.methodName = methodName;
            this.key = key;
            this.defaultValue = defaultValue;
            this.returnType = returnType;
            this.hasContextParam = hasContextParam;
        }
    }
}
