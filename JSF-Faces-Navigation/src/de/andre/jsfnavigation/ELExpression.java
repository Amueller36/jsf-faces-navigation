package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ELExpression {

    private final String expression;
    private final List<String> parts;
    private final int expressionStart;

    public ELExpression(
            String expression,
            List<String> parts,
            int expressionStart) {

        this.expression = expression;
        this.parts = Collections.unmodifiableList(
                new ArrayList<String>(parts));
        this.expressionStart = expressionStart;
    }

    public String getExpression() {
        return expression;
    }

    public List<String> getParts() {
        return parts;
    }

    public int getExpressionStart() {
        return expressionStart;
    }
}
