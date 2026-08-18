package de.andre.jsfnavigation;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

public final class JsfComponentHelpDialog
        extends Dialog {

    private final JsfComponentHelp help;

    public JsfComponentHelpDialog(
            Shell parentShell,
            JsfComponentHelp help) {

        super(parentShell);
        this.help = help;

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
                help == null
                        ? "JSF Component Help"
                        : "JSF Component Help — "
                                + help.getTitle());
    }

    @Override
    protected Control createDialogArea(
            Composite parent) {

        Composite area =
                (Composite)
                        super.createDialogArea(
                                parent);

        area.setLayout(
                new GridLayout(
                        1,
                        false));

        StyledText text =
                new StyledText(
                        area,
                        SWT.MULTI
                        | SWT.READ_ONLY
                        | SWT.WRAP
                        | SWT.V_SCROLL
                        | SWT.BORDER);

        text.setMargins(
                10,
                8,
                10,
                8);

        text.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.FILL,
                        true,
                        true));

        text.setText(
                help == null
                        ? "No help information is available."
                        : help.getText());

        return area;
    }

    @Override
    protected Point getInitialSize() {
        return new Point(
                760,
                560);
    }
}
