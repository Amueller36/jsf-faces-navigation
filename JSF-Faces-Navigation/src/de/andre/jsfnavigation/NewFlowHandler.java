package de.andre.jsfnavigation;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

public final class NewFlowHandler
        extends AbstractHandler {

    @Override
    public Object execute(
            ExecutionEvent event)
            throws ExecutionException {

        Shell shell =
                PlatformUI.getWorkbench()
                        .getActiveWorkbenchWindow()
                        .getShell();

        InputDialog dialog =
                new InputDialog(
                        shell,
                        "New Development Flow",
                        "Flow name:",
                        "",
                        null);

        if (dialog.open()
                != Window.OK) {

            return null;
        }

        FlowExplorerService service =
                Activator.getFlowExplorerService();

        if (service != null) {
            service.createFlow(
                    dialog.getValue());

            FlowExplorerView.refreshIfOpen();
        }

        return null;
    }
}
