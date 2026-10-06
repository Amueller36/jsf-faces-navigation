package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.swt.widgets.Display;

public final class JsfSmartHyperlink
        implements IHyperlink {

    private final IRegion region;
    private final JsfCursorReference reference;
    private final IFile currentFile;
    private final IDocument document;

    public JsfSmartHyperlink(
            IRegion region,
            JsfCursorReference reference,
            IFile currentFile,
            IDocument document) {

        this.region = region;
        this.reference = reference;
        this.currentFile = currentFile;
        this.document = document;
    }

    @Override
    public IRegion getHyperlinkRegion() {
        return region;
    }

    @Override
    public String getTypeLabel() {
        return "PrimeFaces / RichFaces / JSF";
    }

    @Override
    public String getHyperlinkText() {
        return "Open JSF target '"
                + reference.getName()
                + "'";
    }

    @Override
    public void open() {
        if (reference.getKind()
                == JsfCursorReference.COMPOSITE
                && !JsfTaglibCatalogService
                        .isWarm(
                                currentFile)) {

            resolveCustomTagInBackground();
            return;
        }

        openTargets(
                JsfNavigationSupport.resolve(
                        reference,
                        currentFile,
                        document));
    }

    private void resolveCustomTagInBackground() {
        Job job =
                new Job(
                        "Resolve JSF custom tag") {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        if (monitor.isCanceled()) {
                            return Status.CANCEL_STATUS;
                        }

                        JsfTaglibCatalogService
                                .loadForNavigation(
                                        currentFile);

                        final List<NavigationTarget> targets =
                                JsfNavigationSupport.resolve(
                                        reference,
                                        currentFile,
                                        document);

                        if (monitor.isCanceled()) {
                            return Status.CANCEL_STATUS;
                        }

                        Display display =
                                Display.getDefault();

                        if (display != null
                                && !display.isDisposed()) {

                            display.asyncExec(
                                    new Runnable() {
                                        @Override
                                        public void run() {

                                            openTargets(
                                                    targets);
                                        }
                                    });
                        }

                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.setPriority(
                Job.DECORATE);
        job.setRule(
                PluginBackgroundSchedulingRule.INSTANCE);
        job.schedule();
    }

    private void openTargets(
            List<NavigationTarget> targets) {

        NavigationTarget selected =
                MethodNavigationChooser.choose(
                        "JSF Navigation",
                        "Select target for '"
                                + reference.getName()
                                + "':",
                        targets);

        if (selected != null) {
            selected.open();
        }
    }
}
