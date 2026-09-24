package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeLiteral;

public final class TestHelperAnalyzer {

    private static final int MAX_HELPER_DEPTH = 4;
    private static final int MAX_METHODS = 30;
    private static final int MAX_COMPILATION_UNITS = 12;
    private static final int MAX_FIXTURE_TYPES = 40;
    private static final int MAX_ENTITY_RELATION_DEPTH = 3;
    private static final int MAX_FIXTURE_SOURCE_TYPES = 12;

    private TestHelperAnalyzer() {
    }

    public static TestHelperAnalysis analyze(
            IMethod selected,
            IProgressMonitor monitor) {

        if (selected == null
                || !selected.exists()) {

            return empty();
        }

        Context context =
                new Context(
                        selected,
                        monitor);

        MethodAst root =
                context.methodAst(
                        selected);

        if (root == null) {
            return empty();
        }

        IMethodBinding rootBinding =
                root.declaration
                        .resolveBinding();

        String declaringType =
                selected.getDeclaringType()
                        == null
                                ? ""
                                : selected.getDeclaringType()
                                        .getFullyQualifiedName();

        String returnType =
                rootBinding == null
                        ? "Object"
                        : typeName(
                                rootBinding.getReturnType());

        List<TestHelperParameter> parameters =
                parametersOf(
                        root.declaration);

        if (rootBinding != null) {
            context.addFixtureBinding(
                    rootBinding.getReturnType(),
                    "Rückgabetyp von "
                            + selected.getElementName(),
                    0);

            ITypeBinding[] parameterBindings =
                    rootBinding.getParameterTypes();

            for (int i = 0;
                    i < parameterBindings.length;
                    i++) {

                String parameterName =
                        i < parameters.size()
                                ? parameters.get(i)
                                        .getName()
                                : "Parameter "
                                        + (i + 1);

                context.addFixtureBinding(
                        parameterBindings[i],
                        "Methodenparameter "
                                + parameterName,
                        0);
            }
        }

        scanMethod(
                selected,
                0,
                context);

        context.expandFixtureDependencies();

        List<TestHelperDependency> dependencies =
                context.dependencies();

        return new TestHelperAnalysis(
                declaringType,
                selected.getElementName(),
                returnType,
                parameters,
                dependencies,
                context.fixtureDependencies(),
                context.jpaDetected,
                context.jpaWriteDetected,
                context.jpaReadDetected,
                context.truncated);
    }

    private static void scanMethod(
            IMethod method,
            int depth,
            final Context context) {

        if (method == null
                || !method.exists()
                || context.isCanceled()) {

            return;
        }

        if (depth > MAX_HELPER_DEPTH
                || context.inspectedMethods
                        >= MAX_METHODS) {

            context.truncated = true;
            return;
        }

        if (!context.visited
                .add(
                        method.getHandleIdentifier())) {

            return;
        }

        MethodAst target =
                context.methodAst(
                        method);

        if (target == null) {
            return;
        }

        context.inspectedMethods++;

        target.declaration.accept(
                new ASTVisitor() {
                    @Override
                    public boolean visit(
                            MethodInvocation node) {

                        inspectInvocation(
                                node,
                                node.resolveMethodBinding(),
                                node.getExpression(),
                                node.getName()
                                        .getIdentifier(),
                                depth,
                                context);

                        return !context.isCanceled();
                    }

                    @Override
                    public boolean visit(
                            SuperMethodInvocation node) {

                        inspectInvocation(
                                node,
                                node.resolveMethodBinding(),
                                null,
                                node.getName()
                                        .getIdentifier(),
                                depth,
                                context);

                        return !context.isCanceled();
                    }

                    @Override
                    public boolean visit(
                            ClassInstanceCreation node) {

                        context.addFixtureBinding(
                                node.resolveTypeBinding(),
                                "Erzeugt in "
                                        + method.getElementName()
                                        + "(...)" ,
                                0);

                        return !context.isCanceled();
                    }

                    @Override
                    public boolean visit(
                            TypeLiteral node) {

                        context.addFixtureBinding(
                                node.getType()
                                        .resolveBinding(),
                                "Als .class in "
                                        + method.getElementName()
                                        + "(...) verwendet",
                                0);

                        return !context.isCanceled();
                    }
                });
    }

