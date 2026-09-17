package de.andre.jsfnavigation;

public final class ELSelectionResolver {

    private ELSelectionResolver() {
    }

    public static ELSelection resolve(
            int cursorOffset,
            ELExpression expression,
            String preferredProjectName) {

        for (int i = 0; i < expression.getParts().size(); i++) {
            String part = expression.getParts().get(i);
            int start = expression.getPartOffset(i);
            int endExclusive = start + part.length();

            if (cursorOffset >= start && cursorOffset < endExclusive) {
                return new ELSelection(
                        expression,
                        i,
                        part,
                        preferredProjectName);
            }
        }

        return null;
    }
}
