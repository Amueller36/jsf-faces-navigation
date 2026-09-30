package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

public final class MethodNavigationChooser {

    private MethodNavigationChooser() {
    }

    public static NavigationTarget choose(
            final String title,
            final String message,
            final List<NavigationTarget> choices) {

        if (choices == null || choices.isEmpty()) {
            return null;
        }

        if (choices.size() == 1) {
            return choices.get(0);
        }

        final NavigationTarget[] selected =
                new NavigationTarget[1];

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

                                                        return ((NavigationTarget) element)
                                                                .getLabel();
                                                    }
                                                });

                                dialog.setTitle(title);
                                dialog.setMessage(message);
                                dialog.setElements(
                                        choices.toArray());

                                if (dialog.open() == Window.OK) {
                                    selected[0] =
                                            (NavigationTarget)
                                                    dialog.getFirstResult();
                                }
                            }
                        });

        return selected[0];
    }
}
