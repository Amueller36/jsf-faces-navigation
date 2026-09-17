package de.andre.jsfnavigation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

public final class JsfLocalVariableTypeResolver {

    private static final Pattern AUTOCOMPLETE_TAG =
            Pattern.compile(
                    "<\\s*p:autoComplete\\b[^>]*>",
                    Pattern.CASE_INSENSITIVE
                    | Pattern.DOTALL);

    private static final Pattern METHOD_EXPRESSION =
            Pattern.compile(
                    "[#\\$]\\{\\s*([A-Za-z_$][A-Za-z0-9_$]*)"
                    + "\\.([A-Za-z_$][A-Za-z0-9_$]*)"
                    + "(?:\\s*\\([^}]*\\))?\\s*\\}");

    private JsfLocalVariableTypeResolver() {
    }

    public static IType resolve(
            IFile file,
            String variableName,
            String projectName) {

        if (file == null
                || variableName == null
                || variableName.isEmpty()) {

            return null;
        }

        String source =
                JsfPageInspector.read(file);

        if (source == null) {
            return null;
        }

        Matcher tags =
                AUTOCOMPLETE_TAG.matcher(source);

        while (tags.find()) {
            String tag = tags.group();

            String var =
                    attribute(
                            tag,
                            "var");

            if (!variableName.equals(var)) {
                continue;
            }

            String completeMethod =
                    attribute(
                            tag,
                            "completeMethod");

            IType type =
                    resolveCompleteMethodElementType(
                            completeMethod,
                            projectName);

            if (type != null) {
                return type;
            }
        }

        return null;
    }

    private static IType resolveCompleteMethodElementType(
            String expression,
            String projectName) {

        if (expression == null) {
            return null;
        }

        Matcher matcher =
                METHOD_EXPRESSION.matcher(expression);

        if (!matcher.matches()) {
            return null;
        }

        IType bean =
                ElJavaResolver.resolveBean(
                        matcher.group(1),
                        projectName);

        if (bean == null) {
            return null;
        }

        try {
            IMethod method =
                    ElJavaResolver.findMethod(
                            bean,
                            matcher.group(2));

            if (method == null) {
                return null;
            }

            String readable =
                    Signature.toString(
                            method.getReturnType());

            String element =
                    collectionElement(readable);

            if (element == null
                    || element.isEmpty()) {

                return null;
            }

            IType declaring =
                    method.getDeclaringType();

            String[][] resolved =
                    declaring.resolveType(element);

            if (resolved != null
                    && resolved.length > 0) {

                String packageName =
                        resolved[0][0];

                String className =
                        resolved[0][1];

                String qualified =
                        packageName == null
                                || packageName.isEmpty()
                                        ? className
                                        : packageName
                                                + "."
                                                + className;

                IType result =
                        declaring.getJavaProject()
                                .findType(qualified);

                if (result != null
                        && result.exists()) {

                    return result;
                }
            }

            List<IType> candidates =
                    JavaTypeFinder.findTypes(
                            simpleName(element),
                            projectName);

            return candidates.isEmpty()
                    ? null
                    : candidates.get(0);

        } catch (JavaModelException e) {
            return null;
        }
    }

    private static String attribute(
            String tag,
            String name) {

        Pattern pattern =
                Pattern.compile(
                        "\\b"
                        + Pattern.quote(name)
                        + "\\s*=\\s*(['\\\"])(.*?)\\1",
                        Pattern.CASE_INSENSITIVE
                        | Pattern.DOTALL);

        Matcher matcher =
                pattern.matcher(tag);

        return matcher.find()
                ? matcher.group(2).trim()
                : null;
    }

    private static String collectionElement(
            String readable) {

        int lt = readable.indexOf('<');
        int gt = readable.lastIndexOf('>');

        if (lt <= 0
                || gt <= lt) {

            return null;
        }

        String raw =
                simpleName(
                        readable.substring(
                                0,
                                lt));

        if (!"Collection".equals(raw)
                && !"List".equals(raw)
                && !"Set".equals(raw)
                && !"Iterable".equals(raw)) {

            return null;
        }

        String generic =
                readable.substring(
                        lt + 1,
                        gt)
                        .trim();

        int comma = generic.indexOf(',');

        if (comma >= 0) {
            generic =
                    generic.substring(
                            0,
                            comma)
                            .trim();
        }

        if (generic.startsWith("? extends ")) {
            generic =
                    generic.substring(
                            "? extends ".length())
                            .trim();

        } else if (generic.startsWith("? super ")) {
            generic =
                    generic.substring(
                            "? super ".length())
                            .trim();
        }

        return generic;
    }

    private static String simpleName(
            String value) {

        int dot = value.lastIndexOf('.');

        return dot >= 0
                ? value.substring(dot + 1)
                : value;
    }
}
