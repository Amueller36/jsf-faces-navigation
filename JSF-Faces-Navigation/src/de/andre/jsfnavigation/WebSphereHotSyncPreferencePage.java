package de.andre.jsfnavigation;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public final class WebSphereHotSyncPreferencePage
        extends FieldEditorPreferencePage
        implements IWorkbenchPreferencePage {

    public WebSphereHotSyncPreferencePage() {
        super(GRID);

        setPreferenceStore(
                Activator.getDefault()
                        .getPreferenceStore());

        setDescription(
                "Hot-sync JSF web resources directly into an exploded "
                + "WebSphere development deployment. Use this only for a "
                + "local/development server.");
    }

    @Override
    public void init(IWorkbench workbench) {
        // Nothing else required.
    }

    @Override
    protected void createFieldEditors() {
        addField(
                new BooleanFieldEditor(
                        WebSphereHotSyncSettings.ENABLED,
                        "Enable WebSphere hot sync",
                        getFieldEditorParent()));

        addField(
                new BooleanFieldEditor(
                        WebSphereHotSyncSettings.AUTO_SYNC,
                        "Automatically sync supported web resources after save",
                        getFieldEditorParent()));

        DirectoryFieldEditor profile =
                new DirectoryFieldEditor(
                        WebSphereHotSyncSettings.PROFILE_PATH,
                        "WebSphere profile directory:",
                        getFieldEditorParent());

        profile.setEmptyStringAllowed(true);
        addField(profile);

        DirectoryFieldEditor deployedRoot =
                new DirectoryFieldEditor(
                        WebSphereHotSyncSettings.DEPLOYED_WEB_ROOT,
                        "Deployed web module root (recommended):",
                        getFieldEditorParent());

        deployedRoot.setEmptyStringAllowed(true);
        addField(deployedRoot);

        addField(
                new StringFieldEditor(
                        WebSphereHotSyncSettings.SOURCE_WEB_ROOT,
                        "Project-relative source web root:",
                        getFieldEditorParent()));

        addField(
                new BooleanFieldEditor(
                        WebSphereHotSyncSettings.SYNC_XHTML,
                        "Sync .xhtml / .html / .htm",
                        getFieldEditorParent()));

        addField(
                new BooleanFieldEditor(
                        WebSphereHotSyncSettings.SYNC_JS,
                        "Sync .js",
                        getFieldEditorParent()));

        addField(
                new BooleanFieldEditor(
                        WebSphereHotSyncSettings.SYNC_CSS,
                        "Sync .css",
                        getFieldEditorParent()));

        addField(
                new BooleanFieldEditor(
                        WebSphereHotSyncSettings.SYNC_PROPERTIES,
                        "Sync .properties",
                        getFieldEditorParent()));
    }
}
