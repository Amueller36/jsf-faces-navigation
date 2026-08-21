package de.andre.jsfnavigation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;

public final class FlowRelatedTestDiscoveryService {

    private static final long DUPLICATE_WINDOW_MS = 1200L;

    private static final Map<String, Long>
            LAST_REQUEST =
                    new HashMap<String, Long>();

    private FlowRelatedTestDiscoveryService() {
    }

    public static void discoverForOpenedFile(
            final IFile file) {

        if (file == null
                || !file.exists()
                || !"java".equalsIgnoreCase(
                        file.getFileExtension())
                || FlowCategoryClassifier.TEST
                        .equals(
                                FlowCategoryClassifier
                                        .classify(
                                                file))) {

            return;
        }

        final String path =
                file.getFullPath()
                        .toPortableString();

        long now =
                System.currentTimeMillis();

        synchronized (LAST_REQUEST) {
            Long previous =
                    LAST_REQUEST.get(
                            path);

            if (previous != null
                    && now
                            - previous.longValue()
                            < DUPLICATE_WINDOW_MS) {

                return;
            }

            LAST_REQUEST.put(
                    path,
                    Long.valueOf(
                            now));
        }

        Job job =
                new Job(
                        "Find matching tests for "
                        + file.getName()) {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        FlowExplorerService service =
                                Activator
                                        .getFlowExplorerService();

                        if (service == null
                                || !service
                                        .isAutoTestDiscovery()
                                || !service
                                        .containsFile(
                                                file)) {

                            return Status.OK_STATUS;
                        }

                        ICompilationUnit unit =
                                JavaCore
                                        .createCompilationUnitFrom(
                                                file);

                        if (unit == null
                                || !unit.exists()) {

                            return Status.OK_STATUS;
                        }

                        int added = 0;

                        try {
                            for (IType type :
                                    unit.getAllTypes()) {

                                if (monitor.isCanceled()) {
                                    return Status.CANCEL_STATUS;
                                }

                                if (type.getDeclaringType()
                                        != null
                                        || FlowTestClassifier
                                                .classify(
                                                        type)
                                                != FlowTestClassifier
                                                        .NOT_TEST) {

                                    continue;
                                }

                                List<TestTargetCandidate> tests =
                                        TestTargetFinder
                                                .find(
                                                        type,
                                                        monitor);

                                for (TestTargetCandidate candidate :
                                        tests) {

                                    IFile testFile =
                                            candidate
                                                    .getType()
                                                    .getResource()
                                                    instanceof IFile
                                                            ? (IFile)
                                                                    candidate
                                                                            .getType()
                                                                            .getResource()
                                                            : null;

                                    if (testFile != null
                                            && testFile
                                                    .exists()) {

                                        boolean wasPresent =
                                                service
                                                        .containsFile(
                                                                testFile);

                                        service.addFile(
                                                testFile);

                                        if (!wasPresent) {
                                            added++;
                                        }
                                    }
                                }
                            }

                        } catch (Exception e) {
                            return Status.OK_STATUS;
                        }

                        if (added > 0) {
                            FlowExplorerView
                                    .refreshIfOpen();

                            WebSphereStatusLine
                                    .show(
                                            "Added "
                                            + added
                                            + (added == 1
                                                    ? " matching test"
                                                    : " matching tests")
                                            + " to the current Flow.");
                        }

                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(
                true);

        job.schedule(
                300L);
    }
}
