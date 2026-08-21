package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;

public final class FeatureTestAuditService {

    private static final int MAX_PRODUCTION_CLASSES = 250;
    private static final int MAX_METHODS_PER_CLASS = 160;
    private static final int MAX_TEST_COMPILATION_UNITS_PER_CLASS = 16;
    private static final int MAX_RELATED_SUBTYPES = 64;
    private static final int MAX_EXTRA_SUBTYPE_TEST_LOOKUPS = 8;

    private FeatureTestAuditService() {
    }

    public static FeatureTestAuditReport scan(
            String requestedFeature,
            IProgressMonitor monitor) {

        String feature =
                requestedFeature == null
                        ? ""
                        : requestedFeature.trim();

        if (feature.isEmpty()) {
            return new FeatureTestAuditReport(
                    "",
                    Collections
                            .<FeatureTestClassStatus>
                                    emptyList(),
                    false);
        }

        final List<IType> productionTypes =
                new ArrayList<IType>();

        final boolean[] truncated =
                new boolean[] {
                        false
                };

        SearchEngine engine =
                new SearchEngine();

        IJavaSearchScope scope =
                SearchEngine
                        .createWorkspaceScope();

        TypeNameRequestor requestor =
                new TypeNameRequestor() {
                    @Override
                    public void acceptType(
                            int modifiers,
                            char[] packageName,
                            char[] simpleTypeName,
                            char[][] enclosingTypeNames,
                            String path) {

                        if (productionTypes.size()
                                >= MAX_PRODUCTION_CLASSES) {

                            truncated[0] = true;
                            return;
                        }

                        if (enclosingTypeNames != null
                                && enclosingTypeNames.length > 0) {

                            return;
                        }

                        if (Flags.isInterface(
                                modifiers)
                                || Flags.isAnnotation(
                                        modifiers)) {

                            return;
                        }

                        IType type =
                                sourceType(
                                        path,
                                        new String(
                                                simpleTypeName));

                        if (type == null
                                || !type.exists()
                                || !isRelevantProductionType(
                                        type,
                                        feature)) {

                            return;
                        }

                        productionTypes.add(
                                type);
                    }
                };

        try {
            engine.searchAllTypeNames(
                    null,
                    SearchPattern.R_EXACT_MATCH,
                    ("*" + feature + "*")
                            .toCharArray(),
                    SearchPattern.R_PATTERN_MATCH,
                    IJavaSearchConstants.TYPE,
                    scope,
                    requestor,
                    IJavaSearchConstants
                            .WAIT_UNTIL_READY_TO_SEARCH,
                    monitor);

        } catch (JavaModelException e) {
            return new FeatureTestAuditReport(
                    feature,
                    Collections
                            .<FeatureTestClassStatus>
                                    emptyList(),
                    false);
        }

        Collections.sort(
                productionTypes,
                new Comparator<IType>() {
                    @Override
                    public int compare(
                            IType left,
                            IType right) {

                        return left
                                .getFullyQualifiedName()
                                .compareToIgnoreCase(
                                        right.getFullyQualifiedName());
                    }
                });

        List<FeatureTestClassStatus> result =
                new ArrayList<FeatureTestClassStatus>();

        for (IType production :
                productionTypes) {

            if (monitor != null
                    && monitor.isCanceled()) {

                break;
            }

            FeatureTestClassStatus status =
                    analyzeType(
                            production,
                            monitor);

            if (status != null) {
                result.add(
                        status);
            }
        }

        Collections.sort(
                result,
                new Comparator<FeatureTestClassStatus>() {
                    @Override
                    public int compare(
                            FeatureTestClassStatus left,
                            FeatureTestClassStatus right) {

                        boolean leftMissing =
                                left.getUntestedCount() > 0;

                        boolean rightMissing =
                                right.getUntestedCount() > 0;

                        if (leftMissing != rightMissing) {
                            return leftMissing
                                    ? -1
                                    : 1;
                        }

                        if (left.hasTestClass()
                                != right.hasTestClass()) {

                            return left.hasTestClass()
                                    ? 1
                                    : -1;
                        }

                        return left.getProductionType()
                                .getElementName()
                                .compareToIgnoreCase(
                                        right.getProductionType()
                                                .getElementName());
                    }
                });

        return new FeatureTestAuditReport(
                feature,
                result,
                truncated[0]);
    }