    private static void inspectInvocation(
            ASTNode node,
            IMethodBinding binding,
            Expression expression,
            String methodName,
            int depth,
            Context context) {

        if (binding == null
                || context.isCanceled()) {

            return;
        }

        detectJpa(
                binding,
                methodName,
                context);

        IVariableBinding field =
                fieldBinding(
                        expression);

        if (field != null
                && field.isField()
                && !Modifier.isStatic(
                        field.getModifiers())
                && !field.getType()
                        .isPrimitive()) {

            context.addDependency(
                    field,
                    binding);

            return;
        }

        IMethod target =
                javaMethod(
                        binding);

        if (target == null
                || !isWorkspaceSource(
                        target)
                || !context.isInternalHelper(
                        target.getDeclaringType())) {

            return;
        }

        scanMethod(
                target,
                depth + 1,
                context);
    }

    private static void detectJpa(
            IMethodBinding binding,
            String methodName,
            Context context) {

        ITypeBinding declaring =
                binding.getDeclaringClass();

        String qualified =
                declaring == null
                        ? ""
                        : declaring.getErasure()
                                .getQualifiedName();

        String simple =
                declaring == null
                        ? ""
                        : declaring.getErasure()
                                .getName();

        boolean persistenceType =
                qualified.startsWith(
                        "javax.persistence.")
                || qualified.startsWith(
                        "jakarta.persistence.")
                || "EntityManager".equals(
                        simple)
                || "Query".equals(simple)
                || "TypedQuery".equals(
                        simple);

        if (!persistenceType) {
            return;
        }

        context.jpaDetected = true;

        String name =
                methodName == null
                        ? ""
                        : methodName.toLowerCase();

        if ("persist".equals(name)
                || "merge".equals(name)
                || "remove".equals(name)
                || "flush".equals(name)
                || "executeupdate".equals(
                        name)) {

            context.jpaWriteDetected =
                    true;
        }

        if ("find".equals(name)
                || "getreference".equals(name)
                || "createquery".equals(name)
                || "createnamedquery".equals(
                        name)
                || "createnativequery".equals(
                        name)
                || "getresultlist".equals(name)
                || "getsingleresult".equals(
                        name)) {

            context.jpaReadDetected =
                    true;
        }
    }

    private static IVariableBinding fieldBinding(
            Expression expression) {

        if (expression == null) {
            return null;
        }

        if (expression
                instanceof SimpleName) {

            IBinding binding =
                    ((SimpleName)
                            expression)
                            .resolveBinding();

            return binding
                    instanceof IVariableBinding
                            ? (IVariableBinding)
                                    binding
                            : null;
        }

        if (expression
                instanceof FieldAccess) {

            return ((FieldAccess)
                    expression)
                    .resolveFieldBinding();
        }

        if (expression
                instanceof QualifiedName) {

            IBinding binding =
                    ((QualifiedName)
                            expression)
                            .resolveBinding();

            return binding
                    instanceof IVariableBinding
                            ? (IVariableBinding)
                                    binding
                            : null;
        }

        return null;
    }

    private static IMethod javaMethod(
            IMethodBinding binding) {

        IJavaElement element =
                binding.getMethodDeclaration()
                        .getJavaElement();

        return element
                instanceof IMethod
                        ? (IMethod)
                                element
                        : null;
    }

    private static boolean isWorkspaceSource(
            IMethod method) {

        if (method == null) {
            return false;
        }

        IResource resource =
                method.getResource();

        return resource
                instanceof IFile
                && resource.exists()
                && method.getAncestor(
                        IJavaElement.COMPILATION_UNIT)
                        instanceof ICompilationUnit;
    }

    private static List<TestHelperParameter> parametersOf(
            MethodDeclaration declaration) {

        List<TestHelperParameter> result =
                new ArrayList<TestHelperParameter>();

        for (Object value :
                declaration.parameters()) {

            if (!(value
                    instanceof SingleVariableDeclaration)) {

                continue;
            }

            SingleVariableDeclaration parameter =
                    (SingleVariableDeclaration)
                            value;

            IVariableBinding binding =
                    parameter.resolveBinding();

            String type =
                    binding == null
                            ? parameter.getType()
                                    .toString()
                            : typeName(
                                    binding.getType());

            if (parameter.isVarargs()) {
                type += "...";
            }

            result.add(
                    new TestHelperParameter(
                            type,
                            parameter.getName()
                                    .getIdentifier()));
        }

        return result;
    }

