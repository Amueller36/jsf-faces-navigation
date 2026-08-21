package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

public final class FeatureTestAuditDialog
        extends Dialog {

    private final FeatureTestAuditReport report;

    private TreeViewer viewer;
    private Button showTestedButton;

    public FeatureTestAuditDialog(
            Shell parentShell,
            FeatureTestAuditReport report) {

        super(parentShell);

        this.report = report;

        setShellStyle(
                getShellStyle()
                | SWT.RESIZE
                | SWT.MAX);
    }

    @Override
    protected void configureShell(
            Shell newShell) {

        super.configureShell(
                newShell);

        newShell.setText(
                "Feature Test Audit — "
                + report.getFeature());
    }

    @Override
    protected Control createDialogArea(
            Composite parent) {

        Composite area =
                (Composite)
                        super.createDialogArea(
                                parent);

        GridLayout layout =
                new GridLayout(
                        1,
                        false);

        layout.marginWidth = 10;
        layout.marginHeight = 10;
        layout.verticalSpacing = 7;

        area.setLayout(
                layout);

        Label summary =
                new Label(
                        area,
                        SWT.WRAP);

        summary.setText(
                summaryText());

        summary.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false));

        Label note =
                new Label(
                        area,
                        SWT.WRAP);

        note.setText(
                "Coverage here means static test-source references to production methods. "
                + "It is excellent for finding obvious missing tests, but it is not JaCoCo runtime line/branch coverage.");

        note.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false));

        showTestedButton =
                new Button(
                        area,
                        SWT.CHECK);

        showTestedButton.setText(
                "Show already referenced/tested methods");

        showTestedButton.setSelection(
                false);

        showTestedButton.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        viewer.refresh();
                        viewer.expandToLevel(
                                2);
                    }
                });

        viewer =
                new TreeViewer(
                        area,
                        SWT.BORDER
                        | SWT.SINGLE
                        | SWT.H_SCROLL
                        | SWT.V_SCROLL);

        viewer.getTree()
                .setFont(
                        JFaceResources
                                .getTextFont());

        viewer.setContentProvider(
                new AuditContentProvider());

        viewer.setLabelProvider(
                new AuditLabelProvider());

        viewer.setInput(
                report);

        viewer.getTree()
                .setLayoutData(
                        new GridData(
                                SWT.FILL,
                                SWT.FILL,
                                true,
                                true));

        Composite actions =
                new Composite(
                        area,
                        SWT.NONE);

        GridLayout actionLayout =
                new GridLayout(
                        5,
                        true);

        actionLayout.marginWidth = 0;
        actionLayout.marginHeight = 0;

        actions.setLayout(
                actionLayout);

        actions.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.BOTTOM,
                        true,
                        false));

        actionButton(
                actions,
                "Open Source",
                new Runnable() {
                    @Override
                    public void run() {

                        openSource();
                    }
                });

        actionButton(
                actions,
                "Open Test",
                new Runnable() {
                    @Override
                    public void run() {

                        openTest();
                    }
                });

        actionButton(
                actions,
                "Generate Helper",
                new Runnable() {
                    @Override
                    public void run() {

                        generateHelper();
                    }
                });

        actionButton(
                actions,
                "Create Test Class…",
                new Runnable() {
                    @Override
                    public void run() {

                        createTestClass();
                    }
                });

        actionButton(
                actions,
                "Add Audit to Flow",
                new Runnable() {
                    @Override
                    public void run() {

                        addToFlow();
                    }
                });

        viewer.expandToLevel(
                2);

        return area;
    }

    private Button actionButton(
            Composite parent,
            String text,
            final Runnable action) {

        Button button =
                new Button(
                        parent,
                        SWT.PUSH);

        button.setText(
                text);

        button.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        true,
                        false));

        button.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        action.run();
                    }
                });

        return button;
    }

    private String summaryText() {
        StringBuilder out =
                new StringBuilder();

        out.append(
                report.getFeature())
                .append(
                        ": ")
                .append(
                        report.getClasses()
                                .size())
                .append(
                        " production classes • ")
                .append(
                        report.getMethodCount())
                .append(
                        " testable methods • ")
                .append(
                        report.getReferenceCoveragePercent())
                .append(
                        "% static method-reference coverage • ")
                .append(
                        report.getUntestedMethodCount())
                .append(
                        " not referenced by tests • ")
                .append(
                        report.getClassesWithoutTests())
                .append(
                        " classes without a matching test class");

        if (report.isTruncated()) {
            out.append(
                    " • scan hit safety limit");
        }

        return out.toString();
    }

    private Object selected() {
        return viewer == null
                ? null
                : viewer
                        .getStructuredSelection()
                        .getFirstElement();
    }

    private FeatureTestClassStatus selectedClass() {
        Object selected =
                selected();

        if (selected
                instanceof FeatureTestClassStatus) {

            return (FeatureTestClassStatus)
                    selected;
        }

        if (selected
                instanceof FeatureTestMethodStatus) {

            FeatureTestMethodStatus method =
                    (FeatureTestMethodStatus)
                            selected;

            IType type =
                    method.getMethod() == null
                            ? null
                            : method.getMethod()
                                    .getDeclaringType();

            return classFor(
                    type);
        }

        if (selected
                instanceof FeatureExistingTestNode) {

            return ((FeatureExistingTestNode)
                    selected)
                    .owner;
        }

        return null;
    }

    private FeatureTestClassStatus classFor(
            IType type) {

        if (type == null) {
            return null;
        }

        for (FeatureTestClassStatus status :
                report.getClasses()) {

            if (type.equals(
                    status.getProductionType())) {

                return status;
            }
        }

        return null;
    }

    private void openSource() {
        Object selected =
                selected();

        if (selected
                instanceof FeatureTestMethodStatus) {

            IMethod method =
                    ((FeatureTestMethodStatus)
                            selected)
                            .getMethod();

            if (method != null) {
                JavaEditorOpener.open(
                        method);
            }

            return;
        }

        FeatureTestClassStatus clazz =
                selectedClass();

        if (clazz != null
                && clazz.getProductionType()
                        != null) {

            JavaEditorOpener.open(
                    clazz.getProductionType());
        }
    }

    private void openTest() {
        Object selected =
                selected();

        TestTargetCandidate candidate =
                null;

        if (selected
                instanceof FeatureExistingTestNode) {

            candidate =
                    ((FeatureExistingTestNode)
                            selected)
                            .candidate;

        } else {
            FeatureTestClassStatus clazz =
                    selectedClass();

            if (clazz != null
                    && !clazz.getTests()
                            .isEmpty()) {

                candidate =
                        clazz.getTests()
                                .get(0);
            }
        }

        if (candidate != null
                && candidate.getType()
                        != null) {

            JavaEditorOpener.open(
                    candidate.getType());
        }
    }

    private void generateHelper() {
        Object selected =
                selected();

        if (!(selected
                instanceof FeatureTestMethodStatus)) {

            MessageDialog.openInformation(
                    getShell(),
                    "Feature Test Audit",
                    "Select an untested production method first.");

            return;
        }

        IMethod method =
                ((FeatureTestMethodStatus)
                        selected)
                        .getMethod();

        TestHelperGeneratorLauncher
                .open(
                        method);
    }

    private void createTestClass() {
        FeatureTestClassStatus clazz =
                selectedClass();

        if (clazz == null
                || clazz.getProductionType()
                        == null) {

            MessageDialog.openInformation(
                    getShell(),
                    "Feature Test Audit",
                    "Select a production class or one of its methods first.");

            return;
        }

        final IType production =
                clazz.getProductionType();

        Job job =
                new Job(
                        "Suggest test location for "
                        + production
                                .getElementName()) {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        TestHelperAnalysis lightweight =
                                new TestHelperAnalysis(
                                        production
                                                .getFullyQualifiedName(),
                                        "",
                                        "void",
                                        new ArrayList<TestHelperParameter>(),
                                        new ArrayList<TestHelperDependency>(),
                                        false,
                                        false,
                                        false,
                                        false);

                        final List<NewTestLocationCandidate> locations =
                                new ArrayList<NewTestLocationCandidate>();

                        locations.addAll(
                                NewTestLocationFinder
                                        .suggest(
                                                lightweight,
                                                TestHelperSnippetGenerator.UNIT_TEST,
                                                monitor));

                        locations.addAll(
                                NewTestLocationFinder
                                        .suggest(
                                                lightweight,
                                                TestHelperSnippetGenerator.JPA_TEST,
                                                monitor));

                        if (monitor.isCanceled()) {
                            return Status.CANCEL_STATUS;
                        }

                        getShell()
                                .getDisplay()
                                .asyncExec(
                                        new Runnable() {
                                            @Override
                                            public void run() {

                                                chooseAndCreate(
                                                        locations);
                                            }
                                        });

                        return Status.OK_STATUS;
                    }
                };

        job.setUser(true);
        job.schedule();
    }

    private void chooseAndCreate(
            List<NewTestLocationCandidate> locations) {

        if (locations == null
                || locations.isEmpty()) {

            MessageDialog.openInformation(
                    getShell(),
                    "Create Test Class",
                    "No Java test source root could be suggested.");

            return;
        }

        ElementListSelectionDialog dialog =
                new ElementListSelectionDialog(
                        getShell(),
                        new LabelProvider() {
                            @Override
                            public String getText(
                                    Object element) {

                                return element
                                        instanceof NewTestLocationCandidate
                                                ? ((NewTestLocationCandidate)
                                                        element)
                                                        .getLabel()
                                                : super.getText(
                                                        element);
                            }
                        });

        dialog.setTitle(
                "Create Test Class");

        dialog.setMessage(
                "Choose the target test project/source root:");

        dialog.setElements(
                locations.toArray());

        if (dialog.open()
                != Dialog.OK) {

            return;
        }

        final NewTestLocationCandidate selected =
                (NewTestLocationCandidate)
                        dialog.getFirstResult();

        Job create =
                new Job(
                        "Create "
                        + selected
                                .getClassName()) {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        try {
                            final IType created =
                                    NewTestClassCreator
                                            .create(
                                                    selected,
                                                    monitor);

                            if (created != null) {
                                getShell()
                                        .getDisplay()
                                        .asyncExec(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {

                                                        JavaEditorOpener
                                                                .open(
                                                                        created);
                                                    }
                                                });
                            }

                            return Status.OK_STATUS;

                        } catch (Exception e) {
                            return new Status(
                                    IStatus.ERROR,
                                    "de.andre.jsfnavigation",
                                    "Could not create test class.",
                                    e);
                        }
                    }
                };

        create.setUser(true);
        create.schedule();
    }

    private void addToFlow() {
        FlowExplorerService service =
                Activator
                        .getFlowExplorerService();

        if (service == null) {
            return;
        }

        int added = 0;

        for (FeatureTestClassStatus clazz :
                report.getClasses()) {

            IFile production =
                    clazz.getProductionType()
                            .getResource()
                            instanceof IFile
                                    ? (IFile)
                                            clazz.getProductionType()
                                                    .getResource()
                                    : null;

            if (production != null) {
                service.addFile(
                        production);
                added++;
            }

            for (TestTargetCandidate test :
                    clazz.getTests()) {

                IFile testFile =
                        test.getType()
                                .getResource()
                                instanceof IFile
                                        ? (IFile)
                                                test.getType()
                                                        .getResource()
                                        : null;

                if (testFile != null) {
                    service.addFile(
                            testFile);
                    added++;
                }
            }
        }

        FlowExplorerView.refreshIfOpen();

        WebSphereStatusLine.show(
                "Feature audit files added/reclassified in the current Flow ("
                        + added
                        + " production/test entries processed).");
    }

    @Override
    protected Point getInitialSize() {
        return new Point(
                1180,
                760);
    }

    private final class AuditContentProvider
            implements ITreeContentProvider {

        @Override
        public Object[] getElements(
                Object inputElement) {

            return report.getClasses()
                    .toArray();
        }

        @Override
        public Object[] getChildren(
                Object parentElement) {

            if (parentElement
                    instanceof FeatureTestClassStatus) {

                FeatureTestClassStatus clazz =
                        (FeatureTestClassStatus)
                                parentElement;

                List<Object> children =
                        new ArrayList<Object>();

                for (TestTargetCandidate test :
                        clazz.getTests()) {

                    children.add(
                            new FeatureExistingTestNode(
                                    clazz,
                                    test));
                }

                for (FeatureTestMethodStatus method :
                        clazz.getMethods()) {

                    if (!method.isTested()
                            || showTestedButton
                                    .getSelection()) {

                        children.add(
                                method);
                    }
                }

                return children.toArray();
            }

            return new Object[0];
        }

        @Override
        public Object getParent(
                Object element) {

            if (element
                    instanceof FeatureExistingTestNode) {

                return ((FeatureExistingTestNode)
                        element)
                        .owner;
            }

            if (element
                    instanceof FeatureTestMethodStatus) {

                return selectedClass();
            }

            return null;
        }

        @Override
        public boolean hasChildren(
                Object element) {

            return element
                    instanceof FeatureTestClassStatus;
        }

        @Override
        public void dispose() {
        }

        @Override
        public void inputChanged(
                Viewer viewer,
                Object oldInput,
                Object newInput) {
        }
    }

    private static final class AuditLabelProvider
            extends LabelProvider {

        @Override
        public String getText(
                Object element) {

            if (element
                    instanceof FeatureTestClassStatus) {

                return ((FeatureTestClassStatus)
                        element)
                        .getLabel();
            }

            if (element
                    instanceof FeatureTestMethodStatus) {

                return ((FeatureTestMethodStatus)
                        element)
                        .getLabel();
            }

            if (element
                    instanceof FeatureExistingTestNode) {

                return ((FeatureExistingTestNode)
                        element)
                        .getLabel();
            }

            return super.getText(
                    element);
        }
    }

    private static final class FeatureExistingTestNode {

        final FeatureTestClassStatus owner;
        final TestTargetCandidate candidate;

        FeatureExistingTestNode(
                FeatureTestClassStatus owner,
                TestTargetCandidate candidate) {

            this.owner = owner;
            this.candidate = candidate;
        }

        String getLabel() {
            return "TEST: "
                    + candidate.getLabel();
        }
    }
}