    private static FeatureTestClassStatus analyzeType(
            IType production,
            IProgressMonitor monitor) {

        IFile productionFile =
                production.getResource()
                        instanceof IFile
                        ? (IFile)
                                production.getResource()
                        : null;

        if (productionFile == null) {
            return null;
        }

        String role =
                FlowCategoryClassifier
                        .classify(
                                productionFile);

        List<IMethod> methods =
                productionMethods(
                        production);

        RelatedTypeScope relatedTypes =
                relatedTypeScope(
                        production,
                        monitor);

        List<TestTargetCandidate> tests =
                relatedTests(
                        production,
                        relatedTypes,
                        monitor);

        Map<String, LinkedHashSet<String>>
                references =
                        new LinkedHashMap<String, LinkedHashSet<String>>();

        for (IMethod method :
                methods) {

            references.put(
                    methodKey(
                            method),
                    new LinkedHashSet<String>());
        }

        Set<String> acceptedDeclaringTypes =
                relatedTypes
                        .acceptedDeclaringTypes;

        int parsedUnits = 0;

        Set<String> parsedHandles =
                new HashSet<String>();

        for (TestTargetCandidate candidate :
                tests) {

            if (monitor != null
                    && monitor.isCanceled()) {

                break;
            }

            IType testType =
                    candidate.getType();

            if (testType == null
                    || !testType.exists()) {

                continue;
            }

            ICompilationUnit unit =
                    testType.getCompilationUnit();

            if (unit == null
                    || !unit.exists()
                    || !parsedHandles.add(
                            unit.getHandleIdentifier())) {

                continue;
            }

            if (parsedUnits
                    >= MAX_TEST_COMPILATION_UNITS_PER_CLASS) {

                break;
            }

            parsedUnits++;

            collectReferences(
                    unit,
                    testType,
                    production.getFullyQualifiedName(),
                    acceptedDeclaringTypes,
                    references,
                    monitor);
        }

        List<FeatureTestMethodStatus> methodStatuses =
                new ArrayList<FeatureTestMethodStatus>();

        for (IMethod method :
                methods) {

            LinkedHashSet<String> refs =
                    references.get(
                            methodKey(
                                    method));

            methodStatuses.add(
                    new FeatureTestMethodStatus(
                            method,
                            refs == null
                                    ? Collections
                                            .<String>emptyList()
                                    : new ArrayList<String>(
                                            refs)));
        }

        return new FeatureTestClassStatus(
                production,
                role,
                tests,
                methodStatuses);
    }

    private static List<IMethod> productionMethods(
            IType type) {

        List<IMethod> result =
                new ArrayList<IMethod>();

        try {
            for (IMethod method :
                    type.getMethods()) {

                if (method.isConstructor()) {
                    continue;
                }

                int flags =
                        method.getFlags();

                /*
                 * Private helpers are implementation detail. The audit is
                 * intentionally about externally testable production
                 * behavior. Package-private/public/protected methods stay in.
                 */
                if (Flags.isPrivate(
                        flags)
                        || method.isMainMethod()) {

                    continue;
                }

                result.add(
                        method);

                if (result.size()
                        >= MAX_METHODS_PER_CLASS) {

                    break;
                }
            }

        } catch (JavaModelException e) {
            return Collections.emptyList();
        }

        Collections.sort(
                result,
                new Comparator<IMethod>() {
                    @Override
                    public int compare(
                            IMethod left,
                            IMethod right) {

                        try {
                            return left.getSourceRange()
                                    .getOffset()
                                    - right.getSourceRange()
                                            .getOffset();

                        } catch (JavaModelException e) {
                            return left.getElementName()
                                    .compareToIgnoreCase(
                                            right.getElementName());
                        }
                    }
                });

        return result;
    }