    private static String typeName(
            ITypeBinding binding) {

        if (binding == null) {
            return "Object";
        }

        if (binding.isArray()) {
            return typeName(
                    binding.getElementType())
                    + repeat(
                            "[]",
                            binding.getDimensions());
        }

        if (binding.isPrimitive()) {
            return binding.getName();
        }

        if (binding.isTypeVariable()
                || binding.isWildcardType()
                || binding.isCapture()) {

            return "Object";
        }

        ITypeBinding erasure =
                binding.getErasure();

        String name =
                erasure == null
                        ? binding.getName()
                        : erasure.getName();

        return name == null
                || name.isEmpty()
                        ? "Object"
                        : name;
    }

    private static String repeat(
            String value,
            int count) {

        StringBuilder result =
                new StringBuilder();

        for (int i = 0;
                i < count;
                i++) {

            result.append(value);
        }

        return result.toString();
    }

    private static TestHelperAnalysis empty() {
        return new TestHelperAnalysis(
                "",
                "",
                "Object",
                Collections
                        .<TestHelperParameter>
                                emptyList(),
                Collections
                        .<TestHelperDependency>
                                emptyList(),
                false,
                false,
                false,
                false);
    }

    private static final class DependencyBuilder {

        final String fieldName;
        final String fieldType;

        final Map<String, TestHelperInvocation>
                invocations =
                        new LinkedHashMap<String, TestHelperInvocation>();

        DependencyBuilder(
                String fieldName,
                String fieldType) {

            this.fieldName = fieldName;
            this.fieldType = fieldType;
        }

        void add(
                IMethodBinding method) {

            List<String> parameterTypes =
                    new ArrayList<String>();

            for (ITypeBinding parameter :
                    method.getParameterTypes()) {

                parameterTypes.add(
                        typeName(
                                parameter));
            }

            ITypeBinding returnBinding =
                    method.getReturnType();

            String returnType =
                    typeName(
                            returnBinding);

            boolean voidReturn =
                    returnBinding != null
                    && returnBinding
                            .isPrimitive()
                    && "void".equals(
                            returnBinding
                                    .getName());

            TestHelperInvocation invocation =
                    new TestHelperInvocation(
                            method.getName(),
                            returnType,
                            parameterTypes,
                            voidReturn);

            invocations.put(
                    invocation.signatureKey(),
                    invocation);
        }

        TestHelperDependency build() {
            return new TestHelperDependency(
                    fieldName,
                    fieldType,
                    new ArrayList<TestHelperInvocation>(
                            invocations.values()));
        }
    }

    private static final class FixtureBuilder {

        final String qualifiedType;
        final String kind;
        final Set<String> reasons =
                new LinkedHashSet<String>();

        FixtureBuilder(
                String qualifiedType,
                String kind) {

            this.qualifiedType =
                    qualifiedType;
            this.kind = kind;
        }

        void addReason(
                String reason) {

            if (reason != null
                    && !reason.trim()
                            .isEmpty()) {

                reasons.add(
                        reason.trim());
            }
        }

        TestHelperFixtureDependency build() {
            return new TestHelperFixtureDependency(
                    qualifiedType,
                    kind,
                    new ArrayList<String>(
                            reasons));
        }
    }

    private static final class Context {

        final IMethod selected;
        final IProgressMonitor monitor;

        final Set<String> visited =
                new LinkedHashSet<String>();

        final Map<String, CompilationUnit>
                astByUnit =
                        new LinkedHashMap<String, CompilationUnit>();

        final Map<String, DependencyBuilder>
                dependencyByField =
                        new LinkedHashMap<String, DependencyBuilder>();

        final Map<String, FixtureBuilder>
                fixtureByType =
                        new LinkedHashMap<String, FixtureBuilder>();

        final Set<String> fixtureSourceScanned =
                new LinkedHashSet<String>();

        final Map<String, Integer> entityDepthByType =
                new LinkedHashMap<String, Integer>();

