package de.andre.jsfnavigation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

public final class FlowTestImpactService {

    private static final int MAX_CALLER_DEPTH = 5;
    private static final int MAX_VISITED_METHODS = 300;
    private static final long DEBOUNCE_MS = 800L;

    private final Object lock =
            new Object();

    private final Map<String, IMethod> touched =
            new LinkedHashMap<String, IMethod>();

    private final Map<String, IMethod> pending =
            new LinkedHashMap<String, IMethod>();

    private Job discoveryJob;

    private IElementChangedListener javaListener;
    private IResourceChangeListener resourceListener;

    public void start() {
        javaListener =
                new IElementChangedListener() {
                    @Override
                    public void elementChanged(
                            ElementChangedEvent event) {

                        FlowExplorerService flow =
                                Activator.getFlowExplorerService();

                        if (flow == null
                                || !flow.isAutoTestDiscovery()) {

                            return;
                        }

                        collectChangedMethods(
                                event.getDelta());
                    }
                };

        JavaCore.addElementChangedListener(
                javaListener,
                ElementChangedEvent.POST_CHANGE);

        /*
         * JDT deltas are normally enough, but some editors/build setups only
         * surface the compilation-unit content change. On a saved active Java
         * file, use the caret method as a precise fallback.
         */
        resourceListener =
                new IResourceChangeListener() {
                    @Override
                    public void resourceChanged(
                            IResourceChangeEvent event) {

                        FlowExplorerService flow =
                                Activator.getFlowExplorerService();

                        if (flow == null
                                || !flow.isAutoTestDiscovery()
                                || event.getDelta() == null) {

                            return;
                        }

                        final Set<IFile> changed =
                                new LinkedHashSet<IFile>();

                        try {
                            event.getDelta()
                                    .accept(
                                            new IResourceDeltaVisitor() {
                                                @Override
                                                public boolean visit(
                                                        IResourceDelta delta)
                                                        throws CoreException {

                                                    IResource resource =
                                                            delta.getResource();

                                                    if (resource
                                                            instanceof IFile
                                                            && (delta.getFlags()
                                                                    & IResourceDelta.CONTENT) != 0
                                                            && "java".equalsIgnoreCase(
                                                                    resource.getFileExtension())) {

                                                        changed.add(
                                                                (IFile)
                                                                        resource);
                                                    }

                                                    return true;
                                                }
                                            });

                        } catch (CoreException e) {
                            return;
                        }

                        if (changed.isEmpty()) {
                            return;
                        }

                        enqueueTouchedForFiles(
                                changed);

                        if (!PlatformUI.isWorkbenchRunning()) {
                            return;
                        }

                        Display display =
                                PlatformUI.getWorkbench()
                                        .getDisplay();

                        if (display == null
                                || display.isDisposed()) {

                            return;
                        }

                        display.asyncExec(
                                new Runnable() {
                                    @Override
                                    public void run() {

                                        IFile active =
                                                EditorContext.currentFile();

                                        if (active == null
                                                || !changed.contains(
                                                        active)) {

                                            return;
                                        }

                                        IMethod method =
                                                MethodContext.currentMethod();

                                        if (method != null) {
                                            enqueue(method);
                                        }
                                    }
                                });
                    }
                };

        ResourcesPlugin.getWorkspace()
                .addResourceChangeListener(
                        resourceListener,
                        IResourceChangeEvent.POST_CHANGE);
    }

    public void stop() {
        if (javaListener != null) {
            JavaCore.removeElementChangedListener(
                    javaListener);
            javaListener = null;
        }

        if (resourceListener != null) {
            ResourcesPlugin.getWorkspace()
                    .removeResourceChangeListener(
                            resourceListener);
            resourceListener = null;
        }

        synchronized (lock) {
            if (discoveryJob != null) {
                discoveryJob.cancel();
                discoveryJob = null;
            }

            touched.clear();
            pending.clear();
        }
    }

    private void collectChangedMethods(
            IJavaElementDelta delta) {

        if (delta == null) {
            return;
        }

        IJavaElement element =
                delta.getElement();

        if (element instanceof IMethod
                && delta.getKind()
                        == IJavaElementDelta.CHANGED) {

            rememberTouched(
                    (IMethod) element);
        }

        for (IJavaElementDelta child :
                delta.getAffectedChildren()) {

            collectChangedMethods(child);
        }
    }


    private void rememberTouched(
            IMethod method) {

        if (method == null
                || !method.exists()
                || !(method.getResource()
                        instanceof IFile)) {

            return;
        }

        synchronized (lock) {
            touched.put(
                    method.getHandleIdentifier(),
                    method);
        }
    }

    private void enqueueTouchedForFiles(
            Set<IFile> changedFiles) {

        Map<String, IMethod> matches =
                new LinkedHashMap<String, IMethod>();

        synchronized (lock) {
            java.util.Iterator<Map.Entry<String, IMethod>>
                    iterator =
                            touched.entrySet()
                                    .iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, IMethod> entry =
                        iterator.next();

                IMethod method =
                        entry.getValue();

                IResource resource =
                        method == null
                                ? null
                                : method.getResource();

                if (resource instanceof IFile
                        && changedFiles.contains(
                                resource)) {

                    matches.put(
                            entry.getKey(),
                            method);
                    iterator.remove();
                }
            }
        }