    private static void collectReferences(
            ICompilationUnit unit,
            final IType testType,
            final String primaryProductionType,
            final Set<String> acceptedDeclaringTypes,
            final Map<String, LinkedHashSet<String>>
                    references,
            IProgressMonitor monitor) {

        ASTParser parser =
                ASTParser.newParser(
                        AST.JLS8);

        parser.setSource(
                unit);

        parser.setResolveBindings(
                true);

        parser.setBindingsRecovery(
                true);

        CompilationUnit ast =
                (CompilationUnit)
                        parser.createAST(
                                monitor);

        ast.accept(
                new ASTVisitor() {

                    private String currentTestMethod =
                            "<initializer/helper>";

                    @Override
                    public boolean visit(
                            MethodDeclaration node) {

                        currentTestMethod =
                                node.getName()
                                        .getIdentifier();

                        return true;
                    }

                    @Override
                    public void endVisit(
                            MethodDeclaration node) {

                        currentTestMethod =
                                "<initializer/helper>";
                    }

                    @Override
                    public boolean visit(
                            MethodInvocation node) {

                        record(
                                node.resolveMethodBinding());

                        return true;
                    }

                    @Override
                    public boolean visit(
                            SuperMethodInvocation node) {

                        record(
                                node.resolveMethodBinding());

                        return true;
                    }

                    private void record(
                            IMethodBinding binding) {

                        if (binding == null
                                || binding.getDeclaringClass()
                                        == null) {

                            return;
                        }

                        IMethodBinding declaration =
                                binding.getMethodDeclaration();

                        ITypeBinding declaring =
                                declaration
                                        .getDeclaringClass();

                        if (declaring == null
                                || !acceptedDeclaringTypes
                                        .contains(
                                                declaring
                                                        .getErasure()
                                                        .getQualifiedName())) {

                            return;
                        }

                        String key =
                                methodKey(
                                        declaration);

                        LinkedHashSet<String> refs =
                                references.get(
                                        key);

                        if (refs == null) {
                            /*
                             * Calls through an interface/supertype can differ
                             * slightly in generic signatures. Fall back to
                             * name+arity, but only inside the accepted target
                             * type hierarchy.
                             */
                            String prefix =
                                    declaration.getName()
                                    + "#"
                                    + declaration
                                            .getParameterTypes()
                                            .length
                                    + "#";

                            for (Map.Entry<String, LinkedHashSet<String>>
                                    entry :
                                            references.entrySet()) {

                                if (entry.getKey()
                                        .startsWith(
                                                prefix)) {

                                    refs =
                                            entry.getValue();

                                    break;
                                }
                            }
                        }

                        if (refs != null) {
                            String declaringName =
                                    declaring
                                            .getErasure()
                                            .getQualifiedName();

                            StringBuilder reference =
                                    new StringBuilder();

                            reference.append(
                                    testType.getElementName())
                                    .append('.')
                                    .append(
                                            currentTestMethod)
                                    .append(
                                            "(...)");

                            if (primaryProductionType != null
                                    && !primaryProductionType
                                            .equals(
                                                    declaringName)) {

                                reference.append(
                                        " [via ")
                                        .append(
                                                declaring
                                                        .getErasure()
                                                        .getName())
                                        .append(']');
                            }

                            refs.add(
                                    reference.toString());
                        }
                    }
                });
    }


