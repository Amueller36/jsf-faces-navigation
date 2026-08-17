package de.andre.jsfnavigation;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public final class JsfNavigationPreferencePage
        extends PreferencePage
        implements IWorkbenchPreferencePage {

    public JsfNavigationPreferencePage() {
        setDescription(
                "Settings for JSF / Java Navigation development helpers.");
        noDefaultAndApplyButton();
    }

    @Override
    public void init(IWorkbench workbench) {
        // No global fields on the category page.
    }

    @Override
    protected Control createContents(
            Composite parent) {

        Label label =
                new Label(
                        parent,
                        SWT.WRAP);

        label.setText(
                "Select a sub-page such as WebSphere Hot Sync.");

        label.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false));

        return label;
    }
}
