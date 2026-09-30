package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public final class JpaMappingInfo {

    private JpaMappingInfo() {
    }

    public static String describe(JpaResolvedReference resolved) {
        if (resolved == null) {
            return null;
        }

        if (resolved.getMember() == null) {
            IType type = resolved.getAliasType();
            return type == null
                    ? null
                    : "JPQL alias '" + resolved.getReference().getAlias()
                            + "'\nEntity: " + type.getFullyQualifiedName('.');
        }

        JavaMemberTarget target = resolved.getMember();
        IJavaElement element = target.getJavaElement();

        StringBuilder info = new StringBuilder();
        info.append(resolved.getDeclaringType().getElementName())
                .append('.')
                .append(resolved.getReference().getSelectedSegment())
                .append('\n');

        info.append("Java type: ")
                .append(JpaTypeResolver.readableType(target))
                .append('\n');

        info.append("Declared as: ")
                .append(target.getField() != null ? "field" : "getter")
                .append('\n');

        List<String> mappings = mappingAnnotations(target);
        if (!mappings.isEmpty()) {
            info.append("JPA mapping:\n");
            for (String mapping : mappings) {
                info.append("  ").append(mapping).append('\n');
            }
        }

        String javadoc = sourceJavadoc(element);
        if (javadoc != null && !javadoc.isEmpty()) {
            info.append("Comment: ").append(javadoc);
        }

        return info.toString().trim();
    }

    private static List<String> mappingAnnotations(JavaMemberTarget target) {
        List<String> result = new ArrayList<String>();
        IAnnotatable annotatable = target.getField() != null
                ? target.getField()
                : target.getMethod();

        try {
            for (IAnnotation annotation : annotatable.getAnnotations()) {
                String simple = simpleName(annotation.getElementName());
                if (!isJpaAnnotation(simple)) {
                    continue;
                }

                StringBuilder text = new StringBuilder("@").append(simple);
                IMemberValuePair[] pairs = annotation.getMemberValuePairs();

                if (pairs.length > 0) {
                    text.append('(');
                    for (int i = 0; i < pairs.length; i++) {
                        if (i > 0) {
                            text.append(", ");
                        }

                        text.append(pairs[i].getMemberName())
                                .append(" = ")
                                .append(formatValue(pairs[i]));
                    }
                    text.append(')');
                }

                result.add(text.toString());
            }
        } catch (JavaModelException e) {
            // Hover information is best-effort only.
        }

        return result;
    }

    private static String sourceJavadoc(IJavaElement element) {
        if (!(element instanceof IMember)) {
            return null;
        }

        IMember member = (IMember) element;

        try {
            ISourceRange range = member.getJavadocRange();
            IJavaElement unitElement = member.getAncestor(IJavaElement.COMPILATION_UNIT);
            ICompilationUnit unit = unitElement instanceof ICompilationUnit
                    ? (ICompilationUnit) unitElement
                    : null;

            if (range == null || unit == null) {
                return null;
            }

            IBuffer buffer = unit.getBuffer();
            if (buffer == null) {
                return null;
            }

            String raw = buffer.getText(range.getOffset(), range.getLength());
            return cleanJavadoc(raw);

        } catch (JavaModelException e) {
            return null;
        }
    }

    private static String cleanJavadoc(String raw) {
        if (raw == null) {
            return null;
        }

        String text = raw.replace("/**", "")
                .replace("*/", "")
                .replace("\r", "");

        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.startsWith("*")) {
                cleaned = cleaned.substring(1).trim();
            }

            if (cleaned.isEmpty() || cleaned.startsWith("@")) {
                continue;
            }

            if (result.length() > 0) {
                result.append(' ');
            }

            result.append(cleaned);
        }

        return result.toString();
    }

    private static boolean isJpaAnnotation(String name) {
        return "Column".equals(name)
                || "Enumerated".equals(name)
                || "Id".equals(name)
                || "Embedded".equals(name)
                || "EmbeddedId".equals(name)
                || "Basic".equals(name)
                || "Transient".equals(name)
                || "OneToOne".equals(name)
                || "OneToMany".equals(name)
                || "ManyToOne".equals(name)
                || "ManyToMany".equals(name)
                || "JoinColumn".equals(name)
                || "JoinColumns".equals(name)
                || "JoinTable".equals(name)
                || "ElementCollection".equals(name)
                || "OrderBy".equals(name)
                || "MapKey".equals(name);
    }

    private static String formatValue(IMemberValuePair pair) {
        Object value = pair.getValue();

        if (value == null) {
            return "null";
        }

        if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            StringBuilder result = new StringBuilder("{");
            for (int i = 0; i < array.length; i++) {
                if (i > 0) {
                    result.append(", ");
                }
                result.append(formatRawValue(array[i], pair.getValueKind()));
            }
            return result.append('}').toString();
        }

        return formatRawValue(value, pair.getValueKind());
    }

    private static String formatRawValue(Object value, int valueKind) {
        if (valueKind == IMemberValuePair.K_STRING) {
            return "\"" + String.valueOf(value) + "\"";
        }

        return String.valueOf(value);
    }

    private static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