    private static RelatedTypeScope relatedTypeScope(
            IType type,
            IProgressMonitor monitor) {

        Set<String> accepted =
                new LinkedHashSet<String>();

        List<IType> subtypes =
                new ArrayList<IType>();

        accepted.add(
                type.getFullyQualifiedName());

        try {
            /*
             * A full hierarchy is intentionally built only during the
             * on-demand Feature Test Audit. This lets a test that calls an
             * implementation/subclass count toward the matching base
             * production method as well.
             */
            ITypeHierarchy hierarchy =
                    type.newTypeHierarchy(
                            monitor);

            for (IType superType :
                    hierarchy.getAllSupertypes(
                            type)) {

                accepted.add(
                        superType
                                .getFullyQualifiedName());
            }

            int subtypeCount = 0;

            for (IType subtype :
                    hierarchy.getAllSubtypes(
                            type)) {

                if (subtypeCount
                        >= MAX_RELATED_SUBTYPES) {

                    break;
                }

                accepted.add(
                        subtype
                                .getFullyQualifiedName());

                if (isWorkspaceSourceType(
                        subtype)) {

                    subtypes.add(
                            subtype);
                }

                subtypeCount++;
            }

        } catch (JavaModelException e) {
            // Exact production type is still useful if hierarchy creation fails.
        }

        return new RelatedTypeScope(
                accepted,
                subtypes);
    }

    private static List<TestTargetCandidate> relatedTests(
            IType production,
            RelatedTypeScope relatedTypes,
            IProgressMonitor monitor) {

        Map<String, TestTargetCandidate> unique =
                new LinkedHashMap<String, TestTargetCandidate>();

        addCandidates(
                unique,
                TestTargetFinder.find(
                        production,
                        monitor));

        /*
         * `PostbuchISPImplTest` is already found by the `PostbuchISP*` prefix
         * lookup, so no extra search is needed for the common case.
         *
         * Additional subtype searches are only used when an implementation
         * has a different leading name, e.g. `DefaultPostbuchISPImplTest`.
         */
        int extraLookups = 0;

        String productionSimple =
                production.getElementName();

        for (IType subtype :
                relatedTypes.subtypes) {

            if (monitor != null
                    && monitor.isCanceled()) {

                break;
            }

            if (extraLookups
                    >= MAX_EXTRA_SUBTYPE_TEST_LOOKUPS) {

                break;
            }

            if (subtype.getElementName()
                    .startsWith(
                            productionSimple)) {

                continue;
            }

            addCandidates(
                    unique,
                    TestTargetFinder.find(
                            subtype,
                            monitor));

            extraLookups++;
        }

        List<TestTargetCandidate> result =
                new ArrayList<TestTargetCandidate>(
                        unique.values());

        Collections.sort(
                result,
                new Comparator<TestTargetCandidate>() {
                    @Override
                    public int compare(
                            TestTargetCandidate left,
                            TestTargetCandidate right) {

                        int score =
                                right.getScore()
                                        - left.getScore();

                        if (score != 0) {
                            return score;
                        }

                        return left.getLabel()
                                .compareToIgnoreCase(
                                        right.getLabel());
                    }
                });

        return result;
    }

    private static void addCandidates(
            Map<String, TestTargetCandidate> target,
            List<TestTargetCandidate> candidates) {

        if (candidates == null) {
            return;
        }

        for (TestTargetCandidate candidate :
                candidates) {

            if (candidate == null
                    || candidate.getType()
                            == null) {

                continue;
            }

            String handle =
                    candidate.getType()
                            .getHandleIdentifier();

            TestTargetCandidate existing =
                    target.get(
                            handle);

            if (existing == null
                    || candidate.getScore()
                            > existing.getScore()) {

                target.put(
                        handle,
                        candidate);
            }
        }
    }

    private static boolean isWorkspaceSourceType(
            IType type) {

        if (type == null
                || !type.exists()) {

            return false;
        }

        IResource resource =
                type.getResource();

        return resource
                instanceof IFile
                && resource.exists()
                && type.getCompilationUnit()
                        != null;
    }

