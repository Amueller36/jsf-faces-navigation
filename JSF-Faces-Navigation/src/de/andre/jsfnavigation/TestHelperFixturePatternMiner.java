package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

public final class TestHelperFixturePatternMiner {

    private static final int MAX_TEST_COMPILATION_UNITS = 12;
    private static final int MAX_TEST_METHODS_PER_TYPE = 80;
    private static final int MAX_RECIPES = 60;
    private static final int MAX_STATEMENTS_PER_RECIPE = 24;

    private TestHelperFixturePatternMiner() {
    }

    public static TestHelperLearnedFixtureReport mine(
            List<TestHelperAnalysis> analyses,
            List<TestTargetCandidate> targets,
            IProgressMonitor monitor) {

        if (analyses == null
                || analyses.isEmpty()
                || targets == null
                || targets.isEmpty()) {

            return TestHelperLearnedFixtureReport.empty();
        }

        final Set<String> requiredFixtures =
                requiredFixtureTypes(
                        analyses);

        final Set<String> parameterNames =
                parameterNames(
                        analyses);

        final Set<String> productionMethodNames =
                productionMethodNames(
                        analyses);

        final Set<String> productionNameFragments =
                productionNameFragments(
                        analyses);

        if (requiredFixtures.isEmpty()) {
            return TestHelperLearnedFixtureReport.empty();
        }

        List<TestHelperLearnedFixtureRecipe> recipes =
                new ArrayList<TestHelperLearnedFixtureRecipe>();

        Map<String, TestHelperLearnedTargetStyle> styles =
                new LinkedHashMap<String, TestHelperLearnedTargetStyle>();

        Set<String> parsedUnits =
                new HashSet<String>();

        boolean truncated = false;

        for (TestTargetCandidate candidate :
                targets) {

            if (monitor != null
                    && monitor.isCanceled()) {

                break;
            }

            if (candidate == null
                    || candidate.getType() == null
                    || !candidate.getType()
                            .exists()) {

                continue;
            }

            IType testType =
                    candidate.getType();

            ICompilationUnit unit =
                    testType.getCompilationUnit();

            if (unit == null
                    || !unit.exists()
                    || !parsedUnits.add(
                            unit.getHandleIdentifier())) {

                continue;
            }

            if (parsedUnits.size()
                    > MAX_TEST_COMPILATION_UNITS) {

                truncated = true;
                break;
            }

            CompilationUnit ast =
                    parse(
                            unit,
                            monitor);

            if (ast == null) {
                continue;
            }

            String source;

            try {
                source = unit.getSource();

            } catch (JavaModelException e) {
                continue;
            }

            if (source == null) {
                continue;
            }

            final Map<String, Integer> subjectCounts =
                    new LinkedHashMap<String, Integer>();

            collectSubjectExpressions(
                    ast,
                    productionMethodNames,
                    productionNameFragments,
                    subjectCounts);

            styles.put(
                    testType.getHandleIdentifier(),
                    new TestHelperLearnedTargetStyle(
                            testType.getHandleIdentifier(),
                            mostFrequent(
                                    subjectCounts),
                            assertionStyle(
                                    source)));

            int scannedMethods = 0;

            try {
                for (IMethod testMethod :
                        testType.getMethods()) {

                    if (monitor != null
                            && monitor.isCanceled()) {

                        break;
                    }

                    if (scannedMethods
                            >= MAX_TEST_METHODS_PER_TYPE) {

                        truncated = true;
                        break;
                    }

                    if (!FlowTestClassifier
                            .isJUnitTestMethod(
                                    testMethod)) {

                        continue;
                    }

                    scannedMethods++;

                    TestHelperLearnedFixtureRecipe recipe =
                            recipeForMethod(
                                    testType,
                                    testMethod,
                                    ast,
                                    source,
                                    requiredFixtures,
                                    parameterNames,
                                    productionMethodNames,
                                    productionNameFragments);

                    if (recipe != null) {
                        recipes.add(
                                recipe);
                    }

                    if (recipes.size()
                            >= MAX_RECIPES) {

                        truncated = true;
                        break;
                    }
                }

            } catch (JavaModelException e) {
                // Best-effort mining only.
            }

            if (recipes.size()
                    >= MAX_RECIPES) {

                break;
            }
        }

        Collections.sort(
                recipes,
                new Comparator<TestHelperLearnedFixtureRecipe>() {
                    @Override
                    public int compare(
                            TestHelperLearnedFixtureRecipe left,
                            TestHelperLearnedFixtureRecipe right) {

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

        return new TestHelperLearnedFixtureReport(
                recipes,
                styles,
                truncated);
    }

    private static TestHelperLearnedFixtureRecipe recipeForMethod(
            IType testType,
            IMethod method,
            CompilationUnit ast,
            String source,
            Set<String> requiredFixtures,
            Set<String> parameterNames,
            Set<String> productionMethodNames,
            Set<String> productionNameFragments) {

        MethodDeclaration declaration =
                methodDeclaration(
                        method,
                        ast);

        if (declaration == null
                || declaration.getBody() == null) {

            return null;
        }

        Block body =
                declaration.getBody();

        @SuppressWarnings("unchecked")
        List<Statement> statements =
                body.statements();

        if (statements.isEmpty()) {
            return null;
        }

        Map<String, Integer> declarationIndexByVariable =
                new LinkedHashMap<String, Integer>();

        Map<Integer, Set<String>> fixtureTypesByStatement =
                new HashMap<Integer, Set<String>>();

        Set<Integer> selected =
                new LinkedHashSet<Integer>();

        Set<String> trackedVariables =
                new LinkedHashSet<String>();

        Set<String> coveredFixtureTypes =
                new LinkedHashSet<String>();

        int whenIndex =
                statements.size();

        for (int i = 0;
                i < statements.size();
                i++) {

            Statement statement =
                    statements.get(i);

            if (containsProductionInvocation(
                    statement,
                    productionMethodNames,
                    productionNameFragments)) {

                whenIndex = i;
                break;
            }

            if (statement
                    instanceof VariableDeclarationStatement) {

                VariableDeclarationStatement variableStatement =
                        (VariableDeclarationStatement)
                                statement;

                Set<String> fixtureTypes =
                        fixtureTypes(
                                variableStatement.getType()
                                        .resolveBinding(),
                                requiredFixtures);

                fixtureTypesByStatement.put(
                        Integer.valueOf(i),
                        fixtureTypes);

                @SuppressWarnings("unchecked")
                List<VariableDeclarationFragment> fragments =
                        variableStatement.fragments();

                for (VariableDeclarationFragment fragment :
                        fragments) {

                    String name =
                            fragment.getName()
                                    .getIdentifier();

                    declarationIndexByVariable.put(
                            name,
                            Integer.valueOf(i));

                    if (!fixtureTypes.isEmpty()
                            || parameterNames.contains(
                                    name)) {

                        selected.add(
                                Integer.valueOf(i));

                        trackedVariables.add(
                                name);

                        coveredFixtureTypes.addAll(
                                fixtureTypes);
                    }
                }

                /*
                 * Generic wrappers such as Set<ReferenceTO> also count as
                 * fixture input even when the local variable has a generic
                 * collection type rather than the fixture itself.
                 */
                if (!fixtureTypes.isEmpty()) {
                    selected.add(
                            Integer.valueOf(i));

                    coveredFixtureTypes.addAll(
                            fixtureTypes);
                }
            }
        }

        if (selected.isEmpty()) {
            return null;
        }

        boolean changed = true;
        int rounds = 0;

        while (changed
                && rounds < 8) {

            changed = false;
            rounds++;

            for (int i = 0;
                    i < whenIndex;
                    i++) {

                Statement statement =
                        statements.get(i);

                String statementSource =
                        source(
                                source,
                                statement);

                if (statementSource.isEmpty()) {
                    continue;
                }

                boolean referencesTracked =
                        referencesAnyVariable(
                                statementSource,
                                trackedVariables);

                if (referencesTracked
                        && !selected.contains(
                                Integer.valueOf(i))) {

                    selected.add(
                            Integer.valueOf(i));
                    changed = true;
                }

                if (selected.contains(
                        Integer.valueOf(i))) {

                    for (Map.Entry<String, Integer> declarationEntry :
                            declarationIndexByVariable.entrySet()) {

                        String variable =
                                declarationEntry.getKey();

                        int declarationIndex =
                                declarationEntry.getValue()
                                        .intValue();

                        if (declarationIndex >= i
                                || !containsWord(
                                        statementSource,
                                        variable)) {

                            continue;
                        }

                        if (selected.add(
                                Integer.valueOf(
                                        declarationIndex))) {

                            changed = true;
                        }

                        if (trackedVariables.add(
                                variable)) {

                            changed = true;
                        }
                    }
                }
            }
        }

        List<Integer> indexes =
                new ArrayList<Integer>(
                        selected);

        Collections.sort(
                indexes);

        if (indexes.size()
                > MAX_STATEMENTS_PER_RECIPE) {

            indexes =
                    new ArrayList<Integer>(
                            indexes.subList(
                                    0,
                                    MAX_STATEMENTS_PER_RECIPE));
        }

        List<String> recipeStatements =
                new ArrayList<String>();

        Set<String> declaredVariables =
                new LinkedHashSet<String>();

        int score = 0;

        for (Integer indexValue :
                indexes) {

            int index =
                    indexValue.intValue();

            Statement statement =
                    statements.get(index);

            String text =
                    normalizeStatement(
                            source(
                                    source,
                                    statement));

            if (text.isEmpty()) {
                continue;
            }

            recipeStatements.add(
                    text);

            Set<String> fixtureTypes =
                    fixtureTypesByStatement.get(
                            indexValue);

            if (fixtureTypes != null) {
                coveredFixtureTypes.addAll(
                        fixtureTypes);
            }

            if (statement
                    instanceof VariableDeclarationStatement) {

                @SuppressWarnings("unchecked")
                List<VariableDeclarationFragment> fragments =
                        ((VariableDeclarationStatement)
                                statement)
                                .fragments();

                for (VariableDeclarationFragment fragment :
                        fragments) {

                    declaredVariables.add(
                            fragment.getName()
                                    .getIdentifier());
                }
            }

            String lower =
                    text.toLowerCase();

            if (lower.contains(
                    "insertnewentity(")
                    || lower.contains(
                            "persistentity(")
                    || lower.contains(
                            ".persist(")) {

                score += 14;
            }

            if (lower.contains(
                    "createdefault")
                    || lower.contains(
                            "createvalid")) {

                score += 12;
            }

            if (lower.contains(
                    ".set")) {

                score += 2;
            }
        }

        if (recipeStatements.isEmpty()
                || coveredFixtureTypes.isEmpty()) {

            return null;
        }

        score += coveredFixtureTypes.size()
                * 50;

        score += recipeStatements.size();

        return new TestHelperLearnedFixtureRecipe(
                testType.getHandleIdentifier(),
                testType.getElementName(),
                method.getElementName(),
                new ArrayList<String>(
                        coveredFixtureTypes),
                recipeStatements,
                new ArrayList<String>(
                        declaredVariables),
                score);
    }

    private static void collectSubjectExpressions(
            CompilationUnit ast,
            final Set<String> productionMethodNames,
            final Set<String> productionNameFragments,
            final Map<String, Integer> counts) {

        ast.accept(
                new ASTVisitor() {
                    @Override
                    public boolean visit(
                            MethodInvocation node) {

                        if (node.getExpression()
                                == null) {

                            return true;
                        }

                        String expression =
                                node.getExpression()
                                        .toString();

                        if (expression.isEmpty()
                                || "super".equals(
                                        expression)) {

                            return true;
                        }

                        boolean productionCall =
                                productionMethodNames.contains(
                                        node.getName()
                                                .getIdentifier());

                        String lowerExpression =
                                expression.toLowerCase();

                        if (!productionCall) {
                            for (String fragment :
                                    productionNameFragments) {

                                if (!fragment.isEmpty()
                                        && lowerExpression.contains(
                                                fragment)) {

                                    productionCall = true;
                                    break;
                                }
                            }
                        }

                        if (!productionCall) {
                            return true;
                        }

                        Integer current =
                                counts.get(
                                        expression);

                        counts.put(
                                expression,
                                Integer.valueOf(
                                        current == null
                                                ? 1
                                                : current.intValue()
                                                        + 1));

                        return true;
                    }
                });
    }

    private static int assertionStyle(
            String source) {

        if (source != null
                && source.contains(
                        "softly.assertThat(")) {

            return TestHelperLearnedTargetStyle.SOFT_ASSERTJ;
        }

        if (source != null
                && source.contains(
                        "assertThat(")) {

            return TestHelperLearnedTargetStyle.ASSERTJ;
        }

        return TestHelperLearnedTargetStyle.ASSERT_JUNIT;
    }

    private static String mostFrequent(
            Map<String, Integer> counts) {

        String best = "";
        int bestCount = 0;

        for (Map.Entry<String, Integer> entry :
                counts.entrySet()) {

            if (entry.getValue()
                    .intValue()
                    > bestCount) {

                best = entry.getKey();
                bestCount =
                        entry.getValue()
                                .intValue();
            }
        }

        return best;
    }

    private static boolean containsProductionInvocation(
            Statement statement,
            final Set<String> methodNames,
            final Set<String> productionNameFragments) {

        final boolean[] found =
                new boolean[] {
                        false
                };

        statement.accept(
                new ASTVisitor() {
                    @Override
                    public boolean visit(
                            MethodInvocation node) {

                        if (methodNames.contains(
                                node.getName()
                                        .getIdentifier())) {

                            found[0] = true;
                            return false;
                        }

                        if (node.getExpression() != null) {
                            String expression =
                                    node.getExpression()
                                            .toString()
                                            .toLowerCase();

                            for (String fragment :
                                    productionNameFragments) {

                                if (!fragment.isEmpty()
                                        && expression.contains(
                                                fragment)) {

                                    found[0] = true;
                                    return false;
                                }
                            }
                        }

                        return true;
                    }
                });

        return found[0];
    }

    private static Set<String> fixtureTypes(
            ITypeBinding binding,
            Set<String> requiredFixtures) {

        Set<String> result =
                new LinkedHashSet<String>();

        collectFixtureTypes(
                binding,
                requiredFixtures,
                result);

        return result;
    }

    private static void collectFixtureTypes(
            ITypeBinding binding,
            Set<String> requiredFixtures,
            Set<String> result) {

        if (binding == null) {
            return;
        }

        ITypeBinding erasure =
                binding.getErasure();

        String qualified =
                erasure == null
                        ? binding.getQualifiedName()
                        : erasure.getQualifiedName();

        if (requiredFixtures.contains(
                qualified)) {

            result.add(
                    qualified);
        }

        for (ITypeBinding argument :
                binding.getTypeArguments()) {

            collectFixtureTypes(
                    argument,
                    requiredFixtures,
                    result);
        }

        if (binding.isArray()) {
            collectFixtureTypes(
                    binding.getElementType(),
                    requiredFixtures,
                    result);
        }
    }

    private static MethodDeclaration methodDeclaration(
            IMethod method,
            CompilationUnit ast) {

        try {
            ISourceRange range =
                    method.getSourceRange();

            if (range == null) {
                return null;
            }

            ASTNode node =
                    NodeFinder.perform(
                            ast,
                            range.getOffset(),
                            range.getLength());

            while (node != null
                    && !(node
                            instanceof MethodDeclaration)) {

                node = node.getParent();
            }

            return node
                    instanceof MethodDeclaration
                            ? (MethodDeclaration)
                                    node
                            : null;

        } catch (JavaModelException e) {
            return null;
        }
    }

    private static CompilationUnit parse(
            ICompilationUnit unit,
            IProgressMonitor monitor) {

        ASTParser parser =
                ASTParser.newParser(
                        AST.getJLSLatest());

        parser.setSource(
                unit);
        parser.setResolveBindings(
                true);
        parser.setBindingsRecovery(
                true);

        return (CompilationUnit)
                parser.createAST(
                        monitor);
    }

    private static String source(
            String source,
            ASTNode node) {

        if (source == null
                || node == null
                || node.getStartPosition() < 0
                || node.getLength() <= 0
                || node.getStartPosition()
                        + node.getLength()
                        > source.length()) {

            return "";
        }

        return source.substring(
                node.getStartPosition(),
                node.getStartPosition()
                        + node.getLength());
    }

    private static String normalizeStatement(
            String value) {

        if (value == null) {
            return "";
        }

        String normalized =
                value.replace(
                        "\r\n",
                        "\n")
                        .replace(
                                '\r',
                                '\n')
                        .trim();

        String[] lines =
                normalized.split(
                        "\\n",
                        -1);

        int indent =
                Integer.MAX_VALUE;

        for (String line :
                lines) {

            if (line.trim()
                    .isEmpty()) {

                continue;
            }

            int count = 0;

            while (count < line.length()
                    && Character.isWhitespace(
                            line.charAt(
                                    count))) {

                count++;
            }

            indent =
                    Math.min(
                            indent,
                            count);
        }

        if (indent == Integer.MAX_VALUE
                || indent == 0) {

            return normalized;
        }

        StringBuilder out =
                new StringBuilder();

        for (int i = 0;
                i < lines.length;
                i++) {

            String line =
                    lines[i];

            if (line.length()
                    >= indent) {

                line =
                        line.substring(
                                indent);
            }

            out.append(
                    line);

            if (i + 1 < lines.length) {
                out.append('\n');
            }
        }

        return out.toString();
    }

    private static boolean referencesAnyVariable(
            String source,
            Set<String> variables) {

        for (String variable :
                variables) {

            if (containsWord(
                    source,
                    variable)) {

                return true;
            }
        }

        return false;
    }

    private static boolean containsWord(
            String source,
            String word) {

        if (source == null
                || word == null
                || word.isEmpty()) {

            return false;
        }

        return Pattern.compile(
                "(?<![A-Za-z0-9_$])"
                + Pattern.quote(
                        word)
                + "(?![A-Za-z0-9_$])")
                .matcher(
                        source)
                .find();
    }

    private static Set<String> requiredFixtureTypes(
            List<TestHelperAnalysis> analyses) {

        Set<String> result =
                new LinkedHashSet<String>();

        for (TestHelperAnalysis analysis :
                analyses) {

            for (TestHelperFixtureDependency fixture :
                    analysis.getFixtureDependencies()) {

                result.add(
                        fixture.getQualifiedType());
            }
        }

        return result;
    }

    private static Set<String> parameterNames(
            List<TestHelperAnalysis> analyses) {

        Set<String> result =
                new LinkedHashSet<String>();

        for (TestHelperAnalysis analysis :
                analyses) {

            for (TestHelperParameter parameter :
                    analysis.getParameters()) {

                result.add(
                        parameter.getName());
            }
        }

        return result;
    }

    private static Set<String> productionNameFragments(
            List<TestHelperAnalysis> analyses) {

        Set<String> result =
                new LinkedHashSet<String>();

        for (TestHelperAnalysis analysis :
                analyses) {

            String simple =
                    analysis.getSimpleDeclaringType()
                            .toLowerCase();

            if (simple.endsWith(
                    "impl")
                    && simple.length() > 4) {

                simple =
                        simple.substring(
                                0,
                                simple.length() - 4);
            }

            result.add(
                    simple);
        }

        return result;
    }

    private static Set<String> productionMethodNames(
            List<TestHelperAnalysis> analyses) {

        Set<String> result =
                new LinkedHashSet<String>();

        for (TestHelperAnalysis analysis :
                analyses) {

            result.add(
                    analysis.getMethodName());
        }

        return result;
    }
}
