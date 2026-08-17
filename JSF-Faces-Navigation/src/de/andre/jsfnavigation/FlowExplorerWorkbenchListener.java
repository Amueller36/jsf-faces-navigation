package de.andre.jsfnavigation;

import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public final class FlowExplorerWorkbenchListener
        implements IWindowListener {

    private final FlowAutoCaptureListener partListener =
            new FlowAutoCaptureListener();

    public void start() {
        PlatformUI.getWorkbench()
                .addWindowListener(this);

        IWorkbenchWindow[] windows =
                PlatformUI.getWorkbench()
                        .getWorkbenchWindows();

        for (IWorkbenchWindow window : windows) {
            attach(window);
        }
    }

    public void stop() {
        PlatformUI.getWorkbench()
                .removeWindowListener(this);

        IWorkbenchWindow[] windows =
                PlatformUI.getWorkbench()
                        .getWorkbenchWindows();

        for (IWorkbenchWindow window : windows) {
            detach(window);
        }
    }

    @Override
    public void windowOpened(
            IWorkbenchWindow window) {

        attach(window);
    }

    @Override
    public void windowClosed(
            IWorkbenchWindow window) {

        detach(window);
    }

    @Override
    public void windowActivated(
            IWorkbenchWindow window) {
    }

    @Override
    public void windowDeactivated(
            IWorkbenchWindow window) {
    }

    private void attach(
            IWorkbenchWindow window) {

        if (window == null) {
            return;
        }

        IWorkbenchPage[] pages =
                window.getPages();

        for (IWorkbenchPage page : pages) {
            page.addPartListener(
                    partListener);
        }
    }

    private void detach(
            IWorkbenchWindow window) {

        if (window == null) {
            return;
        }

        IWorkbenchPage[] pages =
                window.getPages();

        for (IWorkbenchPage page : pages) {
            page.removePartListener(
                    partListener);
        }
    }
}
