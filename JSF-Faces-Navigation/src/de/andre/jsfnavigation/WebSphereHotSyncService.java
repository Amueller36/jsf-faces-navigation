package de.andre.jsfnavigation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;

public final class WebSphereHotSyncService {

    private final Map<String, Boolean> pending =
            new ConcurrentHashMap<String, Boolean>();

    private final AtomicBoolean scheduled =
            new AtomicBoolean(false);

    private IResourceChangeListener listener;

    public void start() {
        listener =
                new IResourceChangeListener() {
                    @Override
                    public void resourceChanged(
                            IResourceChangeEvent event) {

                        collect(event.getDelta());
                    }
                };

        ResourcesPlugin.getWorkspace()
                .addResourceChangeListener(
                        listener,
                        IResourceChangeEvent.POST_CHANGE);
    }

    public void stop() {
        if (listener != null) {
            ResourcesPlugin.getWorkspace()
                    .removeResourceChangeListener(listener);

            listener = null;
        }

        pending.clear();
    }

    public File syncNow(IFile source)
            throws IOException {

        if (source == null
                || !source.exists()) {

            throw new IOException(
                    "The source file does not exist.");
        }

        if (!WebSphereHotSyncPaths.isSyncable(source)) {
            throw new IOException(
                    "This file type is not enabled for WebSphere hot sync.");
        }

        File target =
                WebSphereHotSyncPaths.resolveTarget(source);

        if (target == null) {
            throw new IOException(
                    "No deployed WebSphere web-module root is configured. "
                    + "Open Preferences > JSF / Java Navigation > WebSphere Hot Sync.");
        }

        File parent =
                target.getParentFile();

        if (parent != null
                && !parent.exists()
                && !parent.mkdirs()
                && !parent.isDirectory()) {

            throw new IOException(
                    "Could not create target directory: "
                    + parent.getAbsolutePath());
        }

        if (source.getLocation() == null) {
            throw new IOException(
                    "The source resource does not have a local filesystem path.");
        }

        Files.copy(
                source.getLocation()
                        .toFile()
                        .toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);

        return target;
    }

    private void collect(IResourceDelta delta) {
        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        if (delta == null
                || store == null
                || !store.getBoolean(
                        WebSphereHotSyncSettings.ENABLED)
                || !store.getBoolean(
                        WebSphereHotSyncSettings.AUTO_SYNC)) {

            return;
        }

        try {
            delta.accept(
                    new IResourceDeltaVisitor() {
                        @Override
                        public boolean visit(
                                IResourceDelta child)
                                throws CoreException {

                            IResource resource =
                                    child.getResource();

                            if (resource.getType()
                                    != IResource.FILE
                                    || child.getKind()
                                            == IResourceDelta.REMOVED) {

                                return true;
                            }

                            int flags =
                                    child.getFlags();

                            boolean contentChange =
                                    child.getKind()
                                            == IResourceDelta.ADDED
                                    || (flags
                                            & IResourceDelta.CONTENT) != 0
                                    || (flags
                                            & IResourceDelta.REPLACED) != 0;

                            if (!contentChange
                                    || !(resource instanceof IFile)) {

                                return false;
                            }

                            IFile file =
                                    (IFile) resource;

                            if (WebSphereHotSyncPaths.isSyncable(file)
                                    && WebSphereHotSyncPaths.relativeWebPath(file)
                                            != null) {

                                pending.put(
                                        file.getFullPath()
                                                .toPortableString(),
                                        Boolean.TRUE);
                            }

                            return false;
                        }
                    });

        } catch (CoreException e) {
            return;
        }

        schedule();
    }

    private void schedule() {
        if (pending.isEmpty()
                || !scheduled.compareAndSet(
                        false,
                        true)) {

            return;
        }

        Job job =
                new Job("WebSphere hot sync") {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        try {
                            processPending();
                        } finally {
                            scheduled.set(false);

                            if (!pending.isEmpty()) {
                                schedule();
                            }
                        }

                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.schedule(200L);
    }

    private void processPending() {
        for (String path :
                pending.keySet()) {

            if (pending.remove(path) == null) {
                continue;
            }

            IFile file =
                    ResourcesPlugin.getWorkspace()
                            .getRoot()
                            .getFile(new Path(path));

            if (!file.exists()) {
                continue;
            }

            try {
                File target = syncNow(file);

                WebSphereStatusLine.show(
                        "WebSphere hot sync: "
                        + file.getName()
                        + " -> "
                        + target.getAbsolutePath());

            } catch (IOException e) {
                /*
                 * Auto-sync is deliberately non-disruptive. The manual command
                 * surfaces configuration/errors explicitly.
                 */
                WebSphereStatusLine.show(
                        "WebSphere hot sync skipped: "
                        + e.getMessage());
            }
        }
    }
}
