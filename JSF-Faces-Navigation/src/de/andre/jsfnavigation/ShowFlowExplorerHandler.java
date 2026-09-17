package de.andre.jsfnavigation;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.PartInitException;

public final class ShowFlowExplorerHandler
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

        try {
            page.showView(
                    FlowExplorerView.VIEW_ID);

        } catch (PartInitException e) {
            throw new ExecutionException(
                    "Could not open Flow Explorer.",
                    e);
        }

        return null;
    }
}
