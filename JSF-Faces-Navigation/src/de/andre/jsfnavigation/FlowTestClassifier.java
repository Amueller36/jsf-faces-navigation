package de.andre.jsfnavigation;

import java.util.Locale;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public final class FlowTestClassifier {

    public static final int NOT_TEST = 0;
    public static final int UNIT_TEST = 1;
    public static final int ARQUILLIAN_TEST = 2;
    public static final int JPA_TEST = 3;
    public static final int INTEGRATION_TEST = 4;

    private FlowTestClassifier() {
    }

    public static int classify(
            IType type) {

        if (type == null
                || !type.exists()) {

            return NOT_TEST;
        }

        String source =
                sourceOf(type);

        if (!looksLikeJUnitTest(
                type,
                source)) {

            return NOT_TEST;
        }

        if (isArquillianSource(source)) {
            return ARQUILLIAN_TEST;
        }

        if (isIntegrationTest(
                type,
                source)) {

            return INTEGRATION_TEST;
        }

        if (isJpaSource(
                source,
                type.getElementName())) {

            return JPA_TEST;
        }

        return UNIT_TEST;
    }


    public static boolean isJUnitTestMethod(
            IMethod method) {

        if (method == null
                || !method.exists()) {

            return false;
        }

        if (hasTestAnnotation(
                method)) {

            return true;
        }

        /*
         * JUnit 3 has no @Test annotation. Keep its conventional public
         * testXxx() methods runnable in the editor gutter as well.
         */
        if (method.getElementName()
                .startsWith("test")
                && method.getNumberOfParameters()
                        == 0) {

            IType declaring =
                    method.getDeclaringType();

            return declaring != null
                    && classify(
                            declaring)
                            != NOT_TEST;
        }

        return false;
    }

    public static boolean isJUnitTestMethodOrType(
            IMethod method) {

        if (method == null
                || !method.exists()) {

            return false;
        }

        if (isJUnitTestMethod(
                method)) {

            return true;
        }

        return classify(
                method.getDeclaringType())
                != NOT_TEST;
    }

    public static String junitKind(
            IType type) {

        String source =
                sourceOf(type);

        if (source.indexOf(
                "org.junit.jupiter.") >= 0) {

            return "org.eclipse.jdt.junit.loader.junit5";
        }

        if (source.indexOf("org.junit.") >= 0
                || source.indexOf("@RunWith") >= 0) {

            return "org.eclipse.jdt.junit.loader.junit4";
        }

        return "org.eclipse.jdt.junit.loader.junit3";
    }

    public static boolean isArquillianSource(
            String source) {

        if (source == null) {
            return false;
        }

        return source.indexOf(
                "org.jboss.arquillian") >= 0
                || source.indexOf(
                        "Arquillian.class") >= 0
                || source.indexOf(
                        "@RunWith(Arquillian") >= 0;
    }


    public static boolean isIntegrationTest(
            IType type,
            String source) {

        if (type == null) {
            return false;
        }

        String name =
                type.getElementName();

        if (name.endsWith(
                "IntegrationTest")
                || name.endsWith(
                        "IntegrationTests")
                || name.endsWith("IT")) {

            return true;
        }

        IFile file =
                type.getResource()
                        instanceof IFile
                        ? (IFile)
                                type.getResource()
                        : null;

        if (file != null) {
            String path =
                    file.getProjectRelativePath()
                            .toPortableString()
                            .toLowerCase(
                                    Locale.ENGLISH);

            if (path.indexOf(
                    "/integration/") >= 0
                    || path.indexOf(
                            "/integrationtest/") >= 0
                    || path.indexOf(
                            "src/integration") >= 0) {

                return true;
            }
        }

        String text =
                source == null
                        ? ""
                        : source;

        return text.indexOf(
                "@Category(Integration") >= 0
                || text.indexOf(
                        "@Tag(\"integration\")") >= 0
                || text.indexOf(
                        "@Tag('integration')") >= 0;
    }

    public static boolean isJpaSource(
            String source,
            String typeName) {

        String text =
                source == null
                        ? ""
                        : source;

        String lowerName =
                typeName == null
                        ? ""
                        : typeName.toLowerCase(
                                Locale.ENGLISH);

        return text.indexOf(
                        "javax.persistence.") >= 0
                || text.indexOf(
                        "jakarta.persistence.") >= 0
                || text.indexOf(
                        "@PersistenceContext") >= 0
                || text.indexOf(
                        "@DataJpaTest") >= 0
                || text.indexOf(
                        "EntityManager") >= 0
                || text.indexOf(
                        "OpenJPA") >= 0
                || lowerName.indexOf(
                        "jpa") >= 0
                || lowerName.indexOf(
                        "persistence") >= 0;
    }

    private static boolean looksLikeJUnitTest(
            IType type,
            String source) {

        if (source == null) {
            return false;
        }

        if (source.indexOf("@Test") >= 0
                || source.indexOf(
                        "@ParameterizedTest") >= 0
                || source.indexOf(
                        "@RepeatedTest") >= 0
                || source.indexOf(
                        "@TestFactory") >= 0
                || source.indexOf(
                        "extends TestCase") >= 0
                || source.indexOf(
                        "org.junit.") >= 0
                || source.indexOf(
                        "org.junit.jupiter.") >= 0) {

            return true;
        }

        String name =
                type.getElementName();

        if (!(name.endsWith("Test")
                || name.endsWith("Tests")
                || name.endsWith("TestCase"))) {

            return false;
        }

        IFile file =
                type.getResource()
                        instanceof IFile
                        ? (IFile)
                                type.getResource()
                        : null;

        if (file == null) {
            return false;
        }

        String path =
                file.getProjectRelativePath()
                        .toPortableString()
                        .toLowerCase(
                                Locale.ENGLISH);

        return path.indexOf(
                "/test/") >= 0
                || path.startsWith(
                        "test/")
                || path.indexOf(
                        "/tests/") >= 0
                || path.indexOf(
                        "src/test") >= 0;
    }

    private static boolean hasTestAnnotation(
            IMethod method) {

        try {
            for (IAnnotation annotation :
                    method.getAnnotations()) {

                String name =
                        annotation.getElementName();

                if ("Test".equals(name)
                        || "org.junit.Test".equals(
                                name)
                        || "org.junit.jupiter.api.Test".equals(
                                name)
                        || "ParameterizedTest".equals(
                                name)
                        || "RepeatedTest".equals(
                                name)) {

                    return true;
                }
            }

        } catch (JavaModelException e) {
            return false;
        }

        return false;
    }

    private static String sourceOf(
            IType type) {

        IJavaElement unitElement =
                type.getAncestor(
                        IJavaElement.COMPILATION_UNIT);

        if (!(unitElement
                instanceof ICompilationUnit)) {

            return "";
        }

        try {
            String source =
                    ((ICompilationUnit)
                            unitElement)
                            .getSource();

            return source == null
                    ? ""
                    : source;

        } catch (JavaModelException e) {
            return "";
        }
    }
}
