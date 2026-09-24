package de.andre.jsfnavigation;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public final class WebSphereLogFilterRulesDialog
        extends Dialog {

    private String rules;
    private Text rulesText;

    public WebSphereLogFilterRulesDialog(
            Shell parentShell,
            String rules) {

        super(parentShell);

        this.rules =
                rules == null
                        ? ""
                        : rules;
    }

    @Override
    protected void configureShell(
            Shell shell) {

        super.configureShell(
                shell);

        shell.setText(
                "WebSphere Log Ignore Rules");
    }

    @Override
    protected Control createDialogArea(
            Composite parent) {

        Composite area =
                (Composite)
                        super.createDialogArea(
                                parent);

        Composite content =
                new Composite(
                        area,
                        SWT.NONE);

        content.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.FILL,
                        true,
                        true));

        content.setLayout(
                new GridLayout(
                        1,
                        false));

        Label help =
                new Label(
                        content,
                        SWT.WRAP);

        help.setText(
                "One ignore rule per line. Plain text is a case-insensitive substring. "
                + "Use regex:... for a regular expression and # for comments. "
                + "Matching stack-trace continuation lines are hidden with the matching error.");

        GridData helpData =
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false);

        helpData.widthHint = 650;
        help.setLayoutData(
                helpData);

        rulesText =
                new Text(
                        content,
                        SWT.BORDER
                        | SWT.MULTI
                        | SWT.V_SCROLL
                        | SWT.H_SCROLL);

        GridData textData =
                new GridData(
                        SWT.FILL,
                        SWT.FILL,
                        true,
                        true);

        textData.widthHint = 650;
        textData.heightHint = 300;

        rulesText.setLayoutData(
                textData);

        rulesText.setText(
                rules);

        return area;
    }

    @Override
    protected void okPressed() {
        rules =
                rulesText == null
                        || rulesText.isDisposed()
                                ? ""
                                : rulesText.getText();

        super.okPressed();
    }

    public String getRules() {
        return rules;
    }
}