        for (IMethod method :
                matches.values()) {

            enqueue(method);
        }
    }

    private void enqueue(
            IMethod method) {

        if (method == null
                || !method.exists()
                || !(method.getResource()
                        instanceof IFile)) {

            return;
        }

        synchronized (lock) {
            pending.put(
                    method.getHandleIdentifier(),
                    method);

            if (discoveryJob != null) {
                discoveryJob.cancel();
            }

            discoveryJob =
                    new Job(
                            "Find impacted JUnit tests") {

                        @Override
                        protected IStatus run(
                                IProgressMonitor monitor) {

                            Map<String, IMethod> batch;

                            synchronized (lock) {
                                batch =
                                        new LinkedHashMap<String, IMethod>(
                                                pending);
                                pending.clear();
                                discoveryJob = null;
                            }

                            if (batch.isEmpty()) {
                                return Status.OK_STATUS;
                            }

                            FlowExplorerService flow =
                                    Activator.getFlowExplorerService();

                            if (flow == null
                                    || !flow.isAutoTestDiscovery()) {

                                return Status.OK_STATUS;
                            }

                            Set<String> newlyAdded =
                                    new LinkedHashSet<String>();

                            int relationships = 0;

                            for (IMethod changed :
                                    batch.values()) {

                                if (monitor.isCanceled()) {
                                    break;
                                }

                                Map<IFile, Integer> tests =
                                        findCallingTests(
                                                changed,
                                                monitor);

                                for (Map.Entry<IFile, Integer> test :
                                        tests.entrySet()) {

                                    if (monitor.isCanceled()) {
                                        break;
                                    }

                                    boolean existed =
                                            flow.containsFile(
                                                    test.getKey());

                                    flow.addImpactedTest(
                                            test.getKey(),
                                            changed,
                                            test.getValue()
                                                    .intValue());

                                    relationships++;

                                    if (!existed) {
                                        newlyAdded.add(
                                                test.getKey()
                                                        .getFullPath()
                                                        .toPortableString());
                                    }
                                }
                            }

                            int added =
                                    newlyAdded.size();

                            if (relationships > 0) {
                                FlowExplorerView.refreshIfOpen();

                                if (added > 0) {
                                    WebSphereStatusLine.show(
                                            added == 1
                                                    ? "Added 1 impacted test and grouped it by changed method."
                                                    : "Added "
                                                            + added
                                                            + " impacted tests and grouped them by changed method.");
                                } else {
                                    WebSphereStatusLine.show(
                                            "Updated impacted-test groups for the changed methods.");
                                }
                            }

                            return Status.OK_STATUS;
                        }
                    };

            discoveryJob.setSystem(true);
            discoveryJob.schedule(
                    DEBOUNCE_MS);
        }
    }


    private static Map<IFile, Integer> findCallingTests(
            IMethod changed,
            IProgressMonitor monitor) {

        Map<IFile, Integer> result =
                new LinkedHashMap<IFile, Integer>();

        Set<String> visited =
                new HashSet<String>();

        ArrayDeque<MethodDepth> queue =
                new ArrayDeque<MethodDepth>();

        queue.add(
                new MethodDepth(
                        changed,
                        0));

        while (!queue.isEmpty()
                && visited.size()
                        < MAX_VISITED_METHODS) {

            if (monitor.isCanceled()) {
                break;
            }

            MethodDepth current =
                    queue.removeFirst();

            IMethod method =
                    current.method;

            if (method == null
                    || !method.exists()
                    || !visited.add(
                            method.getHandleIdentifier())) {

                continue;
            }

            List<NavigationTarget> callers =
                    CallerSearch.findDirectCallers(
                            method);

            for (NavigationTarget target :
                    callers) {

                if (!(target
                        instanceof JavaNavigationTarget)) {

                    continue;
                }

                IMethod caller =
                        ((JavaNavigationTarget)
                                target)
                                .getMethod();

                if (caller == null
                        || !caller.exists()
                        || !(caller.getResource()
                                instanceof IFile)) {

                    continue;
                }

                if (FlowTestClassifier
                        .isJUnitTestMethodOrType(
                                caller)) {

                    IFile testFile =
                            (IFile)
                                    caller.getResource();

                    int depth =
                            current.depth + 1;

                    Integer oldDepth =
                            result.get(testFile);

                    if (oldDepth == null
                            || depth
                                    < oldDepth.intValue()) {

                        result.put(
                                testFile,
                                Integer.valueOf(
                                        depth));
                    }

                    continue;
                }

                if (current.depth
                        < MAX_CALLER_DEPTH) {

                    queue.addLast(
                            new MethodDepth(
                                    caller,
                                    current.depth + 1));
                }
            }
        }

        return result;
    }

    private static final class MethodDepth {

        final IMethod method;
        final int depth;

        MethodDepth(
                IMethod method,
                int depth) {

            this.method = method;
            this.depth = depth;
        }
    }
}
