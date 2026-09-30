package de.andre.jsfnavigation;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.PartInitException;

public final class ToggleWebSphereLogsHandler
        extends AbstractHandler {

    @Override
    public Object execute(
            ExecutionEvent event)
            throws ExecutionException {

        IWorkbenchWindow window =
                PlatformUI.getWorkbench()
                        .getActiveWorkbenchWindow();

        IWorkbenchPage page =
                window == null
                        ? null
                        : window.getActivePage();

        if (page == null) {
            return null;
        }

        IViewPart existing =
                page.findView(
                        WebSphereLogsView.VIEW_ID);

        if (existing != null
                && page.isPartVisible(existing)) {

            page.hideView(existing);
            return null;
        }

        if (WebSphereLogDirectoryChooser
                .ensureConfigured() == null) {

            MessageDialog.openError(
                    window.getShell(),
                    "WebSphere Logs",
                    "No WebSphere log directory could be resolved.\n\n"
                    + "Configure your custom profile path under:\n"
                    + "Window -> Preferences -> JSF / Java Navigation -> WebSphere Hot Sync\n\n"
                    + "Then set either the server name (for example server1) "
                    + "or an explicit log directory override.");

            return null;
        }

        try {
            page.showView(
                    WebSphereLogsView.VIEW_ID);

        } catch (PartInitException e) {
            throw new ExecutionException(
                    "Could not open WebSphere Logs view.",
                    e);
        }

        return null;
    }
}
