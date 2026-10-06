package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

public final class JavaJsfStringHyperlink
        implements IHyperlink {

    private final IRegion region;
    private final JavaStringReference reference;
    private final IFile file;

    public JavaJsfStringHyperlink(
            IRegion region,
            JavaStringReference reference,
            IFile file) {

        this.region = region;
        this.reference = reference;
        this.file = file;
    }

    @Override
    public IRegion getHyperlinkRegion() {
        return region;
    }

    @Override
    public String getTypeLabel() {
        return "JSF / PrimeFaces / JPA";
    }

    @Override
    public String getHyperlinkText() {
        return "Open target for '"
                + reference.getValue()
                + "'";
    }

    @Override
    public void open() {
        Job job =
                new Job("Resolve JSF/JPA string reference") {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        List<NavigationTarget> targets =
                                JavaJsfStringNavigationService
                                        .resolve(
                                                reference,
                                                file);

                        NavigationTarget selected =
                                MethodNavigationChooser.choose(
                                        "Navigate String Reference",
                                        "Select a target for '"
                                                + reference.getValue()
                                                + "':",
                                        targets);

                        if (selected != null) {
                            selected.open();
                        }

                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.schedule();
    }
}
