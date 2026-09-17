package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestHelperLearnedFixtureReport {

    private final List<TestHelperLearnedFixtureRecipe> recipes;
    private final Map<String, TestHelperLearnedTargetStyle> stylesByTarget;
    private final boolean truncated;

    public TestHelperLearnedFixtureReport(
            List<TestHelperLearnedFixtureRecipe> recipes,
            Map<String, TestHelperLearnedTargetStyle> stylesByTarget,
            boolean truncated) {

        this.recipes =
                Collections.unmodifiableList(
                        new ArrayList<TestHelperLearnedFixtureRecipe>(
                                recipes == null
                                        ? Collections.<TestHelperLearnedFixtureRecipe>emptyList()
                                        : recipes));

        this.stylesByTarget =
                Collections.unmodifiableMap(
                        new LinkedHashMap<String, TestHelperLearnedTargetStyle>(
                                stylesByTarget == null
                                        ? Collections.<String, TestHelperLearnedTargetStyle>emptyMap()
                                        : stylesByTarget));

        this.truncated = truncated;
    }

    public static TestHelperLearnedFixtureReport empty() {
        return new TestHelperLearnedFixtureReport(
                Collections.<TestHelperLearnedFixtureRecipe>emptyList(),
                Collections.<String, TestHelperLearnedTargetStyle>emptyMap(),
                false);
    }

    public List<TestHelperLearnedFixtureRecipe> getRecipes() {
        return recipes;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public TestHelperLearnedTargetStyle styleFor(
            TestTargetCandidate target) {

        if (target == null
                || target.getType() == null) {

            return null;
        }

        return stylesByTarget.get(
                target.getType()
                        .getHandleIdentifier());
    }

    public TestHelperLearnedFixtureRecipe bestRecipe(
            TestHelperAnalysis analysis,
            TestTargetCandidate target) {

        if (analysis == null
                || target == null
                || target.getType() == null) {

            return null;
        }

        final String targetHandle =
                target.getType()
                        .getHandleIdentifier();

        List<TestHelperLearnedFixtureRecipe> candidates =
                new ArrayList<TestHelperLearnedFixtureRecipe>();

        for (TestHelperLearnedFixtureRecipe recipe :
                recipes) {

            if (recipe.isFromTargetHandle(
                    targetHandle)
                    && recipe.coverageCount(
                            analysis.getFixtureDependencies()) > 0) {

                candidates.add(
                        recipe);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Collections.sort(
                candidates,
                new Comparator<TestHelperLearnedFixtureRecipe>() {
                    @Override
                    public int compare(
                            TestHelperLearnedFixtureRecipe left,
                            TestHelperLearnedFixtureRecipe right) {

                        int leftCoverage =
                                left.coverageCount(
                                        analysis.getFixtureDependencies());

                        int rightCoverage =
                                right.coverageCount(
                                        analysis.getFixtureDependencies());

                        if (leftCoverage != rightCoverage) {
                            return rightCoverage
                                    - leftCoverage;
                        }

                        int score =
                                right.getBaseScore()
                                - left.getBaseScore();

                        if (score != 0) {
                            return score;
                        }

                        return left.getLabel()
                                .compareToIgnoreCase(
                                        right.getLabel());
                    }
                });

        return candidates.get(0);
    }

    public int recipeCountFor(
            TestTargetCandidate target) {

        if (target == null
                || target.getType() == null) {

            return 0;
        }

        String handle =
                target.getType()
                        .getHandleIdentifier();

        int count = 0;

        for (TestHelperLearnedFixtureRecipe recipe :
                recipes) {

            if (recipe.isFromTargetHandle(
                    handle)) {
                count++;
            }
        }

        return count;
    }
}
