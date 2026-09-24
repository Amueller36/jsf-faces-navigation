package de.andre.jsfnavigation;

public final class ELSelection {

    private final ELExpression expression;
    private final int partIndex;
    private final String selectedPart;
    private final String preferredProjectName;

    public ELSelection(
            ELExpression expression,
            int partIndex,
            String selectedPart,
            String preferredProjectName) {

        this.expression = expression;
        this.partIndex = partIndex;
        this.selectedPart = selectedPart;
        this.preferredProjectName = preferredProjectName;
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

    public String getPreferredProjectName() {
        return preferredProjectName;
    }
}
