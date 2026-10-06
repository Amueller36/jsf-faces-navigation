package de.andre.jsfnavigation;

import java.io.File;
import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

public final class WebSphereDeploymentChooser {

    private WebSphereDeploymentChooser() {
    }

    public static File ensureConfiguredRoot() {
        File configured =
                WebSphereHotSyncPaths.configuredDeployedWebRoot();

        if (configured != null) {
            return configured;
        }

        final List<File> candidates =
                WebSphereHotSyncPaths.discoverDeployedWebRoots();

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

                                                        return ((File) element)
                                                                .getAbsolutePath();
                                                    }
                                                });

                                dialog.setTitle(
                                        "Select WebSphere Deployment");

                                dialog.setMessage(
                                        "Select the exploded web-module root to use for hot sync:");

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

    private static void persist(File root) {
        if (root == null) {
            return;
        }

        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        if (store != null) {
            store.setValue(
                    WebSphereHotSyncSettings.DEPLOYED_WEB_ROOT,
                    root.getAbsolutePath());

            store.setValue(
                    WebSphereHotSyncSettings.ENABLED,
                    true);
        }
    }
}
