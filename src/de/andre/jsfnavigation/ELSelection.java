package de.andre.jsfnavigation;

public final class ELSelection {

    private final ELExpression expression;
    private final int partIndex;
    private final String selectedPart;

    public ELSelection(ELExpression expression, int partIndex, String selectedPart) {
        this.expression = expression;
        this.partIndex = partIndex;
        this.selectedPart = selectedPart;
    }

    public ELExpression getExpression() {
        return expression;
    }

    public int getPartIndex() {
        return partIndex;
    }

    public String getSelectedPart() {
        return selectedPart;
    }
}
