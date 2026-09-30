package de.andre.jsfnavigation;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;

public final class FlowRelatedTestDiscoveryService {

    private static final long DISCOVERY_IDLE_DELAY_MS = 1200L;
    private static final int MAX_COMPLETED_FILES = 256;

    /**
     * Modification stamp of the source file for which discovery last completed.
     * Re-activating an unchanged editor therefore performs no workspace search.
     */
    private static final Map<String, Long>
            LAST_COMPLETED_STAMP =
                    new LinkedHashMap<String, Long>(64, 0.75f, true) {
                        private static final long serialVersionUID = 1L;
                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<String, Long> eldest) {
                            return size() > MAX_COMPLETED_FILES;
                        }
                    };

    private static final Set<String> IN_FLIGHT =
            new HashSet<String>();

    private FlowRelatedTestDiscoveryService() {
    }

    public static void clearRecentRequests() {
        synchronized (LAST_COMPLETED_STAMP) {
            LAST_COMPLETED_STAMP.clear();
            IN_FLIGHT.clear();
        }
    }

    /**
     * Compatibility entry point retained for callers from older code. New code
     * should think of this as discovery for a Flow file, not an editor-open
     * event: it is intentionally de-duplicated by source modification stamp.
     */
    public static void discoverForOpenedFile(
            final IFile file) {

        discoverForFlowFile(file);
    }

    public static void discoverForFlowFile(
            final IFile file) {

        if (file == null
                || !file.exists()
                || !"java".equalsIgnoreCase(
                        file.getFileExtension())) {

            return;
        }

        final String path =
                file.getFullPath()
                        .toPortableString();

        final long requestedStamp =
                file.getModificationStamp();

        synchronized (LAST_COMPLETED_STAMP) {
            Long completed =
                    LAST_COMPLETED_STAMP.get(path);

            if (completed != null
                    && sameStamp(
                            completed.longValue(),
                            requestedStamp)) {

                return;
            }

            if (!IN_FLIGHT.add(path)) {
                return;
            }
        }

        Job job =
                new Job(
                        "Find matching tests for "
                        + file.getName()) {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        boolean completed = false;

                        try {
                            FlowExplorerService service =
                                    Activator
                                            .getFlowExplorerService();

                            if (monitor.isCanceled()) {
                                return Status.CANCEL_STATUS;
                            }

                            if (service == null
                                    || !service
                                            .isAutoTestDiscovery()
                                    || !service
                                            .containsFile(file)) {

                                return Status.OK_STATUS;
                            }

                            /*
                             * Classification can consult JDT semantics. Keep it
                             * here, inside the Job, so even an explicit Add File
                             * button never pays that cost on the SWT UI thread.
                             */
                            if (FlowCategoryClassifier.TEST.equals(
                                    FlowCategoryClassifier.classify(file))) {

                                completed = true;
                                return Status.OK_STATUS;
                            }

                            ICompilationUnit unit =
                                    JavaCore
                                            .createCompilationUnitFrom(
                                                    file);

                            if (unit == null
                                    || !unit.exists()) {

                                completed = true;
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
                                                    .classify(type)
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

                                        if (monitor.isCanceled()) {
                                            return Status.CANCEL_STATUS;
                                        }

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
                                                && testFile.exists()) {

                                            boolean wasPresent =
                                                    service
                                                            .containsFile(
                                                                    testFile);

                                            service.addFile(testFile);

                                            if (!wasPresent) {
                                                added++;
                                            }
                                        }
                                    }
                                }

                                completed = !monitor.isCanceled();

                            } catch (Exception e) {
                                /*
                                 * A temporarily rebuilding JDT index should not
                                 * poison the completion cache. A later explicit
                                 * Flow add/change may retry discovery.
                                 */
                                completed = false;
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

                            return monitor.isCanceled()
                                    ? Status.CANCEL_STATUS
                                    : Status.OK_STATUS;

                        } finally {
                            synchronized (LAST_COMPLETED_STAMP) {
                                IN_FLIGHT.remove(path);

                                if (completed) {
                                    LAST_COMPLETED_STAMP.put(
                                            path,
                                            Long.valueOf(
                                                    file.exists()
                                                            ? file.getModificationStamp()
                                                            : requestedStamp));
                                }
                            }
                        }
                    }
                };

        job.setSystem(true);
        job.setPriority(Job.DECORATE);
        job.setRule(PluginBackgroundSchedulingRule.INSTANCE);
        job.schedule(DISCOVERY_IDLE_DELAY_MS);
    }

    private static boolean sameStamp(
            long left,
            long right) {

        if (left == IResource.NULL_STAMP
                || right == IResource.NULL_STAMP) {

            return left == right;
        }

        return left == right;
    }
}