        ITypeHierarchy hierarchy;

        boolean jpaDetected;
        boolean jpaWriteDetected;
        boolean jpaReadDetected;
        boolean truncated;

        int inspectedMethods;

        Context(
                IMethod selected,
                IProgressMonitor monitor) {

            this.selected = selected;
            this.monitor = monitor;
        }

        boolean isCanceled() {
            return monitor != null
                    && monitor.isCanceled();
        }

        boolean isInternalHelper(
                IType type) {

            IType root =
                    selected.getDeclaringType();

            if (type == null
                    || root == null) {

                return false;
            }

            if (type.equals(root)) {
                return true;
            }

            try {
                if (hierarchy == null) {
                    hierarchy =
                            root.newSupertypeHierarchy(
                                    monitor);
                }

                for (IType superType :
                        hierarchy.getAllSuperclasses(
                                root)) {

                    if (type.equals(
                            superType)) {

                        return true;
                    }
                }

            } catch (JavaModelException e) {
                return false;
            }

            return false;
        }

        void addDependency(
                IVariableBinding field,
                IMethodBinding method) {

            String fieldName =
                    field.getName();

            DependencyBuilder builder =
                    dependencyByField.get(
                            fieldName);

            if (builder == null) {
                builder =
                        new DependencyBuilder(
                                fieldName,
                                typeName(
                                        field.getType()));

                dependencyByField.put(
                        fieldName,
                        builder);
            }

            builder.add(
                    method);
        }

        void addFixtureBinding(
                ITypeBinding binding,
                String reason,
                int entityDepth) {

            if (binding == null
                    || isCanceled()) {

                return;
            }

            if (binding.isArray()) {
                addFixtureBinding(
                        binding.getElementType(),
                        reason,
                        entityDepth);
                return;
            }

            for (ITypeBinding argument :
                    binding.getTypeArguments()) {

                addFixtureBinding(
                        argument,
                        reason,
                        entityDepth);
            }

            ITypeBinding erasure =
                    binding.getErasure();

            IJavaElement javaElement =
                    erasure == null
                            ? binding.getJavaElement()
                            : erasure.getJavaElement();

            if (javaElement
                    instanceof IType) {

                addFixtureType(
                        (IType) javaElement,
                        reason,
                        entityDepth);
            }
        }

        void addFixtureType(
                IType type,
                String reason,
                int entityDepth) {

            if (type == null
                    || !type.exists()
                    || fixtureByType.size()
                            >= MAX_FIXTURE_TYPES) {

                if (fixtureByType.size()
                        >= MAX_FIXTURE_TYPES) {
                    truncated = true;
                }

                return;
            }

            IType root =
                    selected.getDeclaringType();

            if (root != null
                    && root.equals(type)) {

                return;
            }

            IResource resource =
                    type.getResource();

            if (!(resource instanceof IFile)
                    || !resource.exists()
                    || type.getCompilationUnit()
                            == null) {

                return;
            }

            String qualified =
                    type.getFullyQualifiedName();

            String kind =
                    fixtureKind(
                            type);

            if (TestHelperFixtureDependency.TYPE
                    .equals(kind)) {

                return;
            }

            FixtureBuilder builder =
                    fixtureByType.get(
                            qualified);

            if (builder == null) {
                builder =
                        new FixtureBuilder(
                                qualified,
                                kind);

                fixtureByType.put(
                        qualified,
                        builder);
            }

            builder.addReason(
                    reason);

            if (TestHelperFixtureDependency.ENTITY
                    .equals(kind)) {

                jpaDetected = true;

                Integer existingDepth =
                        entityDepthByType.get(
                                qualified);

                if (existingDepth == null
                        || entityDepth
                                < existingDepth.intValue()) {

                    entityDepthByType.put(
                            qualified,
                            Integer.valueOf(
                                    entityDepth));
                }
            }
        }

