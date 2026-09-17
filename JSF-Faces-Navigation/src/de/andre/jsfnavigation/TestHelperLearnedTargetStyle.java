package de.andre.jsfnavigation;

public final class TestHelperLearnedTargetStyle {

    public static final int ASSERT_JUNIT = 0;
    public static final int ASSERTJ = 1;
    public static final int SOFT_ASSERTJ = 2;

    private final String targetTypeHandle;
    private final String subjectExpression;
    private final int assertionStyle;

    public TestHelperLearnedTargetStyle(
            String targetTypeHandle,
            String subjectExpression,
            int assertionStyle) {

        this.targetTypeHandle =
                targetTypeHandle == null
                        ? ""
                        : targetTypeHandle;

        this.subjectExpression =
                subjectExpression == null
                        ? ""
                        : subjectExpression;

        this.assertionStyle =
                assertionStyle;
    }

    public String getTargetTypeHandle() {
        return targetTypeHandle;
    }

    public String getSubjectExpression() {
        return subjectExpression;
    }

    public int getAssertionStyle() {
        return assertionStyle;
    }
}
