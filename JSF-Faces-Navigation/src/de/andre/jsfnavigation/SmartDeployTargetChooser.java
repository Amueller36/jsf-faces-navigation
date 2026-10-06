package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.dialogs.ListSelectionDialog;

public final class SmartDeployTargetChooser {

    private SmartDeployTargetChooser() {
    }

    public static SmartDeployTarget choose(
            String relativeClass,
            List<SmartDeployTarget> targets) {

        return chooseInternal(
                relativeClass,
                targets,
                "Multiple deployed modules contain "
                        + relativeClass
                        + ". Select the deployment target for this Eclipse output folder:");
    }

    public static List<SmartDeployTarget> chooseWebResources(
            final String relativeWebPath,
            final List<SmartDeployTarget> targets) {

        if (targets == null
                || targets.isEmpty()) {

            return Collections.emptyList();
        }

        if (targets.size() == 1) {
            return Collections.singletonList(
                    targets.get(0));
        }

        final List<SmartDeployTarget> selected =
                new ArrayList<SmartDeployTarget>();

        PlatformUI.getWorkbench()
                .getDisplay()
                .syncExec(
                        new Runnable() {
                            @Override
                            public void run() {
                                IWorkbenchWindow window =
                                        PlatformUI.getWorkbench()
                                                .getActiveWorkbenchWindow();

                                if (window == null) {
                                    return;
                                }

                                Shell shell =
                                        window.getShell();

                                ListSelectionDialog dialog =
                                        new ListSelectionDialog(
                                                shell,
                                                targets,
                                                ArrayContentProvider
                                                        .getInstance(),
                                                new LabelProvider() {
                                                    @Override
                                                    public String getText(
                                                            Object element) {

                                                        return ((SmartDeployTarget)
                                                                element)
                                                                .displayName();
                                                    }
                                                },
                                                "Multiple deployed WARs contain "
                                                        + relativeWebPath
                                                        + ". Select every WAR that should receive this shared web resource. "
                                                        + "The selection will be remembered:");

                                dialog.setTitle(
                                        "Smart WebSphere Deploy");

                                dialog.setInitialElementSelections(
                                        targets);

                                if (dialog.open()
                                        == Window.OK) {

                                    Object[] result =
                                            dialog.getResult();

                                    if (result != null) {
                                        for (Object element :
                                                result) {

                                            if (element
                                                    instanceof SmartDeployTarget) {

                                                selected.add(
                                                        (SmartDeployTarget)
                                                                element);
                                            }
                                        }
                                    }
                                }
                            }
                        });

        return selected;
    }

    public static SmartDeployTarget chooseWebResource(
            String relativeWebPath,
            List<SmartDeployTarget> targets) {

        List<SmartDeployTarget> selected =
                chooseWebResources(
                        relativeWebPath,
                        targets);

        return selected.isEmpty()
                ? null
                : selected.get(0);
    }

    private static SmartDeployTarget chooseInternal(
            final String subject,
            final List<SmartDeployTarget> targets,
            final String message) {

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
                                IWorkbenchWindow window =
                                        PlatformUI.getWorkbench()
                                                .getActiveWorkbenchWindow();

                                if (window == null) {
                                    return;
                                }

                                Shell shell =
                                        window.getShell();

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

                                dialog.setMessage(message);
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
