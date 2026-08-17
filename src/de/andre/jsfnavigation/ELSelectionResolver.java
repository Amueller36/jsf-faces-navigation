package de.andre.jsfnavigation;

public final class ELSelectionResolver {

    private ELSelectionResolver() {
    }

    public static ELSelection resolve(int cursorOffset, ELExpression expression) {
        int currentOffset = expression.getExpressionStart() + 2;

        for (int i = 0; i < expression.getParts().size(); i++) {
            String part = expression.getParts().get(i);

            int start = currentOffset;
            int endExclusive = start + part.length();

            if (cursorOffset >= start && cursorOffset <= endExclusive) {
                return new ELSelection(expression, i, part);
            }

            currentOffset = endExclusive + 1;
        }

        return null;
    }
}
