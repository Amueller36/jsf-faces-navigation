package de.andre.jsfnavigation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.IDocument;

public final class JpaQueryResolver {

    private static final Pattern CLASS_NAME_ALIAS = Pattern.compile(
            "([A-Za-z_$][A-Za-z0-9_$]*)\\.class\\.getName\\s*\\(\\s*\\)\\s*\\+\\s*\"([^\"]*)\"");

    private static final Pattern FROM_ALIAS = Pattern.compile(
            "(?i)\\bFROM\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s+(?:AS\\s+)?([A-Za-z_$][A-Za-z0-9_$]*)");

    private static final Pattern JOIN_ALIAS = Pattern.compile(
            "(?i)\\bJOIN\\s+(?:FETCH\\s+)?([A-Za-z_$][A-Za-z0-9_$.]*)\\s+(?:AS\\s+)?([A-Za-z_$][A-Za-z0-9_$]*)");

    private static final Pattern STRING_LITERAL = Pattern.compile(
            "\"((?:\\\\.|[^\"\\\\])*)\"");

    private JpaQueryResolver() {
    }

    public static JpaResolvedReference resolve(
            IDocument document,
            IFile javaFile,
            JpaQueryReference reference) {

        if (document == null || javaFile == null || reference == null) {
            return null;
        }

        String source = document.get();
        int start = Math.max(0, reference.getStatementStart());
        int end = Math.min(source.length(), reference.getStatementEnd());

        if (end <= start) {
            return null;
        }

        String statement = source.substring(start, end);
        String projectName = javaFile.getProject().getName();

        Map<String, IType> aliases = buildAliasMap(statement, projectName);
        IType aliasType = aliases.get(reference.getAlias());

        if (aliasType == null) {
            return null;
        }

        if (reference.getSelectedIndex() == 0) {
            return new JpaResolvedReference(
                    reference,
                    aliasType,
                    aliasType,
                    null);
        }

        IType currentType = aliasType;
        JavaMemberTarget selectedMember = null;
        IType selectedDeclaringType = null;

        try {
            for (int i = 1; i <= reference.getSelectedIndex(); i++) {
                JavaMemberTarget member = JpaMemberResolver.resolve(
                        currentType,
                        reference.getSegments().get(i));

                if (member == null) {
                    return null;
                }

                if (i == reference.getSelectedIndex()) {
                    selectedMember = member;
                    selectedDeclaringType = currentType;
                    break;
                }

                IType nextType = JpaTypeResolver.resolvePropertyType(
                        currentType,
                        member);

                if (nextType == null) {
                    return null;
                }

                currentType = nextType;
            }
        } catch (JavaModelException e) {
            return null;
        }

        return new JpaResolvedReference(
                reference,
                aliasType,
                selectedDeclaringType,
                selectedMember);
    }

    private static Map<String, IType> buildAliasMap(
            String statement,
            String projectName) {

        Map<String, IType> aliases = new HashMap<String, IType>();

        Matcher classMatcher = CLASS_NAME_ALIAS.matcher(statement);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            String literalAfter = unescape(classMatcher.group(2)).trim();
            String alias = firstIdentifier(literalAfter);

            if (alias != null) {
                IType type = findType(className, projectName);
                if (type != null) {
                    aliases.put(alias, type);
                }
            }
        }

        String queryText = concatenateStringLiterals(statement);

        Matcher fromMatcher = FROM_ALIAS.matcher(queryText);
        while (fromMatcher.find()) {
            String entity = fromMatcher.group(1);
            String alias = fromMatcher.group(2);

            IType type = findType(simpleName(entity), projectName);
            if (type != null) {
                aliases.put(alias, type);
            }
        }

        boolean changed;
        int passes = 0;

        do {
            changed = false;
            Matcher joinMatcher = JOIN_ALIAS.matcher(queryText);

            while (joinMatcher.find()) {
                String path = joinMatcher.group(1);
                String alias = joinMatcher.group(2);

                if (aliases.containsKey(alias)) {
                    continue;
                }

                IType joinedType = resolvePathType(path, aliases);
                if (joinedType != null) {
                    aliases.put(alias, joinedType);
                    changed = true;
                }
            }

            passes++;
        } while (changed && passes < 8);

        return aliases;
    }

    private static IType resolvePathType(
            String path,
            Map<String, IType> aliases) {

        String[] parts = path.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        IType current = aliases.get(parts[0]);
        if (current == null) {
            return null;
        }

        try {
            for (int i = 1; i < parts.length; i++) {
                JavaMemberTarget member = JpaMemberResolver.resolve(current, parts[i]);
                if (member == null) {
                    return null;
                }

                current = JpaTypeResolver.resolvePropertyType(current, member);
                if (current == null) {
                    return null;
                }
            }
        } catch (JavaModelException e) {
            return null;
        }

        return current;
    }

    private static IType findType(String simpleName, String projectName) {
        List<IType> matches = JavaTypeFinder.findTypes(simpleName, projectName);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static String concatenateStringLiterals(String statement) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = STRING_LITERAL.matcher(statement);

        while (matcher.find()) {
            result.append(' ')
                    .append(unescape(matcher.group(1)));
        }

        return result.toString();
    }

    private static String firstIdentifier(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }

        if (i >= text.length() || !Character.isJavaIdentifierStart(text.charAt(i))) {
            return null;
        }

        int start = i++;
        while (i < text.length() && Character.isJavaIdentifierPart(text.charAt(i))) {
            i++;
        }

        return text.substring(start, i);
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
