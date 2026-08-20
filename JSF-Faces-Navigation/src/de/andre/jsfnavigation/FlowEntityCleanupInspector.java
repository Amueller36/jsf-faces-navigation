package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;

public final class FlowEntityCleanupInspector {

    private static final int MAX_HELPER_DEPTH = 5;
    private static final int MAX_METHODS = 60;
    private static final int MAX_COMPILATION_UNITS = 18;

    private FlowEntityCleanupInspector() {
    }

    public static FlowEntityCleanupInspection inspect(
            IMethod testMethod,
            IProgressMonitor monitor) {

        if (testMethod == null
                || !testMethod.exists()) {

            return empty();
        }

        Context context =
                new Context(
                        testMethod,
                        monitor);

        /*
         * Follow helper implementations reachable from the actual test method.
         * This catches helpers declared in the same test class, inherited
         * helpers, and project helper classes when source is available.
         */
        scanMethod(
                testMethod,
                false,
                false,
                0,
                context);

        inspectLifecycleMethods(
                testMethod.getDeclaringType(),
                context);

        return new FlowEntityCleanupInspection(
                context.untrackedCreate,
                context.possibleCreate,
                context.trackedCreate,
                context.directCleanup,
                context.lifecycleCleanup,
                context.lifecycleOwner,
                context.inspectedMethods,
                context.sameClassHelper,
                context.superclassHelper,
                context.truncated);
    }

    private static void scanMethod(
            IMethod method,
            boolean trackedPath,
            boolean lifecyclePath,
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

        String identity =
                method.getHandleIdentifier()
                + "|"
                + trackedPath
                + "|"
                + lifecyclePath;

        if (!context.visitedMethods
                .add(identity)) {

            return;
        }

        MethodAst target =
                context.methodAst(
                        method);

        if (target == null) {
            return;
        }

        context.inspectedMethods++;

        final boolean currentTracked =
                trackedPath
                || looksLikeTrackedCreate(
                        method.getElementName()
                                .toLowerCase(
                                        Locale.ENGLISH));

        final boolean currentLifecycle =
                lifecyclePath;

        target.declaration.accept(
                new ASTVisitor() {
                    @Override
                    public boolean visit(
                            MethodInvocation node) {

                        inspectInvocation(
                                node.resolveMethodBinding(),
                                node.getName()
                                        .getIdentifier(),
                                currentTracked,
                                currentLifecycle,
                                depth,
                                context);

                        return !context.isCanceled();
                    }

                    @Override
                    public boolean visit(
                            SuperMethodInvocation node) {

                        inspectInvocation(
                                node.resolveMethodBinding(),
                                node.getName()
                                        .getIdentifier(),
                                currentTracked,
                                currentLifecycle,
                                depth,
                                context);

                        return !context.isCanceled();
                    }
                });
    }

    private static void inspectInvocation(
            IMethodBinding binding,
            String methodName,
            boolean trackedPath,
            boolean lifecyclePath,
            int depth,
            Context context) {

        if (context.isCanceled()) {
            return;
        }

        String name =
                methodName == null
                        ? ""
                        : methodName;

        String lower =
                name.toLowerCase(
                        Locale.ENGLISH);

        String declaring =
                "";

        if (binding != null
                && binding.getDeclaringClass()
                        != null) {

            declaring =
                    binding.getDeclaringClass()
                            .getErasure()
                            .getQualifiedName()
                            .toLowerCase(
                                    Locale.ENGLISH);
        }

        if (isEntityManager(
                declaring)) {

            if ("persist".equals(lower)) {
                if (trackedPath) {
                    context.trackedCreate = true;
                } else {
                    context.untrackedCreate = true;
                }

                return;
            }

            if ("merge".equals(lower)) {
                context.possibleCreate = true;
                return;
            }

            if ("remove".equals(lower)) {
                if (lifecyclePath) {
                    markLifecycleCleanup(
                            context,
                            "EntityManager.remove");
                } else {
                    context.directCleanup = true;
                }

                return;
            }
        }

        boolean trackedHelper =
                looksLikeTrackedCreate(
                        lower);

        if (trackedHelper) {
            context.trackedCreate = true;
        }

        if (looksLikeCleanupHelper(
                lower)) {

            if (lifecyclePath) {
                markLifecycleCleanup(
                        context,
                        name + "(...)");
            } else {
                context.directCleanup = true;
            }
        }

        IMethod target =
                javaMethod(
                        binding);

        if (target == null
                || !isWorkspaceSource(
                        target)) {

            return;
        }

        IType targetType =
                target.getDeclaringType();

        IType testType =
                context.testMethod
                        .getDeclaringType();

        if (targetType != null
                && testType != null) {

            if (targetType.equals(
                    testType)) {

                if (!target.equals(
                        context.testMethod)) {

                    context.sameClassHelper =
                            true;
                }

            } else if (isSuperclassOf(
                    targetType,
                    testType,
                    context)) {

                context.superclassHelper =
                        true;
            }
        }

        scanMethod(
                target,
                trackedPath
                        || trackedHelper,
                lifecyclePath,
                depth + 1,
                context);
    }

