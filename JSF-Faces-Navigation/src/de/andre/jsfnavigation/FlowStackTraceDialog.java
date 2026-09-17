package de.andre.jsfnavigation;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.JFaceResources;
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

public final class FlowStackTraceDialog
        extends Dialog {

    private final FlowTestCaseResult result;

    private StyledText traceText;

    public FlowStackTraceDialog(
            Shell parentShell,
            FlowTestCaseResult result) {

        super(parentShell);

        this.result = result;

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
                "JUnit Stack Trace — "
                + result.getSimpleClassName()
                + "."
                + result.getMethodName());
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

        area.setLayout(layout);

        Label header =
                new Label(
                        area,
                        SWT.WRAP);

        header.setText(
                result.getSimpleClassName()
                + "."
                + result.getMethodName()
                + (result.getFirstTraceLine()
                        .isEmpty()
                                ? ""
                                : "\n"
                                        + result
                                                .getFirstTraceLine()));

        header.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false));

        if (!result.getExpected()
                .isEmpty()
                || !result.getActual()
                        .isEmpty()) {

            Label comparison =
                    new Label(
                            area,
                            SWT.WRAP);

            comparison.setText(
                    "Expected: "
                    + (result.getExpected()
                            .isEmpty()
                                    ? "<not provided>"
                                    : result.getExpected())
                    + "\nActual: "
                    + (result.getActual()
                            .isEmpty()
                                    ? "<not provided>"
                                    : result.getActual()));

            comparison.setLayoutData(
                    new GridData(
                            SWT.FILL,
                            SWT.TOP,
                            true,
                            false));
        }

        Label hint =
                new Label(
                        area,
                        SWT.NONE);

        hint.setText(
                "Double-click a Java stack-trace line to open that source line.");

        traceText =
                new StyledText(
                        area,
                        SWT.BORDER
                        | SWT.READ_ONLY
                        | SWT.MULTI
                        | SWT.H_SCROLL
                        | SWT.V_SCROLL);

        traceText.setFont(
                JFaceResources
                        .getTextFont());

        traceText.setText(
                result.getStackTrace()
                        .isEmpty()
                                ? "No stack trace was reported."
                                : result.getStackTrace());

        traceText.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.FILL,
                        true,
                        true));

        traceText.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseDoubleClick(
                            MouseEvent e) {

                        openCurrentLine();
                    }
                });

        return area;
    }

    private void openCurrentLine() {
        if (traceText == null
                || traceText.isDisposed()) {

            return;
        }

        int charCount =
                traceText.getCharCount();

        if (charCount <= 0) {
            return;
        }

        int offset =
                Math.max(
                        0,
                        Math.min(
                                traceText.getCaretOffset(),
                                charCount - 1));

        int line =
                traceText.getLineAtOffset(
                        offset);

        String text =
                traceText.getLine(
                        line);

        if (!FlowStackTraceNavigator
                .open(text)) {

            WebSphereStatusLine.show(
                    "That stack-trace line does not resolve to a workspace Java source file.");
        }
    }

    @Override
    protected Point getInitialSize() {
        return new Point(
                900,
                600);
    }
}
