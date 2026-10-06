package de.andre.jsfnavigation;

import java.io.File;
import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

public final class WebSphereLogDirectoryChooser {

    private WebSphereLogDirectoryChooser() {
    }

    public static File ensureConfigured() {
        File resolved =
                WebSphereLogPaths.resolveLogDirectory();

        if (resolved != null) {
            return resolved;
        }

        final List<File> candidates =
                WebSphereLogPaths
                        .discoverServerLogDirectories();

        if (candidates.isEmpty()) {
            return null;
        }

        if (candidates.size() == 1) {
            persist(candidates.get(0));
            return candidates.get(0);
        }

        final File[] selected =
                new File[1];

        PlatformUI.getWorkbench()
                .getDisplay()
                .syncExec(
                        new Runnable() {
                            @Override
                            public void run() {
                                Shell shell =
                                        PlatformUI.getWorkbench()
                                                .getActiveWorkbenchWindow()
                                                .getShell();

                                ElementListSelectionDialog dialog =
                                        new ElementListSelectionDialog(
                                                shell,
                                                new LabelProvider() {
                                                    @Override
                                                    public String getText(
                                                            Object element) {

                                                        File file =
                                                                (File) element;

                                                        return file.getName()
                                                                + " — "
                                                                + file.getAbsolutePath();
                                                    }
                                                });

                                dialog.setTitle(
                                        "Select WebSphere Server Logs");

                                dialog.setMessage(
                                        "Select the server log directory:");

                                dialog.setElements(
                                        candidates.toArray());

                                if (dialog.open()
                                        == Window.OK) {

                                    selected[0] =
                                            (File)
                                                    dialog.getFirstResult();
                                }
                            }
                        });

        if (selected[0] != null) {
            persist(selected[0]);
        }

        return selected[0];
    }

    private static void persist(File directory) {
        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        if (store == null
                || directory == null) {

            return;
        }

        store.setValue(
                WebSphereLogSettings.LOG_DIRECTORY,
                directory.getAbsolutePath());

        store.setValue(
                WebSphereLogSettings.SERVER_NAME,
                directory.getName());
    }
}