    private static void inspectLifecycleMethods(
            IType testType,
            Context context) {

        if (testType == null
                || !testType.exists()
                || context.isCanceled()) {

            return;
        }

        List<IType> types =
                new ArrayList<IType>();

        types.add(testType);

        try {
            ITypeHierarchy hierarchy =
                    context.hierarchy(
                            testType);

            if (hierarchy != null) {
                for (IType superType :
                        hierarchy.getAllSuperclasses(
                                testType)) {

                    types.add(
                            superType);
                }
            }

        } catch (Exception e) {
            // Best effort: current class is still inspected.
        }

        for (IType type :
                types) {

            if (context.isCanceled()) {
                return;
            }

            try {
                for (IMethod method :
                        type.getMethods()) {

                    if (!isLifecycleCleanupMethod(
                            method)) {

                        continue;
                    }

                    boolean beforeCleanup =
                            context.lifecycleCleanup;

                    scanMethod(
                            method,
                            false,
                            true,
                            0,
                            context);

                    if (!beforeCleanup
                            && context.lifecycleCleanup) {

                        context.lifecycleOwner =
                                type.getElementName()
                                + "."
                                + method.getElementName()
                                + "(...)";
                    }
                }

            } catch (JavaModelException e) {
                // Continue with other hierarchy types.
            }
        }
    }

    private static boolean isLifecycleCleanupMethod(
            IMethod method) {

        if (method == null
                || !method.exists()) {

            return false;
        }

        String name =
                method.getElementName();

        if ("tearDown".equals(name)
                && method.getNumberOfParameters()
                        == 0) {

            return true;
        }

        try {
            for (IAnnotation annotation :
                    method.getAnnotations()) {

                String simple =
                        simpleName(
                                annotation
                                        .getElementName());

                if ("After".equals(simple)
                        || "AfterEach".equals(simple)
                        || "AfterAll".equals(simple)
                        || "AfterMethod".equals(simple)
                        || "AfterTest".equals(simple)) {

                    return true;
                }
            }

        } catch (JavaModelException e) {
            return false;
        }

        return false;
    }

    private static void markLifecycleCleanup(
            Context context,
            String owner) {

        context.lifecycleCleanup =
                true;

        if (context.lifecycleOwner
                .isEmpty()) {

            context.lifecycleOwner =
                    owner == null
                            ? ""
                            : owner;
        }
    }

    private static IMethod javaMethod(
            IMethodBinding binding) {

        if (binding == null) {
            return null;
        }

        IJavaElement element =
                binding.getMethodDeclaration()
                        .getJavaElement();

        return element
                instanceof IMethod
                        ? (IMethod) element
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

    private static boolean isSuperclassOf(
            IType candidate,
            IType type,
            Context context) {

        if (candidate == null
                || type == null) {

            return false;
        }

        try {
            ITypeHierarchy hierarchy =
                    context.hierarchy(
                            type);

            if (hierarchy == null) {
                return false;
            }

            for (IType superType :
                    hierarchy.getAllSuperclasses(
                            type)) {

                if (candidate.equals(
                        superType)) {

                    return true;
                }
            }

        } catch (Exception e) {
            return false;
        }

        return false;
    }

    private static boolean looksLikeTrackedCreate(
            String name) {

        return name.indexOf(
                "persistentity") >= 0
                || name.indexOf(
                        "persisttestentity") >= 0
                || name.indexOf(
                        "trackentity") >= 0
                || name.indexOf(
                        "registerentity") >= 0
                || name.indexOf(
                        "addentityforcleanup") >= 0
                || name.indexOf(
                        "addtestentity") >= 0;
    }

    private static boolean looksLikeCleanupHelper(
            String name) {

        return name.indexOf(
                "deleteentity") >= 0
                || name.indexOf(
                        "removeentity") >= 0
                || name.indexOf(
                        "cleanupentity") >= 0
                || name.indexOf(
                        "cleanupdatabase") >= 0
                || name.indexOf(
                        "cleardatabase") >= 0
                || name.indexOf(
                        "deletedatabase") >= 0
                || name.indexOf(
                        "deletetestdata") >= 0
                || name.indexOf(
                        "cleanupdata") >= 0
                || "teardown".equals(
                        name);
    }

    private static boolean isEntityManager(
            String declaring) {

        return declaring.endsWith(
                ".entitymanager")
                || "entitymanager".equals(
                        declaring);
    }

    private static String simpleName(
            String name) {

        if (name == null) {
            return "";
        }

        int dot =
                name.lastIndexOf('.');

        return dot >= 0
                ? name.substring(
                        dot + 1)
                : name;
    }

    private static FlowEntityCleanupInspection empty() {
        return new FlowEntityCleanupInspection(
                false,
                false,
                false,
                false,
                false,
                "",
                0,
                false,
                false,
                false);
    }

    private static final class Context {

        final IMethod testMethod;
        final IProgressMonitor monitor;

        final Set<String> visitedMethods =
                new LinkedHashSet<String>();

        final Map<String, CompilationUnit> astByUnit =
                new LinkedHashMap<String, CompilationUnit>();

        ITypeHierarchy hierarchy;

        boolean untrackedCreate;
        boolean possibleCreate;
        boolean trackedCreate;
        boolean directCleanup;
        boolean lifecycleCleanup;
        String lifecycleOwner = "";

        boolean sameClassHelper;
        boolean superclassHelper;
        boolean truncated;

        int inspectedMethods;

        Context(
                IMethod testMethod,
                IProgressMonitor monitor) {

            this.testMethod =
                    testMethod;
            this.monitor =
                    monitor;
        }

        boolean isCanceled() {
            return monitor != null
                    && monitor.isCanceled();
        }

        ITypeHierarchy hierarchy(
                IType type)
                throws JavaModelException {

            if (hierarchy == null) {
                hierarchy =
                        type.newSupertypeHierarchy(
                                monitor);
            }

            return hierarchy;
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
                    astByUnit.get(
                            unit.getHandleIdentifier());

            if (ast == null) {
                if (astByUnit.size()
                        >= MAX_COMPILATION_UNITS) {

                    truncated = true;
                    return null;
                }

                ASTParser parser =
                        ASTParser.newParser(
                                AST.JLS8);

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
