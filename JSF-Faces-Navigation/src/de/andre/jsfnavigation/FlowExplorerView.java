package de.andre.jsfnavigation;

import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;

public final class FlowExplorerView
        extends ViewPart {

    public static final String VIEW_ID =
            "de.andre.jsfnavigation.views.flowExplorer";

    private static volatile FlowExplorerView instance;

    private static final String[] CATEGORY_ORDER =
            new String[] {
                    FlowCategoryClassifier.VIEW,
                    FlowCategoryClassifier.CONTROLLER,
                    FlowCategoryClassifier.BEAN,
                    FlowCategoryClassifier.TO,
                    FlowCategoryClassifier.ISP,
                    FlowCategoryClassifier.DSP,
                    FlowCategoryClassifier.SERVICE,
                    FlowCategoryClassifier.PERSISTENCE,
                    FlowCategoryClassifier.JAXB,
                    FlowCategoryClassifier.SCHEMA,
                    FlowCategoryClassifier.RESOURCE,
                    FlowCategoryClassifier.TEST,
                    FlowCategoryClassifier.OTHER
            };

    private Combo flowCombo;
    private Button autoCaptureButton;
    private Button autoTestsButton;
    private TreeViewer viewer;
    private Label summaryLabel;

    private Font treeZoomFont;
    private FontData[] treeBaseFontData;
    private int treeFontDelta;
    private boolean suppressOpenOnSelection;
    private IResourceChangeListener problemMarkerListener;

    private volatile int focusRequestGeneration;
    private Job focusJob;
    private FlowFocusResult architectureFocus;
    private boolean focusLoading;
    private Color focusDimColor;

    private String flowFilterText = "";
    private FlowFilterMatcher flowFilter =
            FlowFilterMatcher.compile("");

    @Override
    public void createPartControl(
            Composite parent) {

        instance = this;

        parent.setLayout(
                new GridLayout(1, false));

        createHeader(parent);
        createTree(parent);
        installProblemMarkerListener();
        refresh();
    }

private void createHeader(
        Composite parent) {

    /*
     * Do not put every control into one giant GridLayout row. Eclipse
     * views are often docked very narrowly; a single row caused the
     * right-most actions (especially Remove/Auto) to disappear.
     *
     * The header is deliberately split into compact rows so the view
     * remains usable when docked beside the editor.
     */
    Composite header =
            new Composite(
                    parent,
                    SWT.NONE);

    header.setLayoutData(
            new GridData(
                    SWT.FILL,
                    SWT.TOP,
                    true,
                    false));

    GridLayout headerLayout =
            new GridLayout(1, false);

    headerLayout.marginWidth = 0;
    headerLayout.marginHeight = 0;
    headerLayout.verticalSpacing = 3;

    header.setLayout(
            headerLayout);

    createFlowRow(header);
    createPrimaryActionsRow(header);
    createSecondaryActionsRow(header);
    createFilterActionsRow(header);
    createTestActionsRow(header);
    createTestInsightActionsRow(header);

    summaryLabel =
            new Label(
                    header,
                    SWT.NONE);

    summaryLabel.setLayoutData(
            new GridData(
                    SWT.FILL,
                    SWT.TOP,
                    true,
                    false));
}

private void createFlowRow(
        Composite parent) {

    Composite row =
            new Composite(
                    parent,
                    SWT.NONE);

    row.setLayoutData(
            new GridData(
                    SWT.FILL,
                    SWT.TOP,
                    true,
                    false));

    GridLayout layout =
            new GridLayout(3, false);

    layout.marginWidth = 0;
    layout.marginHeight = 0;

    row.setLayout(layout);

    new Label(
            row,
            SWT.NONE)
            .setText("Flow:");

    flowCombo =
            new Combo(
                    row,
                    SWT.DROP_DOWN
                    | SWT.READ_ONLY);

    flowCombo.setLayoutData(
            new GridData(
                    SWT.FILL,
                    SWT.CENTER,
                    true,
                    false));

    flowCombo.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    FlowExplorerService service =
                            service();

                    if (service != null
                            && flowCombo.getSelectionIndex()
                                    >= 0) {

                        clearArchitectureFocus(
                                false);

                        service.setCurrentFlow(
                                flowCombo.getText());

                        refresh();
                    }
                }
            });

    autoCaptureButton =
            new Button(
                    row,
                    SWT.CHECK);

    autoCaptureButton.setText("Auto");
    autoCaptureButton.setToolTipText(
            "Automatically add workspace files when they are opened/activated.");

    autoCaptureButton.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    FlowExplorerService service =
                            service();

                    if (service != null) {
                        service.setAutoCapture(
                                autoCaptureButton
                                        .getSelection());
                    }
                }
            });
}

private void createPrimaryActionsRow(
        Composite parent) {

    Composite row =
            actionRow(
                    parent,
                    4);

    Button add =
            actionButton(
                    row,
                    "+ File",
                    "Add the current editor file to this flow.");

    add.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    addCurrentFile();
                }
            });

    Button newFlow =
            actionButton(
                    row,
                    "+ Flow",
                    "Create a new named development flow.");

    newFlow.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    createFlow();
                }
            });

    Button rename =
            actionButton(
                    row,
                    "Rename",
                    "Rename the current flow.");

    rename.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    renameFlow();
                }
            });

    Button delete =
            actionButton(
                    row,
                    "Delete Flow",
                    "Delete the current flow definition.");

    delete.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    deleteFlow();
                }
            });
}

private void createSecondaryActionsRow(
        Composite parent) {

    Composite row =
            actionRow(
                    parent,
                    4);

    Button openAll =
            actionButton(
                    row,
                    "Open All",
                    "Open every existing file in the current flow.");

    openAll.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    openAllFiles();
                }
            });

    Button focusTabs =
            actionButton(
                    row,
                    "Focus Tabs",
                    "Close workspace editors that are not in the current flow. Unsaved editors keep Eclipse's normal save prompt.");

    focusTabs.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    focusEditorTabs();
                }
            });

    Button remove =
            actionButton(
                    row,
                    "Remove File",
                    "Remove the selected file from this flow. The project file is not deleted.");

    remove.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    removeSelected();
                }
            });

    Button clearFocus =
            actionButton(
                    row,
                    "Clear Focus",
                    "Show every Flow entry normally again and remove the current architecture focus.");

    clearFocus.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    clearArchitectureFocus(
                            true);
                }
            });
}



private void createFilterActionsRow(
        Composite parent) {

    Composite row =
            actionRow(
                    parent,
                    2);

    Button filter =
            actionButton(
                    row,
                    "Filter…",
                    "Filter Flow entries by case-insensitive text, re:regex, or /regex/. Ctrl+F works while the Flow tree is focused.");

    filter.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    openFlowFilterDialog();
                }
            });

    Button clear =
            actionButton(
                    row,
                    "Clear Filter",
                    "Remove the current Flow Explorer text/regex filter.");

    clear.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    clearFlowFilter();
                }
            });
}

private void createTestActionsRow(
        Composite parent) {

    Composite row =
            actionRow(
                    parent,
                    2);

    autoTestsButton =
            new Button(
                    row,
                    SWT.CHECK);

    autoTestsButton.setText(
            "Auto tests");

    autoTestsButton.setToolTipText(
            "Automatically add matching existing test classes when production files are opened, and impacted JUnit tests from caller hierarchy when edited methods are saved.");

    autoTestsButton.setLayoutData(
            new GridData(
                    SWT.FILL,
                    SWT.CENTER,
                    true,
                    false));

    autoTestsButton.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    FlowExplorerService service =
                            service();

                    if (service != null) {
                        service.setAutoTestDiscovery(
                                autoTestsButton
                                        .getSelection());
                    }
                }
            });

    Button runTests =
            actionButton(
                    row,
                    "Run Unit Tests",
                    "Run safe JUnit unit tests currently in this flow. Arquillian integration tests and JPA tests are deliberately skipped.");

    runTests.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    FlowJUnitRunner
                            .runCurrentFlowUnitTests();
                }
            });

}

