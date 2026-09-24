package de.andre.jsfnavigation;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public final class JsfNavigationPreferencePage
        extends FieldEditorPreferencePage
        implements IWorkbenchPreferencePage {

    public JsfNavigationPreferencePage() {
        super(GRID);

        setPreferenceStore(
                Activator.getDefault()
                        .getPreferenceStore());

        setDescription(
                "Settings for JSF / Java Navigation development helpers.");
    }

    @Override
    public void init(IWorkbench workbench) {
        // Nothing else required.
    }

    @Override
    protected void createFieldEditors() {
        addField(
                new BooleanFieldEditor(
                        JavaTestRunSettings.SHOW_GUTTER,
                        "Show JUnit run/debug play buttons next to test cases",
                        getFieldEditorParent()));
    }
}
