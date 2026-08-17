package de.andre.jsfnavigation;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;

public final class AddCurrentFileToFlowHandler
        extends AbstractHandler {

    @Override
    public Object execute(
            ExecutionEvent event)
            throws ExecutionException {

        IFile file =
                EditorContext.currentFile();

        FlowExplorerService service =
                Activator.getFlowExplorerService();

        if (file != null
                && service != null) {

            service.addFile(file);
            FlowExplorerView.refreshIfOpen();

            WebSphereStatusLine.show(
                    "Added "
                    + file.getName()
                    + " to flow '"
                    + service.getCurrentFlowName()
                    + "'.");
        }

        return null;
    }
}
