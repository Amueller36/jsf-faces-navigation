package de.andre.jsfnavigation;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public final class PageControllerGraphDialog
        extends Dialog {

    private final String graphText;

    public PageControllerGraphDialog(
            Shell parentShell,
            String graphText) {

        super(parentShell);
        this.graphText =
                graphText == null
                        ? ""
                        : graphText;

        setShellStyle(
                getShellStyle()
                | SWT.RESIZE
                | SWT.MAX
                | SWT.MIN);
    }

    @Override
    protected void configureShell(
            Shell shell) {

        super.configureShell(shell);
        shell.setText(
                "JSF Page / Controller Graph");
    }

    @Override
    protected Control createDialogArea(
            Composite parent) {

        Composite area =
                (Composite)
                        super.createDialogArea(parent);

        area.setLayout(
                new FillLayout());

        Text text =
                new Text(
                        area,
                        SWT.MULTI
                        | SWT.READ_ONLY
                        | SWT.H_SCROLL
                        | SWT.V_SCROLL
                        | SWT.BORDER);

        text.setText(graphText);

        return area;
    }

    @Override
    protected Point getInitialSize() {
        return new Point(
                760,
                600);
    }
}
