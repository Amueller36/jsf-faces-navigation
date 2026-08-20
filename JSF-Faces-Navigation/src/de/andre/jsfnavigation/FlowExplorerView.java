package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
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
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
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
                    FlowCategoryClassifier.SERVICE,
                    FlowCategoryClassifier.PERSISTENCE,
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
    createTestActionsRow(header);

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
                    3);

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
            "Automatically add JUnit tests from the caller hierarchy when a Java method you edit is touched.");

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

        viewer.setLabelProvider(
                new FlowLabelProvider());

        viewer.getTree()
                .addKeyListener(
                        new KeyAdapter() {
                            @Override
                            public void keyPressed(
                                    KeyEvent e) {

                                if (e.keyCode == SWT.DEL) {
                                    removeSelected();
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
                            openEntry(
                                    (FlowEntry) first);

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

        summaryLabel.setText(
                count
                + (count == 1
                        ? " file in current flow"
                        : " files in current flow")
                + (service.isAutoCapture()
                        ? " • automatic capture on"
                        : " • manual capture"));

        viewer.setInput(
                buildCategories(service));

        viewer.expandAll();
        highlightActiveEditorFile();
    }

    private List<FlowCategoryNode> buildCategories(
            FlowExplorerService service) {

        List<FlowCategoryNode> result =
                new ArrayList<FlowCategoryNode>();

        for (String category :
                CATEGORY_ORDER) {

            List<FlowEntry> entries =
                    service.entriesForCategory(
                            category);

            if (!entries.isEmpty()) {
                if (FlowCategoryClassifier.TEST.equals(
                        category)) {

                    result.add(
                            new FlowCategoryNode(
                                    category,
                                    entries,
                                    FlowImpactTreeBuilder
                                            .build(entries)));

                } else {
                    result.add(
                            new FlowCategoryNode(
                                    category,
                                    entries));
                }
            }
        }

        return result;
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

                return category.getName()
                        + " ("
                        + category.getEntries().size()
                        + ")"
                        + (groupedTests
                                ? "  [grouped by changed file]"
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

        private static String flowEntryLabel(
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

            boolean error = false;

            if (element instanceof FlowCategoryNode) {
                error =
                        errorCount(
                                (FlowCategoryNode)
                                        element) > 0;

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

            if (!error) {
                return null;
            }

            Display display =
                    Display.getCurrent();

            return display == null
                    ? null
                    : display.getSystemColor(
                            SWT.COLOR_RED);
        }

        @Override
        public Color getBackground(
                Object element) {

            return null;
        }
    }
}
