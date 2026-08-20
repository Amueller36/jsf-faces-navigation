package de.andre.jsfnavigation;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public final class TestSnippetInserter {

    private static final Pattern MOCK_FIELD =
            Pattern.compile(
                    "(?ms)^@Mock\\s*\\Rprivate\\s+[^;\\r\\n]+\\s+(\\w+)\\s*;\\s*\\R?");

    private static final Pattern INJECT_FIELD =
            Pattern.compile(
                    "(?ms)^@InjectMocks\\s*\\Rprivate\\s+[^;\\r\\n]+\\s+(\\w+)\\s*;\\s*\\R?");

    private TestSnippetInserter() {
    }

    public static String insert(
            IType testType,
            String snippet)
            throws JavaModelException {

        if (testType == null
                || !testType.exists()
                || snippet == null
                || snippet.trim()
                        .isEmpty()) {

            return "Nothing to insert.";
        }

        if (testType.getCompilationUnit()
                .hasUnsavedChanges()) {

            return "The target test has unsaved changes. Save it first, then run Insert again.";
        }

        if (testType.getCompilationUnit()
                .hasUnsavedChanges()) {

            return "The target test has unsaved changes. Save it first, then run Insert again.";
        }

        IBuffer buffer =
                testType.getCompilationUnit()
                        .getBuffer();

        if (buffer == null) {
            return "The test source buffer is not available.";
        }

        String cleaned =
                removeDuplicateFields(
                        testType,
                        snippet);

        cleaned =
                avoidDuplicateGeneratedMethod(
                        testType,
                        cleaned);

        ISourceRange typeRange =
                testType.getSourceRange();

        if (typeRange == null
                || typeRange.getLength()
                        <= 0) {

            return "Could not resolve the test class source range.";
        }

        int typeEnd =
                typeRange.getOffset()
                + typeRange.getLength();

        String source =
                buffer.getText(
                        typeRange.getOffset(),
                        typeRange.getLength());

        int localClosing =
                source.lastIndexOf('}');

        if (localClosing < 0) {
            return "Could not find the test class closing brace.";
        }

        int insertionOffset =
                typeRange.getOffset()
                + localClosing;

        String indented =
                indent(
                        cleaned,
                        "    ");

        String insertion =
                "\n\n"
                + indented
                + (indented.endsWith("\n")
                        ? ""
                        : "\n");

        buffer.replace(
                insertionOffset,
                0,
                insertion);

        testType.getCompilationUnit()
                .save(
                        null,
                        true);

        return "Inserted generated snippet into "
                + testType.getElementName()
                + ".";
    }

    private static String removeDuplicateFields(
            IType type,
            String snippet)
            throws JavaModelException {

        Set<String> fields =
                new LinkedHashSet<String>();

        for (IField field :
                type.getFields()) {

            fields.add(
                    field.getElementName());
        }

        String result =
                removeDuplicateFields(
                        snippet,
                        MOCK_FIELD,
                        fields);

        result =
                removeDuplicateFields(
                        result,
                        INJECT_FIELD,
                        fields);

        return result;
    }

    private static String removeDuplicateFields(
            String snippet,
            Pattern pattern,
            Set<String> existingFields) {

        Matcher matcher =
                pattern.matcher(
                        snippet);

        StringBuffer result =
                new StringBuffer();

        while (matcher.find()) {
            String fieldName =
                    matcher.group(1);

            if (existingFields.contains(
                    fieldName)) {

                matcher.appendReplacement(
                        result,
                        "");

            } else {
                matcher.appendReplacement(
                        result,
                        Matcher.quoteReplacement(
                                matcher.group()));
            }
        }

        matcher.appendTail(
                result);

        return result.toString();
    }

    private static String avoidDuplicateGeneratedMethod(
            IType type,
            String snippet)
            throws JavaModelException {

        Pattern generated =
                Pattern.compile(
                        "(public\\s+void\\s+)([A-Za-z_$][A-Za-z0-9_$]*_shouldTODO)(\\s*\\()");

        Matcher matcher =
                generated.matcher(
                        snippet);

        if (!matcher.find()) {
            return snippet;
        }

        String base =
                matcher.group(2);

        Set<String> methods =
                new LinkedHashSet<String>();

        for (IMethod method :
                type.getMethods()) {

            methods.add(
                    method.getElementName());
        }

        if (!methods.contains(
                base)) {

            return snippet;
        }

        int suffix = 2;

        while (methods.contains(
                base + suffix)) {

            suffix++;
        }

        return snippet.substring(
                0,
                matcher.start(2))
                + base
                + suffix
                + snippet.substring(
                        matcher.end(2));
    }

    private static String indent(
            String value,
            String indent) {

        String normalized =
                value.replace(
                        "\r\n",
                        "\n")
                        .replace(
                                '\r',
                                '\n');

        String[] lines =
                normalized.split(
                        "\\n",
                        -1);

        StringBuilder out =
                new StringBuilder();

        for (int i = 0;
                i < lines.length;
                i++) {

            if (!lines[i].isEmpty()) {
                out.append(
                        indent);
            }

            out.append(
                    lines[i]);

            if (i + 1 < lines.length) {
                out.append('\n');
            }
        }

        return out.toString();
    }
}
