package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;

public final class FlowAutoCaptureListener
        implements IPartListener2 {

    @Override
    public void partActivated(
            IWorkbenchPartReference partRef) {

        capture(partRef);
    }

    @Override
    public void partOpened(
            IWorkbenchPartReference partRef) {

        capture(partRef);
    }

    private void capture(
            IWorkbenchPartReference partRef) {

        FlowExplorerService service =
                Activator.getFlowExplorerService();

        if (service == null
                || !service.isAutoCapture()) {

            return;
        }

        if (!(partRef.getPart(false)
                instanceof IEditorPart)) {

            return;
        }

        IEditorPart editor =
                (IEditorPart)
                        partRef.getPart(false);

        IEditorInput input =
                editor.getEditorInput();

        if (!(input instanceof IFileEditorInput)) {
            return;
        }

        IFile file =
                ((IFileEditorInput) input)
                        .getFile();

        if (file == null
                || !file.exists()) {

            return;
        }

        service.addFile(file);
        FlowExplorerView.refreshIfOpen();
    }

    @Override
    public void partBroughtToTop(
            IWorkbenchPartReference partRef) {
    }

    @Override
    public void partClosed(
            IWorkbenchPartReference partRef) {
    }

    @Override
    public void partDeactivated(
            IWorkbenchPartReference partRef) {
    }

    @Override
    public void partHidden(
            IWorkbenchPartReference partRef) {
    }

    @Override
    public void partInputChanged(
            IWorkbenchPartReference partRef) {
    }

    @Override
    public void partVisible(
            IWorkbenchPartReference partRef) {
    }
}