private void createTestInsightActionsRow(
        Composite parent) {

    Composite row =
            actionRow(
                    parent,
                    3);

    Button txLens =
            actionButton(
                    row,
                    "Tx Lens",
                    "Analyze the selected/active JUnit test for visible transaction and persistence-context boundaries. Runs only on demand.");

    txLens.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    openTransactionLens();
                }
            });


    Button featureTests =
            actionButton(
                    row,
                    "Feature Tests…",
                    "Audit matching Controller/Bean/ISP/DSP classes, existing test classes and production methods not referenced by tests.");

    featureTests.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    FeatureTestAuditLauncher
                            .open(
                                    getSite()
                                            .getShell());
                }
            });

    Button clearResults =
            actionButton(
                    row,
                    "Clear Results",
                    "Clear the persisted last Flow test summary and stack traces for this flow.");

    clearResults.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(
                        SelectionEvent e) {

                    clearTestResults();
                }
            });
}

private Composite actionRow(
        Composite parent,
        int columns) {

    Composite row =
            new Composite(
                    parent,
                    SWT.NONE);

    row.setLayoutData(
            new GridData(
                    SWT.FILL,
                    SWT.TOP,
                    true,
                    false));

    GridLayout layout =
            new GridLayout(
                    columns,
                    true);

    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.horizontalSpacing = 4;

    row.setLayout(layout);

    return row;
}