    private static boolean isRelevantProductionType(
            IType type,
            String feature) {

        if (type == null
                || !type.exists()) {

            return false;
        }

        String simple =
                type.getElementName();

        if (simple.toLowerCase(
                Locale.ENGLISH)
                .indexOf(
                        feature.toLowerCase(
                                Locale.ENGLISH))
                < 0) {

            return false;
        }

        if (simple.startsWith(
                "I" + feature)
                || simple.endsWith(
                        "Entity")
                || simple.endsWith(
                        "TO")
                || simple.endsWith(
                        "DTO")
                || simple.endsWith(
                        "Dto")) {

            return false;
        }

        IFile file =
                type.getResource()
                        instanceof IFile
                        ? (IFile)
                                type.getResource()
                        : null;

        if (file == null) {
            return false;
        }

        String category =
                FlowCategoryClassifier
                        .classify(
                                file);

        if (FlowCategoryClassifier.TEST
                .equals(
                        category)
                || FlowCategoryClassifier.PERSISTENCE
                        .equals(
                                category)
                || FlowCategoryClassifier.TO
                        .equals(
                                category)
                || FlowCategoryClassifier.JAXB
                        .equals(
                                category)) {

            return false;
        }

        return FlowCategoryClassifier.CONTROLLER
                .equals(
                        category)
                || FlowCategoryClassifier.BEAN
                        .equals(
                                category)
                || FlowCategoryClassifier.ISP
                        .equals(
                                category)
                || FlowCategoryClassifier.DSP
                        .equals(
                                category);
    }

    private static String methodKey(
            IMethod method) {

        StringBuilder key =
                new StringBuilder();

        key.append(
                method.getElementName())
                .append('#')
                .append(
                        method.getNumberOfParameters())
                .append('#');

        for (String parameter :
                method.getParameterTypes()) {

            key.append(
                    simpleSignatureType(
                            parameter))
                    .append(';');
        }

        return key.toString();
    }

    private static String methodKey(
            IMethodBinding method) {

        StringBuilder key =
                new StringBuilder();

        key.append(
                method.getName())
                .append('#')
                .append(
                        method.getParameterTypes()
                                .length)
                .append('#');

        for (ITypeBinding parameter :
                method.getParameterTypes()) {

            key.append(
                    simpleBindingType(
                            parameter))
                    .append(';');
        }

        return key.toString();
    }

    private static String simpleSignatureType(
            String signature) {

        String erased =
                Signature.getTypeErasure(
                        signature);

        String text =
                Signature.toString(
                        erased);

        int generic =
                text.indexOf('<');

        if (generic >= 0) {
            text =
                    text.substring(
                            0,
                            generic);
        }

        int dot =
                text.lastIndexOf('.');

        return dot >= 0
                ? text.substring(
                        dot + 1)
                : text;
    }

    private static String simpleBindingType(
            ITypeBinding binding) {

        if (binding == null) {
            return "Object";
        }

        if (binding.isArray()) {
            return simpleBindingType(
                    binding.getElementType())
                    + "[]";
        }

        ITypeBinding erased =
                binding.getErasure();

        String name =
                erased == null
                        ? binding.getName()
                        : erased.getName();

        return name == null
                ? "Object"
                : name;
    }

    private static IType sourceType(
            String path,
            String simpleName) {

        if (path == null
                || path.isEmpty()) {

            return null;
        }

        IResource resource =
                ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .findMember(
                                new org.eclipse.core.runtime.Path(
                                        path));

        if (!(resource
                instanceof IFile)) {

            return null;
        }

        ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(
                        (IFile)
                                resource);

        if (unit == null
                || !unit.exists()) {

            return null;
        }

        IType type =
                unit.getType(
                        simpleName);

        return type.exists()
                ? type
                : null;
    }

    private static final class RelatedTypeScope {

        final Set<String> acceptedDeclaringTypes;
        final List<IType> subtypes;

        RelatedTypeScope(
                Set<String> acceptedDeclaringTypes,
                List<IType> subtypes) {

            this.acceptedDeclaringTypes =
                    acceptedDeclaringTypes;

            this.subtypes =
                    subtypes;
        }
    }


}
