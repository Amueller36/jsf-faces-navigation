package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaScriptDefinitionFinder {

    private static final String IDENT = "[A-Za-z_$][A-Za-z0-9_$]*";

    private static final Pattern[] PATTERNS = new Pattern[] {
            Pattern.compile("\\bfunction\\s+(" + IDENT + ")\\s*\\("),
            Pattern.compile("(?:\\b(?:var|let|const)\\s+)?(" + IDENT + ")\\s*=\\s*function\\s*\\("),
            Pattern.compile("(?:\\b(?:var|let|const)\\s+)?(" + IDENT + ")\\s*=\\s*(?:\\([^)]*\\)|" + IDENT + ")\\s*=>")
    };

    private JavaScriptDefinitionFinder() {
    }

    public static List<Integer> findOffsets(String source, String functionName) {
        List<Integer> result = new ArrayList<Integer>();

        if (source == null || functionName == null) {
            return result;
        }

        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(source);
            while (matcher.find()) {
                if (functionName.equals(matcher.group(1))) {
                    result.add(Integer.valueOf(matcher.start(1)));
                }
            }
        }

        return result;
    }
}
