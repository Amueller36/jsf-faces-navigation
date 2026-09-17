package de.andre.jsfnavigation;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
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
                        "Deployed web module root (optional override):",
                        getFieldEditorParent());

        deployedRoot.setEmptyStringAllowed(true);
        addField(deployedRoot);

        addField(
                new StringFieldEditor(
                        WebSphereHotSyncSettings.SOURCE_WEB_ROOT,
                        "Project-relative source web root:",
                        getFieldEditorParent()));

        addField(
                new StringFieldEditor(
                        WebSphereLogSettings.SERVER_NAME,
                        "WebSphere server name (for example server1):",
                        getFieldEditorParent()));

        DirectoryFieldEditor logDirectory =
                new DirectoryFieldEditor(
                        WebSphereLogSettings.LOG_DIRECTORY,
                        "Log directory override (optional):",
                        getFieldEditorParent());

        logDirectory.setEmptyStringAllowed(true);
        addField(logDirectory);

        addField(
                new BooleanFieldEditor(
                        WebSphereLogSettings.AUTO_REFRESH,
                        "Automatically refresh WebSphere log view",
                        getFieldEditorParent()));

        addField(
                new BooleanFieldEditor(
                        SmartDeploySettings.ENABLED,
                        "Enable Smart Java/Class Deploy after Eclipse builds (opt-in)",
                        getFieldEditorParent()));

        FileFieldEditor wsadmin =
                new FileFieldEditor(
                        SmartDeploySettings.WSADMIN_PATH,
                        "wsadmin executable override (optional):",
                        getFieldEditorParent());

        wsadmin.setEmptyStringAllowed(true);
        addField(wsadmin);

        addField(
                new StringFieldEditor(
                        SmartDeploySettings.WSADMIN_EXTRA_ARGS,
                        "wsadmin extra arguments (optional):",
                        getFieldEditorParent()));

        Button clearMappings =
                new Button(
                        getFieldEditorParent(),
                        SWT.PUSH);

        clearMappings.setText(
                "Forget learned Smart Deploy mappings");

        clearMappings.setToolTipText(
                "Clears remembered Java output-folder and web source-root -> deployed module mappings. "
                + "The plug-in will rediscover and ask again on the next matching build/save.");

        clearMappings.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        SmartDeployMappingStore mappings =
                                Activator.getSmartDeployMappingStore();

                        if (mappings != null) {
                            mappings.clear();

                            WebSphereStatusLine.show(
                                    "Smart Deploy mappings cleared.");
                        }
                    }
                });

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
