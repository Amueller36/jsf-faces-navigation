package de.andre.jsfnavigation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

public final class FlowTransactionLensDialog
        extends Dialog {

    private static final Pattern LINE_PATTERN =
            Pattern.compile(
                    "^\\s*(\\d+)\\s+\\[");

    private final FlowTransactionLensReport report;

    private StyledText text;

    public FlowTransactionLensDialog(
            Shell parentShell,
            FlowTransactionLensReport report) {

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

        String file =
                report.getFile() == null
                        ? "test"
                        : report.getFile()
                                .getName();

        newShell.setText(
                "Transaction / Persistence Context Lens — "
                + file);
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
        layout.verticalSpacing = 6;

        area.setLayout(
                layout);

        Label explanation =
                new Label(
                        area,
                        SWT.WRAP);

        explanation.setText(
                "Static best-effort view of visible JPA transaction and persistence-context boundaries. "
                + "Container/superclass/framework-managed boundaries may exist outside the test method, so hints are guidance rather than proof.");

        explanation.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false));

        Label navigation =
                new Label(
                        area,
                        SWT.NONE);

        navigation.setText(
                "Double-click an event line to jump to that line in the test source.");

        text =
                new StyledText(
                        area,
                        SWT.BORDER
                        | SWT.READ_ONLY
                        | SWT.MULTI
                        | SWT.H_SCROLL
                        | SWT.V_SCROLL);

        text.setFont(
                JFaceResources
                        .getTextFont());

        text.setText(
                render());

        text.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.FILL,
                        true,
                        true));

        text.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseDoubleClick(
                            MouseEvent e) {

                        openCurrentEventLine();
                    }
                });

        return area;
    }

    private String render() {
        StringBuilder out =
                new StringBuilder();

        if (report.getMethods()
                .isEmpty()) {

            return "No JUnit test methods were detected in this file.";
        }

        for (FlowTransactionMethodReport method :
                report.getMethods()) {

            out.append(
                    method.getMethodLabel())
                    .append(
                            "  (line ")
                    .append(
                            method.getLine())
                    .append(
                            ")\n");

            if (method.getEvents()
                    .isEmpty()) {

                out.append(
                        "    — no explicit boundary events detected\n");

            } else {
                for (FlowTransactionEvent event :
                        method.getEvents()) {

                    out.append(
                            String.format(
                                    "%5d  [%s%s]  %s\n",
                                    Integer.valueOf(
                                            event.getLine()),
                                    event.getKind(),
                                    event.isHeuristic()
                                            ? "?"
                                            : "",
                                    event.getDetail()));
                }
            }

            for (String hint :
                    method.getHints()) {

                out.append(
                        "    HINT: ")
                        .append(hint)
                        .append('\n');
            }

            out.append('\n');
        }

        return out.toString();
    }

    private void openCurrentEventLine() {
        if (text == null
                || text.isDisposed()
                || report.getFile() == null
                || !report.getFile()
                        .exists()) {

            return;
        }

        int charCount =
                text.getCharCount();

        if (charCount <= 0) {
            return;
        }

        int offset =
                Math.max(
                        0,
                        Math.min(
                                text.getCaretOffset(),
                                charCount - 1));

        String lineText =
                text.getLine(
                        text.getLineAtOffset(
                                offset));

        Matcher matcher =
                LINE_PATTERN.matcher(
                        lineText);

        if (!matcher.find()) {
            return;
        }

        try {
            int oneBasedLine =
                    Integer.parseInt(
                            matcher.group(1));

            IWorkbenchPage page =
                    PlatformUI.getWorkbench()
                            .getActiveWorkbenchWindow()
                            .getActivePage();

            IEditorPart editor =
                    IDE.openEditor(
                            page,
                            report.getFile(),
                            true);

            if (!(editor
                    instanceof ITextEditor)) {

                return;
            }

            ITextEditor textEditor =
                    (ITextEditor)
                            editor;

            IDocument document =
                    textEditor
                            .getDocumentProvider()
                            .getDocument(
                                    textEditor
                                            .getEditorInput());

            if (document == null
                    || document.getNumberOfLines()
                            <= 0) {

                return;
            }

            int zeroBasedLine =
                    Math.max(
                            0,
                            Math.min(
                                    oneBasedLine - 1,
                                    document.getNumberOfLines()
                                            - 1));

            textEditor.selectAndReveal(
                    document.getLineOffset(
                            zeroBasedLine),
                    0);

        } catch (Exception e) {
            WebSphereStatusLine.show(
                    "Could not open transaction-lens source line: "
                    + e.getMessage());
        }
    }

    @Override
    protected Point getInitialSize() {
        return new Point(
                980,
                650);
    }
}
