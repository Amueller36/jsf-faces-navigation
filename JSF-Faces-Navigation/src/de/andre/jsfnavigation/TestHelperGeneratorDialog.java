package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public final class TestHelperGeneratorDialog
        extends Dialog {

    private static final int COPY_ID =
            IDialogConstants.CLIENT_ID + 17;

    private final TestHelperAnalysis analysis;
    private final List<TestTargetCandidate> targets;
    private final List<NewTestLocationCandidate> unitLocations;
    private final List<NewTestLocationCandidate> jpaLocations;

    private Combo modeCombo;
    private Combo targetCombo;
    private Button openTargetButton;
    private Button insertButton;
    private Label newLocationLabel;
    private Combo newLocationCombo;
    private Button copyPathButton;
    private StyledText snippetText;
    private Label copiedLabel;

    private boolean targetChosenManually;

    public TestHelperGeneratorDialog(
            Shell parentShell,
            TestHelperAnalysis analysis,
            List<TestTargetCandidate> targets,
            List<NewTestLocationCandidate> unitLocations,
            List<NewTestLocationCandidate> jpaLocations) {

        super(parentShell);

        this.analysis = analysis;

        this.targets =
                targets == null
                        ? new ArrayList<TestTargetCandidate>()
                        : new ArrayList<TestTargetCandidate>(
                                targets);

        this.unitLocations =
                unitLocations == null
                        ? new ArrayList<NewTestLocationCandidate>()
                        : new ArrayList<NewTestLocationCandidate>(
                                unitLocations);

        this.jpaLocations =
                jpaLocations == null
                        ? new ArrayList<NewTestLocationCandidate>()
                        : new ArrayList<NewTestLocationCandidate>(
                                jpaLocations);

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
                "Generate Test Helper — "
                + analysis.getSimpleDeclaringType()
                + "."
                + analysis.getMethodName());
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
                        3,
                        false);

        layout.marginWidth = 10;
        layout.marginHeight = 10;
        layout.horizontalSpacing = 8;
        layout.verticalSpacing = 7;

        area.setLayout(
                layout);

        Label selected =
                new Label(
                        area,
                        SWT.WRAP);

        selected.setText(
                "Selected: "
                + analysis.getSimpleDeclaringType()
                + "."
                + analysis.getMethodName()
                + "(...)"
                + "  •  "
                + analysis.getDependencies()
                        .size()
                + (analysis.getDependencies()
                        .size() == 1
                                ? " collaborator"
                                : " collaborators")
                + (analysis.isJpaDetected()
                        ? "  •  JPA detected"
                        : "")
                + "  •  "
                + targets.size()
                + (targets.size() == 1
                        ? " existing test candidate"
                        : " existing test candidates"));

        selected.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false,
                        3,
                        1));

        new Label(
                area,
                SWT.NONE)
                .setText(
                        "Generate:");

        modeCombo =
                new Combo(
                        area,
                        SWT.DROP_DOWN
                        | SWT.READ_ONLY);

        modeCombo.setItems(
                TestHelperSnippetGenerator
                        .modeNames());

        modeCombo.select(
                TestHelperSnippetGenerator
                        .defaultMode(
                                analysis));

        modeCombo.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        true,
                        false,
                        2,
                        1));

        modeCombo.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        refreshSnippet();
                        refreshNewTestLocations();

                        if (!targetChosenManually) {
                            selectBestTarget();
                        }
                    }
                });

        new Label(
                area,
                SWT.NONE)
                .setText(
                        "Existing test:");

        targetCombo =
                new Combo(
                        area,
                        SWT.DROP_DOWN
                        | SWT.READ_ONLY);

        for (TestTargetCandidate target :
                targets) {

            targetCombo.add(
                    target.getLabel());
        }

        targetCombo.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        true,
                        false));

        targetCombo.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        targetChosenManually =
                                true;

                        updateTargetButtons();
                    }
                });

        Composite targetActions =
                new Composite(
                        area,
                        SWT.NONE);

        GridLayout targetLayout =
                new GridLayout(
                        2,
                        true);

        targetLayout.marginWidth = 0;
        targetLayout.marginHeight = 0;
        targetLayout.horizontalSpacing = 4;

        targetActions.setLayout(
                targetLayout);

        targetActions.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        false,
                        false));

        openTargetButton =
                new Button(
                        targetActions,
                        SWT.PUSH);

        openTargetButton.setText(
                "Open Test");

        openTargetButton.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        TestTargetCandidate target =
                                selectedTarget();

                        if (target != null) {
                            JavaEditorOpener.open(
                                    target.getType());
                        }
                    }
                });

        insertButton =
                new Button(
                        targetActions,
                        SWT.PUSH);

        insertButton.setText(
                "Insert…");

        insertButton.setToolTipText(
                "Insert the currently generated snippet before the existing test class closing brace. Duplicate @Mock/@InjectMocks fields are skipped.");

        insertButton.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        insertIntoSelectedTest();
                    }
                });


        newLocationLabel =
                new Label(
                        area,
                        SWT.NONE);

        newLocationLabel.setText(
                "Suggested new test:");

        newLocationCombo =
                new Combo(
                        area,
                        SWT.DROP_DOWN
                        | SWT.READ_ONLY);

        newLocationCombo.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        true,
                        false));

        copyPathButton =
                new Button(
                        area,
                        SWT.PUSH);

        copyPathButton.setText(
                "Copy Path");

        copyPathButton.setToolTipText(
                "Copy the suggested workspace/project source location for a new test file.");

        copyPathButton.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        NewTestLocationCandidate location =
                                selectedNewLocation();

                        if (location != null) {
                            copyText(
                                    location.getLabel());

                            copiedLabel.setText(
                                    "Suggested test path copied.");
                        }
                    }
                });

        Label hint =
                new Label(
                        area,
                        SWT.WRAP);

        hint.setText(
                "Existing tests are found through Eclipse's JDT workspace type index, including separate test projects. "
                + "If none exists, the plug-in also ranks actual Java source roots and suggests where a new unit/JPA test file should live. "
                + "Projects such as *JUnit / *TestJPA / *TestEJB / *Regression and package similarity influence the ranking. Insert is always explicit.");

        hint.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false,
                        3,
                        1));

        snippetText =
                new StyledText(
                        area,
                        SWT.BORDER
                        | SWT.MULTI
                        | SWT.H_SCROLL
                        | SWT.V_SCROLL);

        snippetText.setFont(
                JFaceResources
                        .getTextFont());

        snippetText.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.FILL,
                        true,
                        true,
                        3,
                        1));

        copiedLabel =
                new Label(
                        area,
                        SWT.WRAP);

        copiedLabel.setText(
                analysis.isTruncated()
                        ? "Dependency traversal hit its safety bound; the snippet contains a note."
                        : "Review TODO values/assertions before copying/inserting.");

        copiedLabel.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        true,
                        false,
                        3,
                        1));

        refreshSnippet();
        refreshNewTestLocations();
        selectBestTarget();
        updateTargetButtons();

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(
            Composite parent) {

        createButton(
                parent,
                COPY_ID,
                "Copy",
                false);

        createButton(
                parent,
                IDialogConstants.OK_ID,
                "Close",
                true);
    }

    @Override
    protected void buttonPressed(
            int buttonId) {

        if (buttonId == COPY_ID) {
            copySnippet();
            return;
        }

        super.buttonPressed(
                buttonId);
    }

    private void refreshSnippet() {
        if (snippetText == null
                || snippetText.isDisposed()) {

            return;
        }

        snippetText.setText(
                TestHelperSnippetGenerator
                        .generate(
                                analysis,
                                selectedMode()));

        snippetText.setSelection(
                0);
    }

    private int selectedMode() {
        return modeCombo == null
                || modeCombo.isDisposed()
                || modeCombo.getSelectionIndex()
                        < 0
                        ? TestHelperSnippetGenerator
                                .defaultMode(
                                        analysis)
                        : modeCombo.getSelectionIndex();
    }


    private void refreshNewTestLocations() {
        if (newLocationCombo == null
                || newLocationCombo.isDisposed()) {

            return;
        }

        newLocationCombo.removeAll();

        List<NewTestLocationCandidate> locations =
                locationsForMode();

        for (NewTestLocationCandidate location :
                locations) {

            newLocationCombo.add(
                    location.getLabel());
        }

        if (!locations.isEmpty()) {
            newLocationCombo.select(
                    0);
        }

        boolean showSuggestion =
                targets.isEmpty();

        if (newLocationLabel != null
                && !newLocationLabel.isDisposed()) {

            newLocationLabel.setText(
                    showSuggestion
                            ? "Suggested new test:"
                            : "New test alternative:");
        }

        if (copyPathButton != null
                && !copyPathButton.isDisposed()) {

            copyPathButton.setEnabled(
                    !locations.isEmpty());
        }
    }

    private List<NewTestLocationCandidate> locationsForMode() {
        return selectedMode()
                == TestHelperSnippetGenerator.JPA_TEST
                        ? jpaLocations
                        : unitLocations;
    }

    private NewTestLocationCandidate selectedNewLocation() {
        if (newLocationCombo == null
                || newLocationCombo.isDisposed()) {

            return null;
        }

        List<NewTestLocationCandidate> locations =
                locationsForMode();

        int index =
                newLocationCombo.getSelectionIndex();

        return index >= 0
                && index < locations.size()
                        ? locations.get(index)
                        : null;
    }

    private void selectBestTarget() {
        if (targetCombo == null
                || targetCombo.isDisposed()
                || targets.isEmpty()) {

            updateTargetButtons();
            return;
        }

        int index =
                TestTargetFinder
                        .bestIndexForMode(
                                targets,
                                selectedMode());

        if (index >= 0
                && index < targets.size()) {

            targetCombo.select(
                    index);
        }

        updateTargetButtons();
    }

    private TestTargetCandidate selectedTarget() {
        if (targetCombo == null
                || targetCombo.isDisposed()) {

            return null;
        }

        int index =
                targetCombo.getSelectionIndex();

        return index >= 0
                && index < targets.size()
                        ? targets.get(index)
                        : null;
    }

    private void updateTargetButtons() {
        boolean available =
                selectedTarget() != null;

        if (openTargetButton != null
                && !openTargetButton.isDisposed()) {

            openTargetButton.setEnabled(
                    available);
        }

        if (insertButton != null
                && !insertButton.isDisposed()) {

            insertButton.setEnabled(
                    available);
        }
    }

    private void insertIntoSelectedTest() {
        TestTargetCandidate target =
                selectedTarget();

        if (target == null
                || snippetText == null
                || snippetText.isDisposed()) {

            return;
        }

        boolean confirmed =
                MessageDialog.openQuestion(
                        getShell(),
                        "Insert Generated Test Helper",
                        "Insert the generated snippet into "
                                + target.getLabel()
                                + "?\n\n"
                                + "The plug-in will skip duplicate @Mock/@InjectMocks field names, but you should still review imports, TODO values, assertions and formatting.");

        if (!confirmed) {
            return;
        }

        try {
            String message =
                    TestSnippetInserter
                            .insert(
                                    target.getType(),
                                    snippetText.getText());

            copiedLabel.setText(
                    message);

            copiedLabel.getParent()
                    .layout(
                            true,
                            true);

            JavaEditorOpener.open(
                    target.getType());

        } catch (Exception e) {
            MessageDialog.openError(
                    getShell(),
                    "Could Not Insert Test Helper",
                    e.getMessage() == null
                            ? e.toString()
                            : e.getMessage());
        }
    }

    private void copySnippet() {
        if (snippetText == null
                || snippetText.isDisposed()) {

            return;
        }

        copyText(
                snippetText.getText());

        copiedLabel.setText(
                "Copied to clipboard.");

        copiedLabel.getParent()
                .layout(
                        true,
                        true);
    }


    private void copyText(
            String value) {

        Clipboard clipboard =
                new Clipboard(
                        getShell()
                                .getDisplay());

        try {
            clipboard.setContents(
                    new Object[] {
                            value == null
                                    ? ""
                                    : value
                    },
                    new Transfer[] {
                            TextTransfer
                                    .getInstance()
                    });

        } finally {
            clipboard.dispose();
        }
    }

    @Override
    protected Point getInitialSize() {
        return new Point(
                1120,
                760);
    }
}