        void expandFixtureDependencies() {
            boolean changed = true;
            int rounds = 0;

            while (changed
                    && !isCanceled()
                    && rounds < MAX_FIXTURE_TYPES) {

                changed = false;
                rounds++;

                List<FixtureBuilder> snapshot =
                        new ArrayList<FixtureBuilder>(
                                fixtureByType.values());

                int before =
                        fixtureByType.size();

                for (FixtureBuilder fixture :
                        snapshot) {

                    IType type =
                            findType(
                                    fixture.qualifiedType);

                    if (type == null) {
                        continue;
                    }

                    if (TestHelperFixtureDependency.QUERY_BUILDER
                            .equals(
                                    fixture.kind)) {

                        scanFixtureSourceType(
                                type);
                    }

                    if (TestHelperFixtureDependency.ENTITY
                            .equals(
                                    fixture.kind)) {

                        Integer depth =
                                entityDepthByType.get(
                                        fixture.qualifiedType);

                        scanEntityRelations(
                                type,
                                depth == null
                                        ? 0
                                        : depth.intValue());
                    }
                }

                changed =
                        fixtureByType.size()
                                > before;
            }
        }

        void scanFixtureSourceType(
                final IType type) {

            if (type == null
                    || fixtureSourceScanned.size()
                            >= MAX_FIXTURE_SOURCE_TYPES
                    || !fixtureSourceScanned.add(
                            type.getHandleIdentifier())) {

                if (fixtureSourceScanned.size()
                        >= MAX_FIXTURE_SOURCE_TYPES) {
                    truncated = true;
                }

                return;
            }

            ICompilationUnit unit =
                    type.getCompilationUnit();

            CompilationUnit ast =
                    astFor(
                            unit);

            if (ast == null) {
                return;
            }

            try {
                ISourceRange range =
                        type.getSourceRange();

                ASTNode node =
                        NodeFinder.perform(
                                ast,
                                range.getOffset(),
                                range.getLength());

                while (node != null
                        && !(node instanceof org.eclipse.jdt.core.dom.AbstractTypeDeclaration)) {

                    node = node.getParent();
                }

                if (node == null) {
                    return;
                }

                final String sourceName =
                        type.getElementName();

                node.accept(
                        new ASTVisitor() {
                            @Override
                            public boolean visit(
                                    TypeLiteral literal) {

                                addFixtureBinding(
                                        literal.getType()
                                                .resolveBinding(),
                                        "Von "
                                                + sourceName
                                                + " verwendet",
                                        0);

                                return !isCanceled();
                            }

                            @Override
                            public boolean visit(
                                    ClassInstanceCreation creation) {

                                addFixtureBinding(
                                        creation.resolveTypeBinding(),
                                        "Von "
                                                + sourceName
                                                + " erzeugt",
                                        0);

                                return !isCanceled();
                            }
                        });

            } catch (JavaModelException e) {
                // Best effort fixture discovery only.
            }
        }

        void scanEntityRelations(
                IType entity,
                int depth) {

            if (entity == null
                    || depth
                            >= MAX_ENTITY_RELATION_DEPTH) {

                return;
            }

            try {
                for (IField field :
                        entity.getFields()) {

                    if (Flags.isStatic(
                            field.getFlags())) {

                        continue;
                    }

                    collectFieldSignature(
                            entity,
                            field,
                            field.getTypeSignature(),
                            depth + 1);
                }

            } catch (JavaModelException e) {
                // Entity relation discovery is advisory only.
            }
        }

        void collectFieldSignature(
                IType owner,
                IField field,
                String signature,
                int depth)
                throws JavaModelException {

            if (signature == null
                    || signature.isEmpty()) {

                return;
            }

            String[] arguments =
                    Signature.getTypeArguments(
                            signature);

            for (String argument :
                    arguments) {

                collectFieldSignature(
                        owner,
                        field,
                        argument,
                        depth);
            }

            String erased =
                    Signature.getTypeErasure(
                            signature);

            String readable =
                    Signature.toString(
                            erased);

            while (readable.endsWith("[]")) {
                readable =
                        readable.substring(
                                0,
                                readable.length() - 2);
            }

            String[][] resolved =
                    owner.resolveType(
                            readable);

            if (resolved == null
                    || resolved.length == 0) {

                return;
            }

            String qualified =
                    resolved[0][0] == null
                            || resolved[0][0].isEmpty()
                                    ? resolved[0][1]
                                    : resolved[0][0]
                                            + "."
                                            + resolved[0][1];

            IType related =
                    owner.getJavaProject()
                            .findType(
                                    qualified);

            if (related == null
                    || !TestHelperFixtureDependency.ENTITY
                            .equals(
                                    fixtureKind(
                                            related))) {

                return;
            }

            addFixtureType(
                    related,
                    "Entity-Beziehung "
                            + entityName(
                                    owner)
                            + "."
                            + field.getElementName(),
                    depth);
        }