private Button actionButton(
        Composite parent,
        String text,
        String toolTip) {

    Button button =
            new Button(
                    parent,
                    SWT.PUSH);

    button.setText(text);
    button.setToolTipText(toolTip);

    button.setLayoutData(
            new GridData(
                    SWT.FILL,
                    SWT.CENTER,
                    true,
                    false));

    return button;
}

    private void createTree(
            Composite parent) {

        viewer =
                new TreeViewer(
                        parent,
                        SWT.SINGLE
                        | SWT.H_SCROLL
                        | SWT.V_SCROLL
                        | SWT.BORDER);

        viewer.getControl()
                .setLayoutData(
                        new GridData(
                                SWT.FILL,
                                SWT.FILL,
                                true,
                                true));

        treeBaseFontData =
                copyFontData(
                        viewer.getTree()
                                .getFont()
                                .getFontData());

        viewer.setContentProvider(
                new FlowContentProvider());

        focusDimColor =
                createFocusDimColor();

        viewer.setLabelProvider(
                new FlowLabelProvider(
                        this));

        viewer.getTree()
                .addKeyListener(
                        new KeyAdapter() {
                            @Override
                            public void keyPressed(
                                    KeyEvent e) {

                                if (e.keyCode == SWT.DEL) {
                                    removeSelected();
                                    e.doit = false;
                                    return;
                                }

                                if ((e.stateMask
                                        & SWT.CTRL) != 0
                                        && (e.keyCode == 'f'
                                                || e.keyCode == 'F')) {

                                    openFlowFilterDialog();
                                    e.doit = false;
                                }
                            }
                        });


        viewer.addSelectionChangedListener(
                new ISelectionChangedListener() {
                    @Override
                    public void selectionChanged(
                            SelectionChangedEvent event) {

                        if (suppressOpenOnSelection) {
                            return;
                        }

                        ISelection selection =
                                event.getSelection();

                        if (!(selection
                                instanceof IStructuredSelection)) {

                            return;
                        }

                        Object first =
                                ((IStructuredSelection)
                                        selection)
                                        .getFirstElement();

                        if (first instanceof FlowEntry) {
                            FlowEntry entry =
                                    (FlowEntry) first;

                            requestArchitectureFocus(
                                    entry);

                            openEntry(
                                    entry);

                        } else if (first
                                instanceof FlowImpactTestNode) {

                            openEntry(
                                    ((FlowImpactTestNode) first)
                                            .getEntry());

                        } else if (first
                                instanceof FlowImpactSourceNode) {

                            openImpactSource(
                                    (FlowImpactSourceNode) first);

                        } else if (first
                                instanceof FlowImpactMethodNode) {

                            openImpactMethod(
                                    (FlowImpactMethodNode) first);

                        } else if (first
                                instanceof FlowTestResultClassNode) {

                            openTestResultClass(
                                    (FlowTestResultClassNode) first);

                        } else if (first
                                instanceof FlowTestResultCaseNode) {

                            openTestResultCase(
                                    (FlowTestResultCaseNode) first);

                        } else if (first
                                instanceof FlowStackTraceNode) {

                            openStackTrace(
                                    (FlowStackTraceNode) first);
                        }
                    }
                });

        viewer.getTree()
                .addListener(
                        SWT.MouseWheel,
                        new org.eclipse.swt.widgets.Listener() {
                            @Override
                            public void handleEvent(
                                    org.eclipse.swt.widgets.Event event) {

                                if ((event.stateMask
                                        & SWT.CTRL) == 0) {

                                    return;
                                }

                                event.doit = false;

                                if (event.count > 0) {
                                    zoomTreeFont(1);
                                } else if (event.count < 0) {
                                    zoomTreeFont(-1);
                                }
                            }
                        });
    }

    public void refresh() {
        if (viewer == null
                || viewer.getControl()
                        .isDisposed()) {

            return;
        }

        FlowExplorerService service =
                service();

        if (service == null) {
            return;
        }

        /*
         * Semantic Java classification is modification-stamp cached, so this
         * is cheap on normal refreshes and immediately moves newly annotated
         * @Entity classes into Persistence without requiring an Eclipse
         * restart.
         */
        service.reclassifyCurrentEntries();

        List<String> names =
                service.getFlowNames();

        String current =
                service.getCurrentFlowName();

        flowCombo.removeAll();

        for (String name : names) {
            flowCombo.add(name);
        }

        int index =
                names.indexOf(current);

        if (index >= 0) {
            flowCombo.select(index);
        }

        autoCaptureButton.setSelection(
                service.isAutoCapture());

        if (autoTestsButton != null
                && !autoTestsButton.isDisposed()) {

            autoTestsButton.setSelection(
                    service.isAutoTestDiscovery());
        }

        FlowDefinition flow =
                service.getCurrentFlow();

        int count =
                flow == null
                        ? 0
                        : flow.getEntries().size();

        if (architectureFocus != null
                && (flow == null
                        || !flow.contains(
                                architectureFocus
                                        .getRootResourcePath()))) {

            clearArchitectureFocus(
                    false);
        }

        updateSummaryLabel(
                service,
                count);

        viewer.setInput(
                buildCategories(service));

        viewer.expandAll();
        highlightActiveEditorFile();
    }

    private List<FlowCategoryNode> buildCategories(
            FlowExplorerService service) {

        List<FlowCategoryNode> result =
                new ArrayList<FlowCategoryNode>();

        FlowTestResultStore resultStore =
                Activator.getFlowTestResultStore();

        FlowTestRunSummary lastRun =
                resultStore == null
                        ? null
                        : resultStore.get(
                                service.getCurrentFlowName());

        for (String category :
                CATEGORY_ORDER) {

            List<FlowEntry> entries =
                    filteredEntries(
                            service,
                            service.entriesForCategory(
                                    category));

            if (FlowCategoryClassifier.TEST.equals(
                    category)) {

                List<Object> children =
                        FlowImpactTreeBuilder
                                .build(entries);

                if (lastRun != null
                        && (!flowFilter.isActive()
                                || !entries.isEmpty())) {

                    children.add(
                            0,
                            new FlowTestRunNode(
                                    lastRun));
                }

                if (!entries.isEmpty()
                        || (lastRun != null
                                && !flowFilter.isActive())) {

                    result.add(
                            new FlowCategoryNode(
                                    category,
                                    entries,
                                    children));
                }

            } else if (!entries.isEmpty()) {
                result.add(
                        new FlowCategoryNode(
                                category,
                                entries));
            }
        }

        return result;
    }





    private void openFlowFilterDialog() {
        InputDialog dialog =
                new InputDialog(
                        getSite().getShell(),
                        "Filter JSF Flow Explorer",
                        "Text filter (case-insensitive), re:<regex>, or /<regex>/:",
                        flowFilterText,
                        null);

        if (dialog.open()
                != Window.OK) {

            return;
        }

        String requested =
                dialog.getValue() == null
                        ? ""
                        : dialog.getValue()
                                .trim();

        FlowFilterMatcher candidate =
                FlowFilterMatcher.compile(
                        requested);

        if (!candidate.isValid()) {
            MessageDialog.openError(
                    getSite().getShell(),
                    "Invalid Flow Filter Regex",
                    candidate.getError());

            return;
        }

        flowFilterText = requested;
        flowFilter = candidate;

        refresh();
    }

    private void clearFlowFilter() {
        if (!flowFilter.isActive()) {
            return;
        }

        flowFilterText = "";
        flowFilter =
                FlowFilterMatcher.compile("");

        refresh();
    }

    private List<FlowEntry> filteredEntries(
            FlowExplorerService service,
            List<FlowEntry> entries) {

        if (!flowFilter.isActive()
                || entries == null
                || entries.isEmpty()) {

            return entries;
        }

        List<FlowEntry> result =
                new ArrayList<FlowEntry>();

        for (FlowEntry entry :
                entries) {

            IFile file =
                    service == null
                            ? null
                            : service.resolve(
                                    entry);

            if (flowFilter.matches(
                    entry,
                    file)) {

                result.add(entry);
            }
        }

        return result;
    }

    private void updateSummaryLabel(
            FlowExplorerService service,
            int count) {

        if (summaryLabel == null
                || summaryLabel.isDisposed()
                || service == null) {

            return;
        }

        StringBuilder text =
                new StringBuilder();

        text.append(count)
                .append(
                        count == 1
                                ? " file in current flow"
                                : " files in current flow")
                .append(
                        service.isAutoCapture()
                                ? " • automatic capture on"
                                : " • manual capture");

        if (flowFilter.isActive()) {
            text.append(" • filter: ")
                    .append(
                            flowFilter.isRegex()
                                    ? "regex "
                                    : "")
                    .append(
                            flowFilterText);
        }

        if (architectureFocus != null) {
            IFile root =
                    fileForResourcePath(
                            architectureFocus
                                    .getRootResourcePath());

            String name =
                    root != null
                            && root.exists()
                                    ? root.getName()
                                    : architectureFocus
                                            .getRootResourcePath();

            text.append(" • focus: ")
                    .append(name);

            if (focusLoading) {
                text.append(
                        " (calculating...)");
            } else {
                int related =
                        Math.max(
                                0,
                                architectureFocus
                                        .getRelatedCount()
                                - 1);

                text.append(" (")
                        .append(related)
                        .append(
                                related == 1
                                        ? " related file)"
                                        : " related files)");
            }
        }

        summaryLabel.setText(
                text.toString());

        summaryLabel.getParent()
                .layout(
                        true,
                        true);
    }

    private Color createFocusDimColor() {
        if (viewer == null
                || viewer.getTree()
                        .isDisposed()) {

            return null;
        }

        Color foreground =
                viewer.getTree()
                        .getForeground();

        Color background =
                viewer.getTree()
                        .getBackground();

        int red =
                (foreground.getRed() * 40
                        + background.getRed()
                                * 60)
                / 100;

        int green =
                (foreground.getGreen() * 40
                        + background.getGreen()
                                * 60)
                / 100;

        int blue =
                (foreground.getBlue() * 40
                        + background.getBlue()
                                * 60)
                / 100;

        return new Color(
                viewer.getTree()
                        .getDisplay(),
                red,
                green,
                blue);
    }

    private void requestArchitectureFocus(
            FlowEntry entry) {

        if (!isFocusRootEligible(
                entry)) {

            return;
        }

        FlowExplorerService flowService =
                service();

        FlowDependencyIndexService dependencyService =
                Activator
                        .getFlowDependencyIndexService();

        if (flowService == null
                || dependencyService == null) {

            return;
        }

        IFile root =
                flowService.resolve(
                        entry);

        if (root == null
                || !root.exists()) {

            return;
        }

        final String rootPath =
                entry.getResourcePath();

        if (architectureFocus != null
                && rootPath.equals(
                        architectureFocus
                                .getRootResourcePath())
                && !focusLoading) {

            return;
        }

        if (focusJob != null) {
            focusJob.cancel();
            focusJob = null;
        }

        final int generation =
                ++focusRequestGeneration;

        Map<String, Integer> initial =
                new LinkedHashMap<String, Integer>();

        initial.put(
                rootPath,
                Integer.valueOf(0));

        architectureFocus =
                new FlowFocusResult(
                        rootPath,
                        initial);

        focusLoading = true;

        if (viewer != null
                && !viewer.getControl()
                        .isDisposed()) {

            viewer.refresh();
        }

        final List<FlowEntry> snapshot =
                flowService
                        .getCurrentEntriesSnapshot();

        int count =
                snapshot.size();

        updateSummaryLabel(
                flowService,
                count);

        final IFile focusRoot =
                root;

        focusJob =
                new Job(
                        "Compute JSF Flow architecture focus") {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        final FlowFocusResult result =
                                dependencyService.focus(
                                        focusRoot,
                                        snapshot,
                                        monitor);

                        if (monitor.isCanceled()) {
                            return Status.CANCEL_STATUS;
                        }

                        PlatformUI.getWorkbench()
                                .getDisplay()
                                .asyncExec(
                                        new Runnable() {
                                            @Override
                                            public void run() {

                                                if (generation
                                                        != focusRequestGeneration
                                                        || viewer == null
                                                        || viewer.getControl()
                                                                .isDisposed()) {

                                                    return;
                                                }

                                                architectureFocus =
                                                        result;

                                                focusLoading =
                                                        false;

                                                focusJob =
                                                        null;

                                                viewer.refresh();

                                                FlowExplorerService currentService =
                                                        service();

                                                FlowDefinition currentFlow =
                                                        currentService == null
                                                                ? null
                                                                : currentService
                                                                        .getCurrentFlow();

                                                updateSummaryLabel(
                                                        currentService,
                                                        currentFlow == null
                                                                ? 0
                                                                : currentFlow
                                                                        .getEntries()
                                                                        .size());
                                            }
                                        });

                        return Status.OK_STATUS;
                    }
                };

        focusJob.setSystem(true);

        /*
         * Tiny debounce: clicking through several files quickly should not
         * start multiple AST/binding walks. The selected root is highlighted
         * immediately while the final background request is pending.
         */
        focusJob.schedule(
                180L);
    }

    private void clearArchitectureFocus(
            boolean refreshViewer) {

        focusRequestGeneration++;

        if (focusJob != null) {
            focusJob.cancel();
            focusJob = null;
        }

        architectureFocus = null;
        focusLoading = false;

        if (refreshViewer
                && viewer != null
                && !viewer.getControl()
                        .isDisposed()) {

            viewer.refresh();

            FlowExplorerService currentService =
                    service();

            FlowDefinition current =
                    currentService == null
                            ? null
                            : currentService
                                    .getCurrentFlow();

            if (currentService != null) {
                updateSummaryLabel(
                        currentService,
                        current == null
                                ? 0
                                : current.getEntries()
                                        .size());
            }
        }
    }

    private boolean isFocusRootEligible(
            FlowEntry entry) {

        if (entry == null
                || FlowCategoryClassifier.TEST.equals(
                        entry.getCategory())
                || FlowCategoryClassifier.RESOURCE.equals(
                        entry.getCategory())) {

            return false;
        }

        IFile file =
                fileForResourcePath(
                        entry.getResourcePath());

        return file != null
                && file.exists()
                && "java".equalsIgnoreCase(
                        file.getFileExtension());
    }

    private boolean isFocusStylingEligible(
            FlowEntry entry) {

        if (entry == null
                || FlowCategoryClassifier.TEST.equals(
                        entry.getCategory())
                || FlowCategoryClassifier.RESOURCE.equals(
                        entry.getCategory())) {

            return false;
        }

        IFile file =
                fileForResourcePath(
                        entry.getResourcePath());

        return file != null
                && file.exists()
                && ("java".equalsIgnoreCase(
                        file.getFileExtension())
                        || FlowCategoryClassifier.VIEW.equals(
                                entry.getCategory())
                        || FlowCategoryClassifier.SCHEMA.equals(
                                entry.getCategory()));
    }

    private boolean isFocusActive() {
        return architectureFocus != null;
    }

    private boolean isFocusRoot(
            String resourcePath) {

        return architectureFocus != null
                && resourcePath != null
                && resourcePath.equals(
                        architectureFocus
                                .getRootResourcePath());
    }

    private boolean isFocusRelated(
            String resourcePath) {

        return architectureFocus != null
                && resourcePath != null
                && architectureFocus
                        .isRelated(
                                resourcePath);
    }

    private int relatedCount(
            FlowCategoryNode category) {

        if (category == null
                || architectureFocus == null) {

            return 0;
        }

        int count = 0;

        for (FlowEntry entry :
                category.getEntries()) {

            if (architectureFocus
                    .isRelated(
                            entry.getResourcePath())) {

                count++;
            }
        }

        return count;
    }

    private void installProblemMarkerListener() {
        problemMarkerListener =
                new IResourceChangeListener() {
                    @Override
                    public void resourceChanged(
                            IResourceChangeEvent event) {

                        IResourceDelta delta =
                                event.getDelta();

                        if (delta == null
                                || !containsMarkerChange(
                                        delta)) {

                            return;
                        }

                        refreshIfOpen();
                    }
                };

        ResourcesPlugin.getWorkspace()
                .addResourceChangeListener(
                        problemMarkerListener,
                        IResourceChangeEvent.POST_CHANGE);
    }

    private static boolean containsMarkerChange(
            IResourceDelta delta) {

        if (delta == null) {
            return false;
        }

        if ((delta.getFlags()
                & IResourceDelta.MARKERS) != 0) {

            return true;
        }

        for (IResourceDelta child :
                delta.getAffectedChildren()) {

            if (containsMarkerChange(child)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasError(
            IFile file) {

        if (file == null
                || !file.exists()) {

            return false;
        }

        try {
            return file.findMaxProblemSeverity(
                    IMarker.PROBLEM,
                    true,
                    IResource.DEPTH_INFINITE)
                    >= IMarker.SEVERITY_ERROR;

        } catch (CoreException e) {
            return false;
        }
    }

    private static int errorCount(
            FlowCategoryNode category) {

        int count = 0;

        for (FlowEntry entry :
                category.getEntries()) {

            IFile file =
                    ResourcesPlugin.getWorkspace()
                            .getRoot()
                            .getFile(
                                    new org.eclipse.core.runtime.Path(
                                            entry.getResourcePath()));

            if (hasError(file)) {
                count++;
            }
        }

        return count;
    }

    private void zoomTreeFont(
            int direction) {

        if (viewer == null
                || viewer.getTree()
                        .isDisposed()
                || direction == 0) {

            return;
        }

        int next =
                treeFontDelta
                + (direction > 0
                        ? 1
                        : -1);

        if (next < -4) {
            next = -4;
        } else if (next > 12) {
            next = 12;
        }

        if (next == treeFontDelta) {
            return;
        }

        if (treeBaseFontData == null
                || treeBaseFontData.length == 0) {

            treeBaseFontData =
                    copyFontData(
                            viewer.getTree()
                                    .getFont()
                                    .getFontData());
        }

        FontData[] data =
                new FontData[
                        treeBaseFontData.length];

        for (int i = 0;
                i < treeBaseFontData.length;
                i++) {

            data[i] =
                    new FontData(
                            treeBaseFontData[i]
                                    .getName(),
                            Math.max(
                                    6,
                                    treeBaseFontData[i]
                                            .getHeight()
                                    + next),
                            treeBaseFontData[i]
                                    .getStyle());
        }

        Font replacement =
                new Font(
                        viewer.getTree()
                                .getDisplay(),
                        data);

        Font old =
                treeZoomFont;

        treeZoomFont =
                replacement;
        treeFontDelta = next;

        viewer.getTree()
                .setFont(replacement);

        if (old != null
                && !old.isDisposed()) {

            old.dispose();
        }
    }

    private static FontData[] copyFontData(
            FontData[] source) {

        if (source == null) {
            return new FontData[0];
        }

        FontData[] copy =
                new FontData[
                        source.length];

        for (int i = 0;
                i < source.length;
                i++) {

            copy[i] =
                    new FontData(
                            source[i].getName(),
                            source[i].getHeight(),
                            source[i].getStyle());
        }

        return copy;
    }

    private void highlightActiveEditorFile() {
        IFile file =
                EditorContext.currentFile();

        highlightFile(file);
    }

    private void highlightFile(
            IFile file) {

        if (viewer == null
                || viewer.getControl()
                        .isDisposed()) {

            return;
        }

        Object match = null;

        if (file != null) {
            String path =
                    file.getFullPath()
                            .toPortableString();

            match =
                    findTreeElementForPath(
                            viewer.getInput(),
                            path);
        }

        suppressOpenOnSelection = true;

        try {
            if (match != null) {
                viewer.setSelection(
                        new StructuredSelection(
                                match),
                        true);
            } else {
                viewer.setSelection(
                        StructuredSelection.EMPTY);
            }

        } finally {
            suppressOpenOnSelection = false;
        }
    }

    private static Object findTreeElementForPath(
            Object element,
            String path) {

        if (element == null
                || path == null) {

            return null;
        }

        if (element instanceof List<?>) {
            for (Object child :
                    (List<?>) element) {

                Object match =
                        findTreeElementForPath(
                                child,
                                path);

                if (match != null) {
                    return match;
                }
            }

            return null;
        }

        if (element instanceof FlowEntry) {
            return path.equals(
                    ((FlowEntry) element)
                            .getResourcePath())
                    ? element
                    : null;
        }

        if (element instanceof FlowImpactTestNode) {
            return path.equals(
                    ((FlowImpactTestNode) element)
                            .getEntry()
                            .getResourcePath())
                    ? element
                    : null;
        }

        if (element instanceof FlowCategoryNode) {
            return findTreeElementForPath(
                    ((FlowCategoryNode) element)
                            .getChildren(),
                    path);
        }

        if (element instanceof FlowImpactSourceNode) {
            FlowImpactSourceNode source =
                    (FlowImpactSourceNode) element;

            if (path.equals(
                    source.getSourceResourcePath())) {

                return source;
            }

            return findTreeElementForPath(
                    source.getMethods(),
                    path);
        }

        if (element instanceof FlowImpactMethodNode) {
            return findTreeElementForPath(
                    ((FlowImpactMethodNode) element)
                            .getTests(),
                    path);
        }

        if (element instanceof FlowOtherTestsNode) {
            return findTreeElementForPath(
                    ((FlowOtherTestsNode) element)
                            .getEntries(),
                    path);
        }

        return null;
    }

    private void openAllFiles() {
        FlowExplorerService service =
                service();

        FlowDefinition flow =
                service == null
                        ? null
                        : service.getCurrentFlow();

        if (flow == null) {
            return;
        }

        IWorkbenchWindow window =
                PlatformUI.getWorkbench()
                        .getActiveWorkbenchWindow();

        IWorkbenchPage page =
                window == null
                        ? null
                        : window.getActivePage();

        if (page == null) {
            return;
        }

        for (FlowEntry entry :
                flow.getEntries()) {

            IFile file =
                    service.resolve(entry);

            if (file == null) {
                continue;
            }

            try {
                IDE.openEditor(
                        page,
                        file,
                        false);

            } catch (Exception e) {
                // Continue opening the remaining flow files.
            }
        }
    }

    private void focusEditorTabs() {
        FlowExplorerService service =
                service();

        FlowDefinition flow =
                service == null
                        ? null
                        : service.getCurrentFlow();

        if (flow == null) {
            return;
        }

        java.util.Set<String> keep =
                new java.util.HashSet<String>();

        for (FlowEntry entry :
                flow.getEntries()) {

            keep.add(
                    entry.getResourcePath());
        }

        IWorkbenchWindow window =
                PlatformUI.getWorkbench()
                        .getActiveWorkbenchWindow();

        IWorkbenchPage page =
                window == null
                        ? null
                        : window.getActivePage();

        if (page == null) {
            return;
        }

        java.util.List<IEditorReference> close =
                new java.util.ArrayList<IEditorReference>();

        for (IEditorReference reference :
                page.getEditorReferences()) {

            try {
                IEditorInput input =
                        reference.getEditorInput();

                if (!(input
                        instanceof IFileEditorInput)) {

                    continue;
                }

                IFile file =
                        ((IFileEditorInput) input)
                                .getFile();

                String path =
                        file.getFullPath()
                                .toPortableString();

                if (!keep.contains(path)) {
                    close.add(reference);
                }

            } catch (org.eclipse.ui.PartInitException e) {
                // Leave editors whose input cannot be inspected.
            }
        }

        if (close.isEmpty()) {
            return;
        }

        page.closeEditors(
                close.toArray(
                        new IEditorReference[
                                close.size()]),
                true);
    }

    private void addCurrentFile() {
        FlowExplorerService service =
                service();

        IFile file =
                EditorContext.currentFile();

        if (service != null
                && file != null) {

            service.addFile(file);
            refresh();
        }
    }

    private void createFlow() {
        InputDialog dialog =
                new InputDialog(
                        getSite().getShell(),
                        "New Development Flow",
                        "Flow name:",
                        "",
                        null);

        if (dialog.open()
                != Window.OK) {

            return;
        }

        FlowExplorerService service =
                service();

        if (service != null) {
            service.createFlow(
                    dialog.getValue());

            refresh();
        }
    }

    private void renameFlow() {
        FlowExplorerService service =
                service();

        if (service == null) {
            return;
        }

        InputDialog dialog =
                new InputDialog(
                        getSite().getShell(),
                        "Rename Development Flow",
                        "New name:",
                        service.getCurrentFlowName(),
                        null);

        if (dialog.open()
                == Window.OK) {

            service.renameCurrentFlow(
                    dialog.getValue());

            refresh();
        }
    }

    private void deleteFlow() {
        FlowExplorerService service =
                service();

        if (service == null) {
            return;
        }

        if (service.getFlowNames().size()
                <= 1) {

            MessageDialog.openInformation(
                    getSite().getShell(),
                    "Flow Explorer",
                    "At least one flow is kept.");

            return;
        }

        if (!MessageDialog.openConfirm(
                getSite().getShell(),
                "Delete Development Flow",
                "Delete flow '"
                        + service.getCurrentFlowName()
                        + "'?")) {

            return;
        }

        service.deleteCurrentFlow();
        refresh();
    }

    private void removeSelected() {
        if (viewer == null) {
            return;
        }

        IStructuredSelection selection =
                viewer.getStructuredSelection();

        Object first =
                selection.getFirstElement();

        FlowEntry entry = null;

        if (first instanceof FlowEntry) {
            entry =
                    (FlowEntry) first;

        } else if (first
                instanceof FlowImpactTestNode) {

            entry =
                    ((FlowImpactTestNode) first)
                            .getEntry();
        }

        if (entry == null) {
            return;
        }

        FlowExplorerService service =
                service();

        if (service != null) {
            service.removeFile(
                    entry.getResourcePath());

            refresh();
        }
    }

    private void openImpactSource(
            FlowImpactSourceNode source) {

        if (source == null) {
            return;
        }

        IFile file =
                ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .getFile(
                                new org.eclipse.core.runtime.Path(
                                        source.getSourceResourcePath()));

        if (!file.exists()) {
            return;
        }

        try {
            IWorkbenchWindow window =
                    PlatformUI.getWorkbench()
                            .getActiveWorkbenchWindow();

            IWorkbenchPage page =
                    window == null
                            ? null
                            : window.getActivePage();

            if (page != null) {
                IDE.openEditor(
                        page,
                        file,
                        true);
            }

        } catch (Exception e) {
            WebSphereStatusLine.show(
                    "Could not open impacted source: "
                    + e.getMessage());
        }
    }

    private void openImpactMethod(
            FlowImpactMethodNode method) {

        if (method == null
                || method.getMethodHandleIdentifier() == null
                || method.getMethodHandleIdentifier()
                        .isEmpty()) {

            return;
        }

        IJavaElement element =
                JavaCore.create(
                        method.getMethodHandleIdentifier());

        if (element instanceof IMethod
                && element.exists()) {

            JavaEditorOpener.open(
                    (IMethod) element);
        }
    }




    private void openTransactionLens() {
        final IFile file =
                selectedTestFile();

        if (file == null
                || !file.exists()
                || !"java".equalsIgnoreCase(
                        file.getFileExtension())) {

            WebSphereStatusLine.show(
                    "Select a Java test file/class in the Flow Explorer, or open a Java test in the editor, before using Tx Lens.");

            return;
        }

        Job job =
                new Job(
                        "Analyze test transaction boundaries") {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        final FlowTransactionLensReport report =
                                FlowTransactionLensAnalyzer
                                        .analyze(
                                                file,
                                                monitor);

                        if (monitor.isCanceled()) {
                            return Status.CANCEL_STATUS;
                        }

                        PlatformUI.getWorkbench()
                                .getDisplay()
                                .asyncExec(
                                        new Runnable() {
                                            @Override
                                            public void run() {

                                                if (viewer == null
                                                        || viewer.getControl()
                                                                .isDisposed()) {

                                                    return;
                                                }

                                                FlowTransactionLensDialog dialog =
                                                        new FlowTransactionLensDialog(
                                                                getSite()
                                                                        .getShell(),
                                                                report);

                                                dialog.open();
                                            }
                                        });

                        return Status.OK_STATUS;
                    }
                };

        job.setUser(true);
        job.schedule();
    }

    private IFile selectedTestFile() {
        if (viewer != null
                && !viewer.getControl()
                        .isDisposed()) {

            Object first =
                    viewer.getStructuredSelection()
                            .getFirstElement();

            IFile selected =
                    testFileForElement(
                            first);

            if (selected != null) {
                return selected;
            }

            if (first
                    instanceof FlowTestResultCaseNode) {

                return fileForResourcePath(
                        ((FlowTestResultCaseNode)
                                first)
                                .getResult()
                                .getTestFilePath());
            }

            if (first
                    instanceof FlowStackTraceNode) {

                return fileForResourcePath(
                        ((FlowStackTraceNode)
                                first)
                                .getResult()
                                .getTestFilePath());
            }
        }

        IFile active =
                EditorContext.currentFile();

        return active != null
                && active.exists()
                && "java".equalsIgnoreCase(
                        active.getFileExtension())
                        ? active
                        : null;
    }

    private IFile testFileForElement(
            Object element) {

        if (element instanceof FlowEntry) {
            FlowEntry entry =
                    (FlowEntry) element;

            if (!FlowCategoryClassifier.TEST.equals(
                    entry.getCategory())) {

                return null;
            }

            FlowExplorerService current =
                    service();

            return current == null
                    ? null
                    : current.resolve(
                            entry);
        }

        if (element
                instanceof FlowImpactTestNode) {

            FlowExplorerService current =
                    service();

            return current == null
                    ? null
                    : current.resolve(
                            ((FlowImpactTestNode) element)
                                    .getEntry());
        }

        if (element
                instanceof FlowTestResultClassNode) {

            return fileForResourcePath(
                    ((FlowTestResultClassNode)
                            element)
                            .getTestFilePath());
        }

        return null;
    }

    private void clearTestResults() {
        FlowExplorerService flow =
                service();

        FlowTestResultStore store =
                Activator.getFlowTestResultStore();

        if (flow == null
                || store == null) {

            return;
        }

        store.clear(
                flow.getCurrentFlowName());

        refresh();

        WebSphereStatusLine.show(
                "Cleared the last Flow test results.");
    }

    private void openTestResultClass(
            FlowTestResultClassNode node) {

        if (node == null) {
            return;
        }

        openWorkspaceFile(
                node.getTestFilePath());
    }

    private void openTestResultCase(
            FlowTestResultCaseNode node) {

        if (node == null) {
            return;
        }

        FlowTestCaseResult result =
                node.getResult();

        String methodName =
                result.getMethodName();

        IFile file =
                fileForResourcePath(
                        result.getTestFilePath());

        if (file == null
                || !file.exists()) {

            return;
        }

        if (methodName != null
                && !methodName.isEmpty()
                && !methodName.startsWith("<")) {

            ICompilationUnit unit =
                    JavaCore.createCompilationUnitFrom(
                            file);

            if (unit != null
                    && unit.exists()) {

                try {
                    String wantedClass =
                            result.getClassName();

                    for (IType type :
                            unit.getAllTypes()) {

                        if (!wantedClass.isEmpty()
                                && !wantedClass.equals(
                                        type.getFullyQualifiedName())) {

                            continue;
                        }

                        for (IMethod method :
                                type.getMethods()) {

                            if (methodName.equals(
                                    method.getElementName())) {

                                JavaEditorOpener.open(
                                        method);
                                return;
                            }
                        }
                    }

                } catch (Exception e) {
                    // Fall back to opening the test source file.
                }
            }
        }

        openWorkspaceFile(
                result.getTestFilePath());
    }

    private void openStackTrace(
            FlowStackTraceNode node) {

        if (node == null) {
            return;
        }

        FlowStackTraceDialog dialog =
                new FlowStackTraceDialog(
                        getSite().getShell(),
                        node.getResult());

        dialog.open();
    }

    private void openWorkspaceFile(
            String resourcePath) {

        IFile file =
                fileForResourcePath(
                        resourcePath);

        if (file == null
                || !file.exists()) {

            return;
        }

        try {
            IWorkbenchWindow window =
                    PlatformUI.getWorkbench()
                            .getActiveWorkbenchWindow();

            IWorkbenchPage page =
                    window == null
                            ? null
                            : window.getActivePage();

            if (page != null) {
                IDE.openEditor(
                        page,
                        file,
                        true);
            }

        } catch (Exception e) {
            WebSphereStatusLine.show(
                    "Could not open test source: "
                    + e.getMessage());
        }
    }

    private static IFile fileForResourcePath(
            String resourcePath) {

        if (resourcePath == null
                || resourcePath.isEmpty()) {

            return null;
        }

        return ResourcesPlugin.getWorkspace()
                .getRoot()
                .getFile(
                        new org.eclipse.core.runtime.Path(
                                resourcePath));
    }

    private void openEntry(
            FlowEntry entry) {

        FlowExplorerService service =
                service();

        if (service == null) {
            return;
        }

        IFile file =
                service.resolve(entry);

        if (file == null) {
            MessageDialog.openWarning(
                    getSite().getShell(),
                    "Flow Explorer",
                    "This workspace file no longer exists:\n\n"
                            + entry.getResourcePath());

            return;
        }

        try {
            IWorkbenchWindow window =
                    PlatformUI.getWorkbench()
                            .getActiveWorkbenchWindow();

            IWorkbenchPage page =
                    window == null
                            ? null
                            : window.getActivePage();

            if (page != null) {
                IDE.openEditor(
                        page,
                        file,
                        true);
            }

        } catch (Exception e) {
            MessageDialog.openError(
                    getSite().getShell(),
                    "Flow Explorer",
                    "Could not open file:\n\n"
                            + e.getMessage());
        }
    }

    private FlowExplorerService service() {
        return Activator.getFlowExplorerService();
    }

    @Override
    public void setFocus() {
        if (viewer != null
                && !viewer.getControl()
                        .isDisposed()) {

            viewer.getControl()
                    .setFocus();
        }
    }

    @Override
    public void dispose() {
        instance = null;

        focusRequestGeneration++;

        if (focusJob != null) {
            focusJob.cancel();
            focusJob = null;
        }

        if (problemMarkerListener != null) {
            ResourcesPlugin.getWorkspace()
                    .removeResourceChangeListener(
                            problemMarkerListener);
            problemMarkerListener = null;
        }

        if (treeZoomFont != null
                && !treeZoomFont.isDisposed()) {

            treeZoomFont.dispose();
            treeZoomFont = null;
        }

        if (focusDimColor != null
                && !focusDimColor.isDisposed()) {

            focusDimColor.dispose();
            focusDimColor = null;
        }

        super.dispose();
    }

    public static void refreshIfOpen() {
        final FlowExplorerView current =
                instance;

        if (current == null) {
            return;
        }

        PlatformUI.getWorkbench()
                .getDisplay()
                .asyncExec(
                        new Runnable() {
                            @Override
                            public void run() {
                                current.refresh();
                            }
                        });
    }


    public static void activeEditorChanged(
            final IFile file) {

        final FlowExplorerView current =
                instance;

        if (current == null) {
            return;
        }

        PlatformUI.getWorkbench()
                .getDisplay()
                .asyncExec(
                        new Runnable() {
                            @Override
                            public void run() {

                                current.highlightFile(
                                        file);
                            }
                        });
    }

    private static final class FlowContentProvider
            implements ITreeContentProvider {

        @Override
        public Object[] getElements(
                Object inputElement) {

            if (inputElement
                    instanceof List<?>) {

                return ((List<?>) inputElement)
                        .toArray();
            }

            return new Object[0];
        }

        @Override
        public Object[] getChildren(
                Object parentElement) {

            if (parentElement
                    instanceof FlowCategoryNode) {

                return ((FlowCategoryNode)
                        parentElement)
                        .getChildren()
                        .toArray();
            }

            if (parentElement
                    instanceof FlowImpactSourceNode) {

                return ((FlowImpactSourceNode)
                        parentElement)
                        .getMethods()
                        .toArray();
            }

            if (parentElement
                    instanceof FlowImpactMethodNode) {

                return ((FlowImpactMethodNode)
                        parentElement)
                        .getTests()
                        .toArray();
            }

            if (parentElement
                    instanceof FlowOtherTestsNode) {

                return ((FlowOtherTestsNode)
                        parentElement)
                        .getEntries()
                        .toArray();
            }

            if (parentElement
                    instanceof FlowTestRunNode) {

                return ((FlowTestRunNode)
                        parentElement)
                        .getChildren()
                        .toArray();
            }

            if (parentElement
                    instanceof FlowTestResultGroupNode) {

                return ((FlowTestResultGroupNode)
                        parentElement)
                        .getClasses()
                        .toArray();
            }

            if (parentElement
                    instanceof FlowTestResultClassNode) {

                return ((FlowTestResultClassNode)
                        parentElement)
                        .getCases()
                        .toArray();
            }

            if (parentElement
                    instanceof FlowTestResultCaseNode) {

                return ((FlowTestResultCaseNode)
                        parentElement)
                        .getChildren()
                        .toArray();
            }

            return new Object[0];
        }

        @Override
        public Object getParent(
                Object element) {

            return null;
        }

        @Override
        public boolean hasChildren(
                Object element) {

            if (element instanceof FlowCategoryNode) {
                return !((FlowCategoryNode) element)
                        .getChildren()
                        .isEmpty();
            }

            if (element instanceof FlowImpactSourceNode) {
                return !((FlowImpactSourceNode) element)
                        .getMethods()
                        .isEmpty();
            }

            if (element instanceof FlowImpactMethodNode) {
                return !((FlowImpactMethodNode) element)
                        .getTests()
                        .isEmpty();
            }

            if (element instanceof FlowOtherTestsNode) {
                return !((FlowOtherTestsNode) element)
                        .getEntries()
                        .isEmpty();
            }

            if (element instanceof FlowTestRunNode) {
                return !((FlowTestRunNode) element)
                        .getChildren()
                        .isEmpty();
            }

            if (element instanceof FlowTestResultGroupNode) {
                return !((FlowTestResultGroupNode) element)
                        .getClasses()
                        .isEmpty();
            }

            if (element instanceof FlowTestResultClassNode) {
                return !((FlowTestResultClassNode) element)
                        .getCases()
                        .isEmpty();
            }

            if (element instanceof FlowTestResultCaseNode) {
                return !((FlowTestResultCaseNode) element)
                        .getChildren()
                        .isEmpty();
            }

            return false;
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

    private static final class FlowLabelProvider
            extends LabelProvider
            implements IColorProvider {

        private final FlowExplorerView owner;

        FlowLabelProvider(
                FlowExplorerView owner) {

            this.owner = owner;
        }

        @Override
        public String getText(
                Object element) {

            if (element
                    instanceof FlowCategoryNode) {

                FlowCategoryNode category =
                        (FlowCategoryNode) element;

                int errors =
                        errorCount(category);

                boolean groupedTests =
                        FlowCategoryClassifier.TEST.equals(
                                category.getName())
                        && hasImpactGroups(
                                category);

                boolean focusCategory =
                        owner.isFocusActive()
                        && !FlowCategoryClassifier.TEST.equals(
                                category.getName())
                        && !FlowCategoryClassifier.RESOURCE.equals(
                                category.getName());

                int related =
                        focusCategory
                                ? owner.relatedCount(
                                        category)
                                : 0;

                return category.getName()
                        + " ("
                        + category.getEntries().size()
                        + ")"
                        + (groupedTests
                                ? "  [grouped by changed file]"
                                : "")
                        + (focusCategory
                                ? "  ["
                                        + related
                                        + " related]"
                                : "")
                        + (errors > 0
                                ? "  ["
                                        + errors
                                        + (errors == 1
                                                ? " error]"
                                                : " errors]")
                                : "");
            }

            if (element
                    instanceof FlowImpactSourceNode) {

                FlowImpactSourceNode source =
                        (FlowImpactSourceNode) element;

                IFile file =
                        fileForPath(
                                source.getSourceResourcePath());

                String name =
                        file.exists()
                                ? file.getName()
                                : source.getSourceResourcePath();

                int methods =
                        source.getMethods().size();

                int tests =
                        source.getUniqueTestCount();

                return "Impacted by "
                        + name
                        + "  ("
                        + methods
                        + (methods == 1
                                ? " method, "
                                : " methods, ")
                        + tests
                        + (tests == 1
                                ? " test)"
                                : " tests)");
            }

            if (element
                    instanceof FlowImpactMethodNode) {

                FlowImpactMethodNode method =
                        (FlowImpactMethodNode) element;

                int tests =
                        method.getTests().size();

                return method.getMethodLabel()
                        + "  ("
                        + tests
                        + (tests == 1
                                ? " test)"
                                : " tests)");
            }

            if (element
                    instanceof FlowImpactTestNode) {

                FlowImpactTestNode test =
                        (FlowImpactTestNode) element;

                return flowEntryLabel(
                        test.getEntry(),
                        test.getDepth(),
                        true);
            }

            if (element
                    instanceof FlowOtherTestsNode) {

                int count =
                        ((FlowOtherTestsNode) element)
                                .getEntries()
                                .size();

                return "Other tests  ("
                        + count
                        + ")";
            }


            if (element
                    instanceof FlowTestRunNode) {

                FlowTestRunSummary summary =
                        ((FlowTestRunNode) element)
                                .getSummary();

                String state =
                        summary.isCanceled()
                                ? "CANCELED"
                                : summary.hasFailures()
                                        ? "FAILED"
                                        : summary.getClassesRun() == 0
                                                ? "NO TESTS"
                                                : summary.getCaseCount() == 0
                                                        ? "NO CASES"
                                                        : "PASSED";

                String time =
                        new SimpleDateFormat(
                                "dd MMM HH:mm")
                                .format(
                                        new Date(
                                                summary.getFinishedAt()));

                int excluded =
                        summary.getArquillianSkipped()
                        + summary.getJpaSkipped()
                        + summary.getIntegrationSkipped();

                return "Last run: "
                        + state
                        + " — "
                        + time
                        + "  ("
                        + summary.getPassedCount()
                        + " passed, "
                        + summary.getFailedCount()
                        + " failed, "
                        + summary.getSkippedCount()
                        + " skipped, "
                        + summary.getClassesRun()
                        + (summary.getClassesRun() == 1
                                ? " class"
                                : " classes")
                        + (excluded > 0
                                ? ", "
                                        + excluded
                                        + " excluded"
                                : "")
                        + ")";
            }

            if (element
                    instanceof FlowTestResultGroupNode) {

                FlowTestResultGroupNode group =
                        (FlowTestResultGroupNode)
                                element;

                return (group.getKind()
                        == FlowTestResultGroupNode.FAILED
                                ? "Failed tests"
                                : "Skipped tests")
                        + "  ("
                        + group.getCaseCount()
                        + ")";
            }

            if (element
                    instanceof FlowTestResultClassNode) {

                FlowTestResultClassNode node =
                        (FlowTestResultClassNode)
                                element;

                int count =
                        node.getCases()
                                .size();

                return node.getSimpleClassName()
                        + "  ("
                        + count
                        + (count == 1
                                ? " case)"
                                : " cases)");
            }

            if (element
                    instanceof FlowTestResultCaseNode) {

                FlowTestCaseResult result =
                        ((FlowTestResultCaseNode)
                                element)
                                .getResult();

                String prefix;

                if (result.getStatus()
                        == FlowTestCaseResult.FAILURE) {

                    prefix = "✗ ";

                } else if (result.getStatus()
                        == FlowTestCaseResult.ERROR) {

                    prefix = "! ";

                } else if (result.getStatus()
                        == FlowTestCaseResult.SKIPPED) {

                    prefix = "○ ";

                } else {
                    prefix = "✓ ";
                }

                String firstLine =
                        compactTraceLine(
                                result.getFirstTraceLine());

                return prefix
                        + result.getMethodName()
                        + (firstLine.isEmpty()
                                ? ""
                                : "  —  "
                                        + firstLine);
            }

            if (element
                    instanceof FlowStackTraceNode) {

                return "Stack trace…  (click to open)";
            }

            if (element
                    instanceof FlowEntry) {

                FlowEntry entry =
                        (FlowEntry) element;

                return flowEntryLabel(
                        entry,
                        entry.getImpactOrigins()
                                .isEmpty()
                                ? entry.getImpactDepth()
                                : 0,
                        FlowCategoryClassifier.TEST.equals(
                                entry.getCategory()));
            }

            return super.getText(element);
        }

        private static String compactTraceLine(
                String value) {

            if (value == null) {
                return "";
            }

            String text =
                    value.trim();

            return text.length() <= 140
                    ? text
                    : text.substring(
                            0,
                            137)
                            + "...";
        }

        private static boolean hasImpactGroups(
                FlowCategoryNode category) {

            for (Object child :
                    category.getChildren()) {

                if (child instanceof FlowImpactSourceNode) {
                    return true;
                }
            }

            return false;
        }

        private String flowEntryLabel(
                FlowEntry entry,
                int depth,
                boolean showDepth) {

            IFile file =
                    fileForPath(
                            entry.getResourcePath());

            if (!file.exists()) {
                return entry.getResourcePath()
                        + "  [missing]";
            }

            StringBuilder label =
                    new StringBuilder();

            if (hasError(file)) {
                label.append(
                        "[ERROR]  ");
            }

            if (owner.isFocusActive()
                    && owner.isFocusStylingEligible(
                            entry)) {

                if (owner.isFocusRoot(
                        entry.getResourcePath())) {

                    label.append(
                            "[FOCUS]  ");

                } else if (owner.isFocusRelated(
                        entry.getResourcePath())) {

                    label.append(
                            "•  ");
                }
            }

            if (FlowCategoryClassifier.PERSISTENCE.equals(
                    entry.getCategory())
                    && FlowJavaSemantics.isEntity(
                            file)) {

                label.append(
                        "[ENTITY]  ");
            }

            if (showDepth
                    && depth > 0) {

                if (depth == 1) {
                    label.append(
                            "[DIRECT]  ");
                } else {
                    label.append('[')
                            .append(depth)
                            .append(
                                    " calls away]  ");
                }
            }

            label.append(
                    file.getName())
                    .append("  —  ")
                    .append(
                            file.getProjectRelativePath()
                                    .toPortableString());

            return label.toString();
        }

        private static IFile fileForPath(
                String path) {

            return ResourcesPlugin.getWorkspace()
                    .getRoot()
                    .getFile(
                            new org.eclipse.core.runtime.Path(
                                    path));
        }

        @Override
        public Color getForeground(
                Object element) {

            Display display =
                    Display.getCurrent();

            if (display == null) {
                return null;
            }

            if (element instanceof FlowTestRunNode) {
                FlowTestRunSummary summary =
                        ((FlowTestRunNode) element)
                                .getSummary();

                if (summary.hasFailures()) {
                    return display.getSystemColor(
                            SWT.COLOR_RED);
                }

                if (summary.isSuccessful()) {
                    return display.getSystemColor(
                            SWT.COLOR_DARK_GREEN);
                }

                return null;
            }

            if (element instanceof FlowTestResultGroupNode) {
                FlowTestResultGroupNode group =
                        (FlowTestResultGroupNode)
                                element;

                return group.getKind()
                        == FlowTestResultGroupNode.FAILED
                                ? display.getSystemColor(
                                        SWT.COLOR_RED)
                                : null;
            }

            if (element instanceof FlowTestResultClassNode) {
                FlowTestResultClassNode node =
                        (FlowTestResultClassNode)
                                element;

                return node.getGroupKind()
                        == FlowTestResultGroupNode.FAILED
                                ? display.getSystemColor(
                                        SWT.COLOR_RED)
                                : null;
            }

            if (element instanceof FlowTestResultCaseNode) {
                FlowTestCaseResult result =
                        ((FlowTestResultCaseNode)
                                element)
                                .getResult();

                if (result.isFailed()) {
                    return display.getSystemColor(
                            SWT.COLOR_RED);
                }

                return null;
            }

            if (element instanceof FlowStackTraceNode) {
                return display.getSystemColor(
                        SWT.COLOR_RED);
            }

            boolean error = false;

            if (element instanceof FlowCategoryNode) {
                FlowCategoryNode category =
                        (FlowCategoryNode)
                                element;

                error =
                        errorCount(category) > 0
                        || hasFailedRun(
                                category);

            } else if (element
                    instanceof FlowEntry) {

                error =
                        hasError(
                                fileForPath(
                                        ((FlowEntry) element)
                                                .getResourcePath()));

            } else if (element
                    instanceof FlowImpactTestNode) {

                error =
                        hasError(
                                fileForPath(
                                        ((FlowImpactTestNode) element)
                                                .getEntry()
                                                .getResourcePath()));

            } else if (element
                    instanceof FlowImpactSourceNode) {

                error =
                        hasError(
                                fileForPath(
                                        ((FlowImpactSourceNode) element)
                                                .getSourceResourcePath()));
            }

            if (error) {
                return display.getSystemColor(
                        SWT.COLOR_RED);
            }

            if (owner.isFocusActive()) {
                if (element
                        instanceof FlowCategoryNode) {

                    FlowCategoryNode category =
                            (FlowCategoryNode)
                                    element;

                    if (!FlowCategoryClassifier.TEST.equals(
                            category.getName())
                            && !FlowCategoryClassifier.RESOURCE.equals(
                                    category.getName())
                            && owner.relatedCount(
                                    category) == 0) {

                        return owner.focusDimColor;
                    }

                } else if (element
                        instanceof FlowEntry) {

                    FlowEntry entry =
                            (FlowEntry) element;

                    if (owner.isFocusStylingEligible(
                            entry)
                            && !owner.isFocusRelated(
                                    entry.getResourcePath())) {

                        return owner.focusDimColor;
                    }
                }
            }

            return null;
        }

        private static boolean hasFailedRun(
                FlowCategoryNode category) {

            if (!FlowCategoryClassifier.TEST.equals(
                    category.getName())) {

                return false;
            }

            for (Object child :
                    category.getChildren()) {

                if (child instanceof FlowTestRunNode
                        && ((FlowTestRunNode) child)
                                .getSummary()
                                .hasFailures()) {

                    return true;
                }
            }

            return false;
        }

        @Override
        public Color getBackground(
                Object element) {

            return null;
        }
    }
}
