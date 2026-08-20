package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public final class FlowTransactionLensAnalyzer {

    private static final int MAX_EVENTS_PER_METHOD = 120;

    private FlowTransactionLensAnalyzer() {
    }

    public static FlowTransactionLensReport analyze(
            IFile file,
            IProgressMonitor monitor) {

        if (file == null
                || !file.exists()
                || !"java".equalsIgnoreCase(
                        file.getFileExtension())) {

            return new FlowTransactionLensReport(
                    file,
                    Collections
                            .<FlowTransactionMethodReport>
                                    emptyList());
        }

        ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(
                        file);

        if (unit == null
                || !unit.exists()) {

            return new FlowTransactionLensReport(
                    file,
                    Collections
                            .<FlowTransactionMethodReport>
                                    emptyList());
        }

        ASTParser parser =
                ASTParser.newParser(
                        AST.JLS8);

        parser.setSource(unit);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);

        final CompilationUnit ast =
                (CompilationUnit)
                        parser.createAST(
                                monitor);

        final List<FlowTransactionMethodReport> reports =
                new ArrayList<FlowTransactionMethodReport>();

        ast.accept(
                new ASTVisitor() {
                    @Override
                    public boolean visit(
                            MethodDeclaration method) {

                        if (!isTestMethod(
                                method)) {

                            return true;
                        }

                        reports.add(
                                analyzeMethod(
                                        ast,
                                        method,
                                        monitor));

                        /*
                         * analyzeMethod walks this method body itself. Returning
                         * false prevents the outer visitor from doing duplicate
                         * work below this method.
                         */
                        return false;
                    }
                });

        Collections.sort(
                reports,
                new Comparator<FlowTransactionMethodReport>() {
                    @Override
                    public int compare(
                            FlowTransactionMethodReport left,
                            FlowTransactionMethodReport right) {

                        return left.getLine()
                                - right.getLine();
                    }
                });

        return new FlowTransactionLensReport(
                file,
                reports);
    }

    private static FlowTransactionMethodReport analyzeMethod(
            final CompilationUnit ast,
            final MethodDeclaration method,
            IProgressMonitor monitor) {

        final List<FlowTransactionEvent> events =
                new ArrayList<FlowTransactionEvent>();

        final State state =
                new State();

        addTransactionAnnotations(
                ast,
                method,
                events,
                state);

        method.accept(
                new ASTVisitor() {
                    @Override
                    public boolean visit(
                            MethodInvocation node) {

                        if (events.size()
                                >= MAX_EVENTS_PER_METHOD) {

                            return false;
                        }

                        classifyInvocation(
                                ast,
                                node,
                                node.resolveMethodBinding(),
                                node.getName()
                                        .getIdentifier(),
                                events,
                                state);

                        return true;
                    }

                    @Override
                    public boolean visit(
                            SuperMethodInvocation node) {

                        if (events.size()
                                >= MAX_EVENTS_PER_METHOD) {

                            return false;
                        }

                        classifyInvocation(
                                ast,
                                node,
                                node.resolveMethodBinding(),
                                node.getName()
                                        .getIdentifier(),
                                events,
                                state);

                        return true;
                    }
                });


        IMethod javaTestMethod =
                javaMethod(
                        method.resolveBinding());

        if (javaTestMethod != null) {
            FlowEntityCleanupInspection inspection =
                    FlowEntityCleanupInspector
                            .inspect(
                                    javaTestMethod,
                                    monitor);

            boolean hadTrackedCreate =
                    state.trackedCreate;

            state.untrackedCreate =
                    state.untrackedCreate
                    || inspection
                            .hasUntrackedCreate();

            state.possibleCreate =
                    state.possibleCreate
                    || inspection
                            .hasPossibleCreate();

            state.trackedCreate =
                    state.trackedCreate
                    || inspection
                            .hasTrackedCreate();

            state.deleteCleanup =
                    state.deleteCleanup
                    || inspection
                            .hasDirectCleanup();

            state.lifecycleCleanup =
                    inspection
                            .hasLifecycleCleanup();

            state.lifecycleOwner =
                    inspection
                            .getLifecycleOwner();

            state.inspectedHelperMethods =
                    inspection
                            .getInspectedMethods();

            state.sameClassHelperInspected =
                    inspection
                            .hasSameClassHelper();

            state.superclassHelperInspected =
                    inspection
                            .hasSuperclassHelper();

            state.helperInspectionTruncated =
                    inspection
                            .isTruncated();

            if (!hadTrackedCreate
                    && inspection
                            .hasTrackedCreate()) {

                events.add(
                        new FlowTransactionEvent(
                                FlowTransactionEvent.CLEANUP_TRACK,
                                "tracked entity helper found during helper traversal"
                                        + helperScopeSuffix(
                                                inspection)
                                        + "  [tracking-name heuristic]",
                                line(
                                        ast,
                                        method),
                                true));
            }

            if (inspection
                    .hasLifecycleCleanup()) {

                events.add(
                        new FlowTransactionEvent(
                                FlowTransactionEvent.DB_DELETE,
                                "cleanup found in test lifecycle"
                                        + (inspection
                                                .getLifecycleOwner()
                                                .isEmpty()
                                                        ? ""
                                                        : ": "
                                                                + inspection
                                                                        .getLifecycleOwner())
                                        + "  [association with this exact row is not proven]",
                                line(
                                        ast,
                                        method),
                                true));
            }
        }

        Collections.sort(
                events,
                new Comparator<FlowTransactionEvent>() {
                    @Override
                    public int compare(
                            FlowTransactionEvent left,
                            FlowTransactionEvent right) {

                        return left.getLine()
                                - right.getLine();
                    }
                });

        List<String> hints =
                buildHints(
                        state);

        return new FlowTransactionMethodReport(
                methodLabel(
                        method),
                line(
                        ast,
                        method),
                events,
                hints);
    }

    private static void addTransactionAnnotations(
            CompilationUnit ast,
            MethodDeclaration method,
            List<FlowTransactionEvent> events,
            State state) {

        TypeDeclaration type =
                ancestorType(
                        method);

        if (type != null) {
            addTransactionAnnotations(
                    ast,
                    type.modifiers(),
                    type,
                    events,
                    state,
                    "class");
        }

        addTransactionAnnotations(
                ast,
                method.modifiers(),
                method,
                events,
                state,
                "method");
    }

    private static void addTransactionAnnotations(
            CompilationUnit ast,
            List<?> modifiers,
            ASTNode owner,
            List<FlowTransactionEvent> events,
            State state,
            String scope) {

        for (Object modifier :
                modifiers) {

            if (!(modifier
                    instanceof Annotation)) {

                continue;
            }

            Annotation annotation =
                    (Annotation)
                            modifier;

            String simple =
                    simpleName(
                            annotation
                                    .getTypeName()
                                    .getFullyQualifiedName());

            if ("Transactional".equals(
                    simple)
                    || "TransactionAttribute".equals(
                            simple)
                    || "Commit".equals(simple)
                    || "Rollback".equals(simple)) {

                events.add(
                        new FlowTransactionEvent(
                                FlowTransactionEvent.TX,
                                "@"
                                        + simple
                                        + " ("
                                        + scope
                                        + ")",
                                line(
                                        ast,
                                        annotation),
                                false));

                state.transactionAnnotation =
                        true;
            }
        }
    }

    private static void classifyInvocation(
            CompilationUnit ast,
            ASTNode node,
            IMethodBinding binding,
            String methodName,
            List<FlowTransactionEvent> events,
            State state) {

        String declaring =
                "";

        if (binding != null
                && binding.getDeclaringClass()
                        != null) {

            declaring =
                    binding.getDeclaringClass()
                            .getErasure()
                            .getQualifiedName();
        }

        String lowerDeclaring =
                declaring.toLowerCase(
                        Locale.ENGLISH);

        String lowerMethod =
                methodName == null
                        ? ""
                        : methodName.toLowerCase(
                                Locale.ENGLISH);

        int line =
                line(
                        ast,
                        node);

        if (isEntityManager(
                lowerDeclaring)) {

            if ("persist".equals(
                    lowerMethod)) {

                events.add(
                        event(
                                FlowTransactionEvent.DB_CREATE,
                                declaring,
                                methodName,
                                line,
                                false));

                state.write = true;
                state.untrackedCreate = true;
                state.samePcWriteActive = true;
                state.flushedSinceWrite = false;
                return;
            }

            if ("merge".equals(
                    lowerMethod)) {

                events.add(
                        event(
                                FlowTransactionEvent.WRITE,
                                declaring,
                                methodName,
                                line,
                                false));

                state.write = true;
                state.possibleCreate = true;
                state.samePcWriteActive = true;
                state.flushedSinceWrite = false;
                return;
            }

            if ("remove".equals(
                    lowerMethod)) {

                events.add(
                        event(
                                FlowTransactionEvent.DB_DELETE,
                                declaring,
                                methodName,
                                line,
                                false));

                state.write = true;
                state.deleteCleanup = true;
                return;
            }

            if ("flush".equals(
                    lowerMethod)) {

                events.add(
                        event(
                                FlowTransactionEvent.FLUSH,
                                declaring,
                                methodName,
                                line,
                                false));

                state.flush = true;

                if (state.samePcWriteActive) {
                    state.flushedSinceWrite = true;
                }

                return;
            }

            if ("clear".equals(
                    lowerMethod)
                    || "close".equals(
                            lowerMethod)) {

                events.add(
                        event(
                                FlowTransactionEvent.PC_RESET,
                                declaring,
                                methodName,
                                line,
                                false));

                if (state.samePcWriteActive) {
                    state.clearAfterWrite = true;

                    if (state.flushedSinceWrite) {
                        state.flushBeforeClear = true;
                    }
                }

                state.clear = true;
                state.samePcWriteActive = false;
                state.flushedSinceWrite = false;
                return;
            }

            if ("detach".equals(
                    lowerMethod)) {

                events.add(
                        event(
                                FlowTransactionEvent.PC,
                                declaring,
                                methodName,
                                line,
                                false));

                return;
            }

            if ("find".equals(
                    lowerMethod)
                    || "getreference".equals(
                            lowerMethod)
                    || "refresh".equals(
                            lowerMethod)) {

                events.add(
                        event(
                                FlowTransactionEvent.READ,
                                declaring,
                                methodName,
                                line,
                                false));

                state.read = true;

                if (state.samePcWriteActive) {
                    state.writeThenReadSamePc = true;
                }

                return;
            }

            if ("createquery".equals(
                    lowerMethod)
                    || "createnamedquery".equals(
                            lowerMethod)
                    || "createnativequery".equals(
                            lowerMethod)) {

                events.add(
                        event(
                                FlowTransactionEvent.QUERY,
                                declaring,
                                methodName,
                                line,
                                false));

                state.read = true;

                if (state.samePcWriteActive) {
                    state.writeThenReadSamePc = true;
                }

                return;
            }

            if ("jointransaction".equals(
                    lowerMethod)) {

                events.add(
                        event(
                                FlowTransactionEvent.TX,
                                declaring,
                                methodName,
                                line,
                                false));

                state.explicitTransaction = true;
                return;
            }
        }

        if (isTransactionType(
                lowerDeclaring)) {

            if ("begin".equals(
                    lowerMethod)) {

                events.add(
                        event(
                                FlowTransactionEvent.TX,
                                declaring,
                                methodName,
                                line,
                                false));

                state.explicitTransaction = true;
                state.transactionBegin = true;
                return;
            }

            if ("commit".equals(
                    lowerMethod)
                    || "rollback".equals(
                            lowerMethod)) {

                events.add(
                        event(
                                FlowTransactionEvent.TX,
                                declaring,
                                methodName,
                                line,
                                false));

                state.transactionEnd = true;
                return;
            }

            if ("setrollbackonly".equals(
                    lowerMethod)) {

                events.add(
                        event(
                                FlowTransactionEvent.TX,
                                declaring,
                                methodName,
                                line,
                                false));

                return;
            }
        }


        if (looksLikeTrackedCreate(
                lowerMethod)) {

            events.add(
                    new FlowTransactionEvent(
                            FlowTransactionEvent.CLEANUP_TRACK,
                            methodName
                                    + "(...)"
                                    + "  [helper-name heuristic]",
                            line,
                            true));

            state.write = true;
            state.trackedCreate = true;
            state.heuristic = true;
        }

        if (looksLikeCleanupHelper(
                lowerMethod)) {

            events.add(
                    new FlowTransactionEvent(
                            FlowTransactionEvent.DB_DELETE,
                            methodName
                                    + "(...)"
                                    + "  [cleanup-helper heuristic]",
                            line,
                            true));

            state.deleteCleanup = true;
            state.heuristic = true;
        }

        if (!looksLikeTrackedCreate(
                    lowerMethod)
                && looksLikeCreateHelper(
                        lowerMethod)
                && bindingTouchesEntity(
                        binding)) {

            events.add(
                    new FlowTransactionEvent(
                            FlowTransactionEvent.DB_CREATE,
                            methodName
                                    + "(...)"
                                    + "  [entity-helper heuristic]",
                            line,
                            true));

            state.write = true;
            state.untrackedCreate = true;
            state.heuristic = true;
        }

        /*
         * Project test frameworks often hide PC/transaction operations behind
         * helper methods. These name-based events are deliberately marked with
         * '?' in the UI so they are useful clues, never asserted facts.
         */
        if (looksLikePersistenceContextReset(
                lowerMethod)) {

            events.add(
                    new FlowTransactionEvent(
                            FlowTransactionEvent.PC_RESET,
                            methodName
                                    + "(...)"
                                    + "  [helper-name heuristic]",
                            line,
                            true));

            if (state.samePcWriteActive) {
                state.clearAfterWrite = true;

                if (state.flushedSinceWrite) {
                    state.flushBeforeClear = true;
                }
            }

            state.clear = true;
            state.samePcWriteActive = false;
            state.flushedSinceWrite = false;
            state.heuristic = true;
            return;
        }

        if (looksLikeFlushAndClear(
                lowerMethod)) {

            events.add(
                    new FlowTransactionEvent(
                            FlowTransactionEvent.FLUSH,
                            methodName
                                    + "(...)"
                                    + "  [helper-name heuristic]",
                            line,
                            true));

            events.add(
                    new FlowTransactionEvent(
                            FlowTransactionEvent.PC_RESET,
                            methodName
                                    + "(...)"
                                    + "  [helper-name heuristic]",
                            line,
                            true));

            state.flush = true;

            if (state.samePcWriteActive) {
                state.flushedSinceWrite = true;
                state.clearAfterWrite = true;
                state.flushBeforeClear = true;
            }

            state.clear = true;
            state.samePcWriteActive = false;
            state.flushedSinceWrite = false;
            state.heuristic = true;
            return;
        }

        if (looksLikeTransactionBegin(
                lowerMethod)) {

            events.add(
                    new FlowTransactionEvent(
                            FlowTransactionEvent.TX,
                            methodName
                                    + "(...)"
                                    + "  [helper-name heuristic]",
                            line,
                            true));

            state.transactionBegin = true;
            state.explicitTransaction = true;
            state.heuristic = true;
            return;
        }

        if (looksLikeTransactionEnd(
                lowerMethod)) {

            events.add(
                    new FlowTransactionEvent(
                            FlowTransactionEvent.TX,
                            methodName
                                    + "(...)"
                                    + "  [helper-name heuristic]",
                            line,
                            true));

            state.transactionEnd = true;
            state.heuristic = true;
        }
    }

    private static List<String> buildHints(
            State state) {

        List<String> hints =
                new ArrayList<String>();

        boolean hasJpa =
                state.write
                || state.read
                || state.flush
                || state.clear;


        if (state.untrackedCreate
                && !state.deleteCleanup
                && !state.trackedCreate) {

            hints.add(
                    "POSSIBLE DB LEAK: this test appears to create/persist an entity, but no cleanup registration or direct delete/remove could be associated with that creation. "
                    + "A persistence-context clear only detaches managed objects; it does not delete database rows."
                    + (state.lifecycleCleanup
                            ? " An @After/tearDown-style cleanup path was found in the class hierarchy, but this entity is not visibly registered with it, so the row may still leak."
                            : " No cleanup lifecycle path was found in the inspected test class/superclasses."));
        }

        if (state.possibleCreate
                && !state.deleteCleanup
                && !state.trackedCreate) {

            hints.add(
                    "POSSIBLE DB LEAK: EntityManager.merge(...) is visible and may insert as well as update, but no visible cleanup registration/delete is present. "
                    + "Check whether this entity can be newly inserted in this test path.");
        }

        if ((state.untrackedCreate
                    || state.possibleCreate)
                && state.clear
                && !state.deleteCleanup
                && !state.trackedCreate) {

            hints.add(
                    "Persistence-context reset detected, but that is not database cleanup: clear()/renew-PC detaches entities and leaves persisted rows in the database.");
        }

        if (state.trackedCreate) {

            hints.add(
                    state.lifecycleCleanup
                            ? "Cleanup tracking plus an @After/tearDown-style cleanup path was found while traversing the test class hierarchy/helper implementations. That substantially lowers leak risk, although static analysis cannot prove the runtime cleanup succeeded."
                            : "A cleanup-tracking helper (for example persistEntity/registerEntity) was found, including reachable helper implementations, but no @After/tearDown cleanup path was found in the inspected class hierarchy. Verify where the registered entities are actually removed.");
        }

        if (state.writeThenReadSamePc) {

            hints.add(
                    "Same persistence context spans a write and later read/query. "
                    + "If this test is meant to prove a DB round-trip, a managed entity / first-level cache can hide mapping or query problems; consider an explicit flush + clear/renew-PC boundary.");
        }

        if (state.clearAfterWrite
                && !state.flushBeforeClear) {

            hints.add(
                    "A persistence-context reset is visible after a write, but no explicit flush is visible first. "
                    + "Your framework/transaction may flush automatically, so verify the intended boundary rather than assuming the write already reached the database.");
        }

        if (state.transactionBegin
                && !state.transactionEnd) {

            hints.add(
                    "A manual transaction begin is visible, but no commit/rollback is visible in this test method.");
        }

        if (hasJpa
                && !state.transactionAnnotation
                && !state.explicitTransaction) {

            hints.add(
                    "No explicit transaction boundary is visible in this test method. "
                    + "It may be inherited or managed by the test framework/superclass.");
        }

        if (!hasJpa
                && !state.transactionAnnotation
                && !state.explicitTransaction) {

            hints.add(
                    "No direct JPA/transaction boundary calls were detected in this test method.");
        }


        if (state.inspectedHelperMethods > 0) {
            hints.add(
                    "Cleanup scan inspected "
                    + state.inspectedHelperMethods
                    + " reachable test/helper/lifecycle method"
                    + (state.inspectedHelperMethods == 1
                            ? ""
                            : "s")
                    + (state.sameClassHelperInspected
                            ? ", including same-class helpers"
                            : "")
                    + (state.superclassHelperInspected
                            ? ", including inherited superclass helpers"
                            : "")
                    + (state.helperInspectionTruncated
                            ? ". The bounded scan hit its safety limit, so deeper helpers were not inspected."
                            : "."));
        }

        if (state.heuristic) {
            hints.add(
                    "Entries marked '?' come from helper-method names and are clues only; inspect the helper implementation when the exact boundary matters.");
        }

        return hints;
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
                        ? (IMethod)
                                element
                        : null;
    }

    private static String helperScopeSuffix(
            FlowEntityCleanupInspection inspection) {

        StringBuilder result =
                new StringBuilder();

        if (inspection.hasSameClassHelper()) {
            result.append(
                    " [same-class helper]");
        }

        if (inspection.hasSuperclassHelper()) {
            result.append(
                    " [inherited helper]");
        }

        return result.toString();
    }

    private static FlowTransactionEvent event(
            String kind,
            String declaring,
            String method,
            int line,
            boolean heuristic) {

        return new FlowTransactionEvent(
                kind,
                simpleName(
                        declaring)
                        + "."
                        + method
                        + "(...)",
                line,
                heuristic);
    }

    private static boolean isEntityManager(
            String lowerDeclaring) {

        return lowerDeclaring.endsWith(
                ".entitymanager")
                || "entitymanager".equals(
                        lowerDeclaring);
    }

    private static boolean isTransactionType(
            String lowerDeclaring) {

        return lowerDeclaring.endsWith(
                ".entitytransaction")
                || lowerDeclaring.endsWith(
                        ".usertransaction")
                || "entitytransaction".equals(
                        lowerDeclaring)
                || "usertransaction".equals(
                        lowerDeclaring);
    }


    private static boolean looksLikeTrackedCreate(
            String name) {

        return name.indexOf(
                "persistentity") >= 0
                || name.indexOf(
                        "trackentity") >= 0
                || name.indexOf(
                        "registerentity") >= 0
                || name.indexOf(
                        "addentityforcleanup") >= 0
                || name.indexOf(
                        "addtestentity") >= 0
                || name.indexOf(
                        "persisttestentity") >= 0;
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

    private static boolean looksLikeCreateHelper(
            String name) {

        return name.startsWith(
                "persist")
                || name.startsWith(
                        "insert")
                || name.startsWith(
                        "create")
                || name.startsWith(
                        "save")
                || name.startsWith(
                        "erstelle")
                || name.startsWith(
                        "anlegen");
    }

    private static boolean bindingTouchesEntity(
            IMethodBinding binding) {

        if (binding == null) {
            return false;
        }

        if (isEntityType(
                binding.getReturnType())) {

            return true;
        }

        for (ITypeBinding parameter :
                binding.getParameterTypes()) {

            if (isEntityType(
                    parameter)) {

                return true;
            }
        }

        return false;
    }

    private static boolean isEntityType(
            ITypeBinding binding) {

        if (binding == null) {
            return false;
        }

        if (binding.isArray()) {
            return isEntityType(
                    binding.getElementType());
        }

        for (ITypeBinding argument :
                binding.getTypeArguments()) {

            if (isEntityType(
                    argument)) {

                return true;
            }
        }

        ITypeBinding declaration =
                binding.getTypeDeclaration();

        IJavaElement element =
                declaration == null
                        ? null
                        : declaration
                                .getJavaElement();

        if (!(element
                instanceof IType)) {

            return false;
        }

        IResource resource =
                element.getResource();

        return resource
                instanceof IFile
                && FlowJavaSemantics
                        .isEntity(
                                (IFile)
                                        resource);
    }

    private static boolean looksLikePersistenceContextReset(
            String name) {

        return name.indexOf(
                "renewpersistencecontext") >= 0
                || name.indexOf(
                        "resetpersistencecontext") >= 0
                || name.indexOf(
                        "newpersistencecontext") >= 0
                || name.indexOf(
                        "recreatepersistencecontext") >= 0
                || name.indexOf(
                        "renewentitymanager") >= 0
                || name.indexOf(
                        "recreateentitymanager") >= 0;
    }

    private static boolean looksLikeFlushAndClear(
            String name) {

        return name.indexOf(
                "flushandclear") >= 0
                || name.indexOf(
                        "flushclear") >= 0;
    }

    private static boolean looksLikeTransactionBegin(
            String name) {

        return name.indexOf(
                "begintransaction") >= 0
                || name.indexOf(
                        "starttransaction") >= 0;
    }

    private static boolean looksLikeTransactionEnd(
            String name) {

        return name.indexOf(
                "committransaction") >= 0
                || name.indexOf(
                        "rollbacktransaction") >= 0
                || name.indexOf(
                        "endtransaction") >= 0;
    }

    private static boolean isTestMethod(
            MethodDeclaration method) {

        if (method == null
                || method.isConstructor()) {

            return false;
        }

        for (Object modifier :
                method.modifiers()) {

            if (!(modifier
                    instanceof Annotation)) {

                continue;
            }

            String annotation =
                    simpleName(
                            ((Annotation) modifier)
                                    .getTypeName()
                                    .getFullyQualifiedName());

            if ("Test".equals(
                    annotation)
                    || "ParameterizedTest".equals(
                            annotation)
                    || "RepeatedTest".equals(
                            annotation)
                    || "TestFactory".equals(
                            annotation)) {

                return true;
            }
        }

        return method.getName()
                .getIdentifier()
                .startsWith("test")
                && method.parameters()
                        .isEmpty();
    }

    private static TypeDeclaration ancestorType(
            ASTNode node) {

        ASTNode current =
                node == null
                        ? null
                        : node.getParent();

        while (current != null) {
            if (current
                    instanceof TypeDeclaration) {

                return (TypeDeclaration)
                        current;
            }

            current =
                    current.getParent();
        }

        return null;
    }

    private static String methodLabel(
            MethodDeclaration method) {

        return method.getName()
                .getIdentifier()
                + "(...)";
    }

    private static int line(
            CompilationUnit ast,
            ASTNode node) {

        int line =
                ast.getLineNumber(
                        node.getStartPosition());

        return line <= 0
                ? 1
                : line;
    }

    private static String simpleName(
            String value) {

        if (value == null
                || value.isEmpty()) {

            return "";
        }

        int dot =
                value.lastIndexOf('.');

        return dot >= 0
                ? value.substring(
                        dot + 1)
                : value;
    }

    private static final class State {

        boolean write;
        boolean read;
        boolean flush;
        boolean clear;
        boolean transactionAnnotation;
        boolean explicitTransaction;
        boolean transactionBegin;
        boolean transactionEnd;
        boolean heuristic;

        boolean samePcWriteActive;
        boolean flushedSinceWrite;
        boolean writeThenReadSamePc;
        boolean clearAfterWrite;
        boolean flushBeforeClear;

        boolean untrackedCreate;
        boolean possibleCreate;
        boolean trackedCreate;
        boolean deleteCleanup;

        boolean lifecycleCleanup;
        String lifecycleOwner = "";
        int inspectedHelperMethods;
        boolean sameClassHelperInspected;
        boolean superclassHelperInspected;
        boolean helperInspectionTruncated;
    }
}