        IType findType(
                String qualifiedName) {

            try {
                return selected.getJavaProject()
                        .findType(
                                qualifiedName);

            } catch (JavaModelException e) {
                return null;
            }
        }

        String fixtureKind(
                IType type) {

            if (type == null
                    || !(type.getResource()
                            instanceof IFile)) {

                return TestHelperFixtureDependency.TYPE;
            }

            IFile file =
                    (IFile) type.getResource();

            if (FlowJavaSemantics.isEntity(
                    file)) {

                return TestHelperFixtureDependency.ENTITY;
            }

            String simple =
                    type.getElementName();

            if (simple.endsWith("TO")
                    || simple.endsWith("DTO")
                    || simple.endsWith("Dto")
                    || FlowCategoryClassifier.TO
                            .equals(
                                    FlowCategoryClassifier.classify(
                                            file))) {

                return TestHelperFixtureDependency.TO;
            }

            if (simple.contains("QueryBuilder")
                    || simple.endsWith("Builder")) {

                return TestHelperFixtureDependency.QUERY_BUILDER;
            }

            return TestHelperFixtureDependency.TYPE;
        }

        List<TestHelperFixtureDependency> fixtureDependencies() {
            List<TestHelperFixtureDependency> result =
                    new ArrayList<TestHelperFixtureDependency>();

            for (FixtureBuilder builder :
                    fixtureByType.values()) {

                result.add(
                        builder.build());
            }

            return result;
        }

        CompilationUnit astFor(
                ICompilationUnit unit) {

            if (unit == null
                    || !unit.exists()) {

                return null;
            }

            CompilationUnit ast =
                    astByUnit.get(
                            unit.getHandleIdentifier());

            if (ast != null) {
                return ast;
            }

            if (astByUnit.size()
                    >= MAX_COMPILATION_UNITS) {

                truncated = true;
                return null;
            }

            ASTParser parser =
                    ASTParser.newParser(
                            AST.getJLSLatest());

            parser.setSource(
                    unit);
            parser.setResolveBindings(
                    true);
            parser.setBindingsRecovery(
                    true);

            ast =
                    (CompilationUnit)
                            parser.createAST(
                                    monitor);

            astByUnit.put(
                    unit.getHandleIdentifier(),
                    ast);

            return ast;
        }

        private String entityName(
                IType type) {

            return type == null
                    ? "Entity"
                    : type.getElementName();
        }

        List<TestHelperDependency> dependencies() {
            List<TestHelperDependency> result =
                    new ArrayList<TestHelperDependency>();

            for (DependencyBuilder builder :
                    dependencyByField.values()) {

                result.add(
                        builder.build());
            }

            return result;
        }

        MethodAst methodAst(
                IMethod method) {

            ICompilationUnit unit =
                    (ICompilationUnit)
                            method.getAncestor(
                                    IJavaElement.COMPILATION_UNIT);

            if (unit == null
                    || !unit.exists()) {

                return null;
            }

            CompilationUnit ast =
                    astFor(
                            unit);

            if (ast == null) {
                return null;
            }

            try {
                ISourceRange range =
                        method.getSourceRange();

                ASTNode node =
                        NodeFinder.perform(
                                ast,
                                range.getOffset(),
                                range.getLength());

                while (node != null
                        && !(node
                                instanceof MethodDeclaration)) {

                    node =
                            node.getParent();
                }

                return node
                        instanceof MethodDeclaration
                                ? new MethodAst(
                                        ast,
                                        (MethodDeclaration)
                                                node)
                                : null;

            } catch (JavaModelException e) {
                return null;
            }
        }
    }

    private static final class MethodAst {

        final CompilationUnit ast;
        final MethodDeclaration declaration;

        MethodAst(
                CompilationUnit ast,
                MethodDeclaration declaration) {

            this.ast = ast;
            this.declaration =
                    declaration;
        }
    }
}
