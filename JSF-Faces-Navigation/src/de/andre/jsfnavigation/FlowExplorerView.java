package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
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
                    FlowCategoryClassifier.ISP,
                    FlowCategoryClassifier.SERVICE,
                    FlowCategoryClassifier.PERSISTENCE,
                    FlowCategoryClassifier.RESOURCE,
                    FlowCategoryClassifier.TEST,
                    FlowCategoryClassifier.OTHER
            };

    private Combo flowCombo;
    private Button autoCaptureButton;
    private TreeViewer viewer;
    private Label summaryLabel;

    @Override
    public void createPartControl(
            Composite parent) {

        instance = this;

        parent.setLayout(
                new GridLayout(1, false));

        createHeader(parent);
        createTree(parent);
        refresh();
    }

    private void createHeader(
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
                new GridLayout(10, false);

        layout.marginWidth = 0;
        layout.marginHeight = 0;
        row.setLayout(layout);

        new Label(row, SWT.NONE)
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

        Button add =
                new Button(
                        row,
                        SWT.PUSH);

        add.setText("+ File");
        add.setToolTipText(
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
                new Button(
                        row,
                        SWT.PUSH);

        newFlow.setText("+ Flow");
        newFlow.setToolTipText(
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
                new Button(
                        row,
                        SWT.PUSH);

        rename.setText("Rename");

        rename.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        renameFlow();
                    }
                });

        Button delete =
                new Button(
                        row,
                        SWT.PUSH);

        delete.setText("Delete");

        delete.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        deleteFlow();
                    }
                });

        Button openAll =
                new Button(
                        row,
                        SWT.PUSH);

        openAll.setText("Open All");
        openAll.setToolTipText(
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
                new Button(
                        row,
                        SWT.PUSH);

        focusTabs.setText("Focus Tabs");
        focusTabs.setToolTipText(
                "Close workspace editors that are not in the current flow. Unsaved editors keep Eclipse's normal save prompt.");

        focusTabs.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        focusEditorTabs();
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

        Button remove =
                new Button(
                        row,
                        SWT.PUSH);

        remove.setText("Remove");
        remove.setToolTipText(
                "Remove the selected file from this flow.");

        remove.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        removeSelected();
                    }
                });

        summaryLabel =
                new Label(
                        parent,
                        SWT.NONE);

        summaryLabel.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false));
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

        viewer.setContentProvider(
                new FlowContentProvider());

        viewer.setLabelProvider(
                new FlowLabelProvider());

        viewer.addDoubleClickListener(
                new IDoubleClickListener() {
                    @Override
                    public void doubleClick(
                            DoubleClickEvent event) {

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
                result.add(
                        new FlowCategoryNode(
                                category,
                                entries));
            }
        }

        return result;
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

        if (!(first instanceof FlowEntry)) {
            return;
        }

        FlowExplorerService service =
                service();

        if (service != null) {
            service.removeFile(
                    ((FlowEntry) first)
                            .getResourcePath());

            refresh();
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

            return element
                    instanceof FlowCategoryNode
                    && !((FlowCategoryNode)
                            element)
                            .getEntries()
                            .isEmpty();
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
            extends LabelProvider {

        @Override
        public String getText(
                Object element) {

            if (element
                    instanceof FlowCategoryNode) {

                FlowCategoryNode category =
                        (FlowCategoryNode) element;

                return category.getName()
                        + " ("
                        + category.getEntries().size()
                        + ")";
            }

            if (element
                    instanceof FlowEntry) {

                FlowEntry entry =
                        (FlowEntry) element;

                IFile file =
                        ResourcesPlugin.getWorkspace()
                                .getRoot()
                                .getFile(
                                        new org.eclipse.core.runtime.Path(
                                                entry.getResourcePath()));

                if (file.exists()) {
                    return file.getName()
                            + "  —  "
                            + file.getProjectRelativePath()
                                    .toPortableString();
                }

                return entry.getResourcePath()
                        + "  [missing]";
            }

            return super.getText(element);
        }
    }
}
