package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
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
import org.eclipse.swt.custom.StyledText;

public final class BatchTestHelperGeneratorDialog
        extends Dialog {

    private static final int COPY_ID =
            IDialogConstants.CLIENT_ID + 41;

    private final List<TestHelperAnalysis> analyses;
    private final List<TestTargetCandidate> targets;
    private final List<NewTestLocationCandidate> unitLocations;
    private final List<NewTestLocationCandidate> jpaLocations;
    private final TestHelperLearnedFixtureReport learnedFixtures;

    private Combo modeCombo;
    private Combo targetCombo;
    private StyledText snippetText;
    private Label statusLabel;
    private Button reuseLearnedGivenButton;

    public BatchTestHelperGeneratorDialog(
            Shell parentShell,
            List<TestHelperAnalysis> analyses,
            List<TestTargetCandidate> targets,
            List<NewTestLocationCandidate> unitLocations,
            List<NewTestLocationCandidate> jpaLocations,
            TestHelperLearnedFixtureReport learnedFixtures) {

        super(parentShell);

        this.analyses =
                analyses == null
                        ? new ArrayList<TestHelperAnalysis>()
                        : new ArrayList<TestHelperAnalysis>(
                                analyses);

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

        this.learnedFixtures =
                learnedFixtures == null
                        ? TestHelperLearnedFixtureReport.empty()
                        : learnedFixtures;

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
                "Generate Tests for Selected Methods — "
                + simpleDeclaringType());
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

        Label summary =
                new Label(
                        area,
                        SWT.WRAP);

        summary.setText(
                analyses.size()
                + " selected production methods • "
                + mergedFixtureCount()
                + " detected fixture/query dependencies • "
                + learnedFixtures.getRecipes().size()
                + " learned Given recipes • "
                + targets.size()
                + " existing test candidates");

        summary.setLayoutData(
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
                new String[] {
                        "Mockito unit test templates",
                        "JPA test templates"
                });

        modeCombo.select(
                anyJpaDetected()
                        ? 1
                        : 0);

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

                        selectBestTarget();
                        refreshSnippet();
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

                        refreshSnippet();
                    }
                });

        Button open =
                new Button(
                        area,
                        SWT.PUSH);

        open.setText(
                "Open Test");

        open.addSelectionListener(
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

        Label methodList =
                new Label(
                        area,
                        SWT.WRAP);

        methodList.setText(
                "Methods: "
                + methodNames());

        methodList.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false,
                        3,
                        1));

        reuseLearnedGivenButton =
                new Button(
                        area,
                        SWT.CHECK);

        reuseLearnedGivenButton.setText(
                "Reuse learned // Given setup from the selected existing test");

        reuseLearnedGivenButton.setToolTipText(
                "Reuses setup statements mined from other test methods in the selected target test class. Only statements from that same target are inserted as executable code.");

        reuseLearnedGivenButton.setSelection(
                true);

        reuseLearnedGivenButton.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        true,
                        false,
                        3,
                        1));

        reuseLearnedGivenButton.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        refreshSnippet();
                    }
                });

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

        Composite insertRow =
                new Composite(
                        area,
                        SWT.NONE);

        GridLayout insertLayout =
                new GridLayout(
                        2,
                        false);

        insertLayout.marginWidth = 0;
        insertLayout.marginHeight = 0;

        insertRow.setLayout(
                insertLayout);

        insertRow.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        true,
                        false,
                        3,
                        1));

        Button insert =
                new Button(
                        insertRow,
                        SWT.PUSH);

        insert.setText(
                "Insert All…");

        insert.setToolTipText(
                "Insert all generated method templates into the selected existing test class in one operation.");

        insert.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        insertAll();
                    }
                });

        statusLabel =
                new Label(
                        insertRow,
                        SWT.WRAP);

        statusLabel.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        true,
                        false));

        statusLabel.setText(
                targets.isEmpty()
                        ? suggestedNewTestText()
                        : "Review dependency hints/TODO values before insertion.");

        selectBestTarget();
        refreshSnippet();

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(
            Composite parent) {

        createButton(
                parent,
                COPY_ID,
                "Copy All",
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
            copyText(
                    snippetText == null
                            ? ""
                            : snippetText.getText());

            statusLabel.setText(
                    "All generated templates copied.");
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

        TestTargetCandidate target =
                selectedTarget();

        boolean reuseLearned =
                reuseLearnedGivenButton != null
                && !reuseLearnedGivenButton
                        .isDisposed()
                && reuseLearnedGivenButton
                        .getSelection();

        snippetText.setText(
                TestHelperSnippetGenerator
                        .generateBatch(
                                analyses,
                                selectedMode(),
                                target,
                                learnedFixtures,
                                reuseLearned));

        snippetText.setSelection(
                0);

        updateLearnedStatus(
                target,
                reuseLearned);
    }

    private void updateLearnedStatus(
            TestTargetCandidate target,
            boolean reuseLearned) {

        if (statusLabel == null
                || statusLabel.isDisposed()) {

            return;
        }

        if (!reuseLearned
                || selectedMode()
                        != TestHelperSnippetGenerator.JPA_TEST) {

            statusLabel.setText(
                    targets.isEmpty()
                            ? suggestedNewTestText()
                            : "Generic scaffold; learned Given reuse is disabled/not applicable.");

            return;
        }

        int recipes =
                learnedFixtures
                        .recipeCountFor(
                                target);

        if (recipes == 0) {
            statusLabel.setText(
                    target == null
                            ? "Select an existing JPA test to reuse learned fixture setup."
                            : "No reusable Given recipe was found inside the selected test class; generic dependency hints are used.");
            return;
        }

        StringBuilder text =
                new StringBuilder();

        text.append(
                recipes)
                .append(
                        recipes == 1
                                ? " reusable Given recipe found in "
                                : " reusable Given recipes found in ")
                .append(
                        target.getType()
                                .getElementName());

        TestHelperLearnedFixtureRecipe best =
                analyses.isEmpty()
                        ? null
                        : learnedFixtures
                                .bestRecipe(
                                        analyses.get(0),
                                        target);

        if (best != null) {
            text.append(
                    " • best source: ")
                    .append(
                            best.getOriginMethodName())
                    .append(
                            "(...)");
        }

        if (learnedFixtures.isTruncated()) {
            text.append(
                    " • mining hit a safety limit");
        }

        statusLabel.setText(
                text.toString());
    }

    private int selectedMode() {
        return modeCombo != null
                && modeCombo.getSelectionIndex()
                        == 1
                                ? TestHelperSnippetGenerator.JPA_TEST
                                : TestHelperSnippetGenerator.UNIT_TEST;
    }

    private void selectBestTarget() {
        if (targetCombo == null
                || targetCombo.isDisposed()
                || targets.isEmpty()) {

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

    private void insertAll() {
        TestTargetCandidate target =
                selectedTarget();

        if (target == null) {
            MessageDialog.openInformation(
                    getShell(),
                    "Generate Tests for Selected Methods",
                    "No existing target test is selected. Create/select a test class first, then run batch generation again.");
            return;
        }

        if (!MessageDialog.openQuestion(
                getShell(),
                "Insert Generated Test Templates",
                "Insert all "
                        + analyses.size()
                        + " generated test templates into "
                        + target.getLabel()
                        + "?\n\nDuplicate generated test method names and duplicate mock fields are adjusted/skipped. Review TODO fixture values afterwards.")) {

            return;
        }

        try {
            String message =
                    TestSnippetInserter
                            .insert(
                                    target.getType(),
                                    snippetText.getText());

            statusLabel.setText(
                    message);

            JavaEditorOpener.open(
                    target.getType());

        } catch (Exception e) {
            MessageDialog.openError(
                    getShell(),
                    "Could Not Insert Test Templates",
                    e.getMessage() == null
                            ? e.toString()
                            : e.getMessage());
        }
    }

    private boolean anyJpaDetected() {
        for (TestHelperAnalysis analysis :
                analyses) {

            if (analysis.isJpaDetected()) {
                return true;
            }
        }

        return false;
    }

    private int mergedFixtureCount() {
        Map<String, TestHelperFixtureDependency> unique =
                new LinkedHashMap<String, TestHelperFixtureDependency>();

        for (TestHelperAnalysis analysis :
                analyses) {

            for (TestHelperFixtureDependency fixture :
                    analysis.getFixtureDependencies()) {

                unique.put(
                        fixture.getQualifiedType(),
                        fixture);
            }
        }

        return unique.size();
    }

    private String simpleDeclaringType() {
        return analyses.isEmpty()
                ? "Tests"
                : analyses.get(0)
                        .getSimpleDeclaringType();
    }

    private String methodNames() {
        StringBuilder out =
                new StringBuilder();

        for (int i = 0;
                i < analyses.size();
                i++) {

            if (i > 0) {
                out.append(", ");
            }

            out.append(
                    analyses.get(i)
                            .getMethodName())
                    .append("(...)");
        }

        return out.toString();
    }

    private String suggestedNewTestText() {
        List<NewTestLocationCandidate> locations =
                selectedMode()
                        == TestHelperSnippetGenerator.JPA_TEST
                                ? jpaLocations
                                : unitLocations;

        return locations.isEmpty()
                ? "No existing test found; no new test source root was resolved."
                : "No existing test found. Suggested new test: "
                        + locations.get(0)
                                .getLabel();
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
                1180,
                800);
    }
}
