package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

public final class SmartDeployTargetChooser {

    private SmartDeployTargetChooser() {
    }

    public static SmartDeployTarget choose(
            final String relativeClass,
            final List<SmartDeployTarget> targets) {

        if (targets == null
                || targets.isEmpty()) {

            return null;
        }

        if (targets.size() == 1) {
            return targets.get(0);
        }

        final SmartDeployTarget[] selected =
                new SmartDeployTarget[1];

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

                                                        return ((SmartDeployTarget)
                                                                element)
                                                                .displayName();
                                                    }
                                                });

                                dialog.setTitle(
                                        "Smart WebSphere Deploy");

                                dialog.setMessage(
                                        "Multiple deployed modules contain "
                                                + relativeClass
                                                + ". Select the module for this Eclipse output folder:");

                                dialog.setElements(
                                        targets.toArray());

                                if (dialog.open()
                                        == Window.OK) {

                                    selected[0] =
                                            (SmartDeployTarget)
                                                    dialog.getFirstResult();
                                }
                            }
                        });

        return selected[0];
    }
}
