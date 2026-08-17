package de.andre.jsfnavigation;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public final class BeanIndexService {

    private static final int MAGIC = 0x4A534645; // "JSFE"
    private static final int FORMAT_VERSION = 1;

    private final File indexFile;

    private final Object lock = new Object();

    private final Map<String, List<BeanDescriptor>> byBeanName =
            new HashMap<String, List<BeanDescriptor>>();

    private final Map<String, List<BeanDescriptor>> byResourcePath =
            new HashMap<String, List<BeanDescriptor>>();

    private final Map<String, Integer> pendingJavaChanges =
            new ConcurrentHashMap<String, Integer>();

    private final AtomicBoolean updateJobScheduled =
            new AtomicBoolean(false);

    private final AtomicBoolean rebuildAttemptedThisSession =
            new AtomicBoolean(false);

    private volatile boolean completeIndex;

    private IResourceChangeListener resourceListener;

    public BeanIndexService(File indexFile) {
        this.indexFile = indexFile;
    }

    public void start() {
        loadFromDisk();

        resourceListener = new IResourceChangeListener() {
            @Override
            public void resourceChanged(IResourceChangeEvent event) {
                collectJavaChanges(event.getDelta());
            }
        };

        ResourcesPlugin.getWorkspace().addResourceChangeListener(
                resourceListener,
                IResourceChangeEvent.POST_CHANGE);
    }

    public void stop() {
        if (resourceListener != null) {
            ResourcesPlugin.getWorkspace()
                    .removeResourceChangeListener(resourceListener);
            resourceListener = null;
        }

        persistSnapshot();
    }

    public IType resolve(String beanName, String preferredProjectName) {
        if (beanName == null || beanName.isEmpty()) {
            return null;
        }

        IType indexed = resolveIndexed(beanName, preferredProjectName);
        if (indexed != null) {
            return indexed;
        }

        IType conventional = resolveConventional(
                beanName,
                preferredProjectName);

        if (conventional != null) {
            indexType(conventional);
            schedulePersistOnly();
            return conventional;
        }

        /*
         * Explicit names like:
         *
         * @ManagedBean(name = "foo")
         * public class CompletelyDifferentController { ... }
         *
         * cannot be found from the EL name alone. We therefore do one full
         * source-type rebuild on the first unresolved lookup in a session.
         * This happens on our background navigation Job, never on the UI thread.
         */
        if (rebuildAttemptedThisSession.compareAndSet(false, true)) {
            rebuildAll();

            indexed = resolveIndexed(beanName, preferredProjectName);
            if (indexed != null) {
                return indexed;
            }
        }

        return null;
    }

    public void rebuildAll() {
        Map<String, List<BeanDescriptor>> newByName =
                new HashMap<String, List<BeanDescriptor>>();

        Map<String, List<BeanDescriptor>> newByPath =
                new HashMap<String, List<BeanDescriptor>>();

        for (IType type : JavaTypeFinder.findAllSourceTypes()) {
            BeanDescriptor descriptor = descriptorOf(type);

            if (descriptor != null) {
                addDescriptor(newByName, newByPath, descriptor);
            }
        }

        synchronized (lock) {
            byBeanName.clear();
            byBeanName.putAll(newByName);

            byResourcePath.clear();
            byResourcePath.putAll(newByPath);

            completeIndex = true;
        }

        persistSnapshot();
    }

    private IType resolveIndexed(
            String beanName,
            String preferredProjectName) {

        List<BeanDescriptor> candidates;

        synchronized (lock) {
            List<BeanDescriptor> current = byBeanName.get(beanName);

            if (current == null || current.isEmpty()) {
                return null;
            }

            candidates = new ArrayList<BeanDescriptor>(current);
        }

        Collections.sort(
                candidates,
                preferredProjectComparator(preferredProjectName));

        List<BeanDescriptor> stale =
                new ArrayList<BeanDescriptor>();

        for (BeanDescriptor descriptor : candidates) {
            IType type = descriptor.resolveType();

            if (type != null) {
                return type;
            }

            stale.add(descriptor);
        }

        if (!stale.isEmpty()) {
            synchronized (lock) {
                for (BeanDescriptor descriptor : stale) {
                    removeDescriptor(descriptor);
                }
            }
            schedulePersistOnly();
        }

        return null;
    }

    private IType resolveConventional(
            String beanName,
            String preferredProjectName) {

        String className =
                Character.toUpperCase(beanName.charAt(0))
                + beanName.substring(1);

        List<IType> candidates =
                JavaTypeFinder.findTypes(
                        className,
                        preferredProjectName);

        for (IType type : candidates) {
            try {
                if (beanName.equals(
                        BeanIntrospector.beanNameOf(type))) {
                    return type;
                }
            } catch (JavaModelException e) {
                // Continue with the remaining candidates.
            }
        }

        return null;
    }

    private Comparator<BeanDescriptor> preferredProjectComparator(
            final String preferredProjectName) {

        return new Comparator<BeanDescriptor>() {
            @Override
            public int compare(
                    BeanDescriptor left,
                    BeanDescriptor right) {

                boolean leftPreferred =
                        preferredProjectName != null
                        && preferredProjectName.equals(
                                left.getProjectName());

                boolean rightPreferred =
                        preferredProjectName != null
                        && preferredProjectName.equals(
                                right.getProjectName());

                if (leftPreferred == rightPreferred) {
                    return 0;
                }

                return leftPreferred ? -1 : 1;
            }
        };
    }

    private void collectJavaChanges(IResourceDelta rootDelta) {
        if (rootDelta == null) {
            return;
        }

        try {
            rootDelta.accept(new IResourceDeltaVisitor() {
                @Override
                public boolean visit(IResourceDelta delta)
                        throws CoreException {

                    IResource resource = delta.getResource();

                    if (resource.getType() == IResource.FILE
                            && "java".equalsIgnoreCase(
                                    resource.getFileExtension())) {

                        pendingJavaChanges.put(
                                resource.getFullPath().toPortableString(),
                                Integer.valueOf(delta.getKind()));

                        return false;
                    }

                    return true;
                }
            });
        } catch (CoreException e) {
            return;
        }

        if (!pendingJavaChanges.isEmpty()) {
            scheduleIncrementalUpdate();
        }
    }

    private void scheduleIncrementalUpdate() {
        if (!updateJobScheduled.compareAndSet(false, true)) {
            return;
        }

        Job job = new Job("Update JSF EL bean index") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    processPendingChanges();
                } finally {
                    updateJobScheduled.set(false);

                    if (!pendingJavaChanges.isEmpty()) {
                        scheduleIncrementalUpdate();
                    }
                }

                return Status.OK_STATUS;
            }
        };

        job.setSystem(true);
        job.schedule(250L);
    }

    private void processPendingChanges() {
        Map<String, Integer> batch =
                new LinkedHashMap<String, Integer>();

        for (Map.Entry<String, Integer> entry :
                pendingJavaChanges.entrySet()) {

            if (pendingJavaChanges.remove(
                    entry.getKey(),
                    entry.getValue())) {

                batch.put(
                        entry.getKey(),
                        entry.getValue());
            }
        }

        if (batch.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Integer> entry : batch.entrySet()) {
            String resourcePath = entry.getKey();
            int kind = entry.getValue().intValue();

            removeResource(resourcePath);

            if (kind != IResourceDelta.REMOVED) {
                IFile file = ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .getFile(new Path(resourcePath));

                if (file.exists()) {
                    indexCompilationUnit(file);
                }
            }
        }

        JavaPropertyResolver.clearCache();
        JavaReturnTypeResolver.clearCache();

        persistSnapshot();
    }

    private void indexCompilationUnit(IFile file) {
        ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(file);

        if (unit == null || !unit.exists()) {
            return;
        }

        try {
            for (IType type : unit.getAllTypes()) {
                indexType(type);
            }
        } catch (JavaModelException e) {
            // The Java model can be temporarily stale while a build is running.
        }
    }

    private void indexType(IType type) {
        BeanDescriptor descriptor = descriptorOf(type);

        if (descriptor == null) {
            return;
        }

        synchronized (lock) {
            addDescriptor(
                    byBeanName,
                    byResourcePath,
                    descriptor);
        }
    }

    private BeanDescriptor descriptorOf(IType type) {
        try {
            String beanName = BeanIntrospector.beanNameOf(type);

            if (beanName == null || beanName.isEmpty()) {
                return null;
            }

            IResource resource = type.getResource();

            if (!(resource instanceof IFile)) {
                return null;
            }

            IFile file = (IFile) resource;

            return new BeanDescriptor(
                    beanName,
                    file.getProject().getName(),
                    type.getFullyQualifiedName('.'),
                    file.getFullPath().toPortableString(),
                    file.getModificationStamp());

        } catch (JavaModelException e) {
            return null;
        }
    }

    private void removeResource(String resourcePath) {
        synchronized (lock) {
            List<BeanDescriptor> descriptors =
                    byResourcePath.remove(resourcePath);

            if (descriptors == null) {
                return;
            }

            for (BeanDescriptor descriptor : descriptors) {
                List<BeanDescriptor> named =
                        byBeanName.get(
                                descriptor.getBeanName());

                if (named != null) {
                    named.remove(descriptor);

                    if (named.isEmpty()) {
                        byBeanName.remove(
                                descriptor.getBeanName());
                    }
                }
            }
        }
    }

    private void removeDescriptor(BeanDescriptor descriptor) {
        List<BeanDescriptor> named =
                byBeanName.get(descriptor.getBeanName());

        if (named != null) {
            removeEquivalent(named, descriptor);

            if (named.isEmpty()) {
                byBeanName.remove(
                        descriptor.getBeanName());
            }
        }

        List<BeanDescriptor> resource =
                byResourcePath.get(
                        descriptor.getResourcePath());

        if (resource != null) {
            removeEquivalent(resource, descriptor);

            if (resource.isEmpty()) {
                byResourcePath.remove(
                        descriptor.getResourcePath());
            }
        }
    }

    private static void removeEquivalent(
            List<BeanDescriptor> descriptors,
            BeanDescriptor target) {

        for (int i = descriptors.size() - 1; i >= 0; i--) {
            BeanDescriptor current = descriptors.get(i);

            if (sameIdentity(current, target)) {
                descriptors.remove(i);
            }
        }
    }

    private static void addDescriptor(
            Map<String, List<BeanDescriptor>> nameMap,
            Map<String, List<BeanDescriptor>> pathMap,
            BeanDescriptor descriptor) {

        List<BeanDescriptor> byName =
                nameMap.get(descriptor.getBeanName());

        if (byName == null) {
            byName = new ArrayList<BeanDescriptor>();
            nameMap.put(
                    descriptor.getBeanName(),
                    byName);
        }

        removeEquivalent(byName, descriptor);
        byName.add(descriptor);

        List<BeanDescriptor> byPath =
                pathMap.get(descriptor.getResourcePath());

        if (byPath == null) {
            byPath = new ArrayList<BeanDescriptor>();
            pathMap.put(
                    descriptor.getResourcePath(),
                    byPath);
        }

        removeEquivalent(byPath, descriptor);
        byPath.add(descriptor);
    }

    private static boolean sameIdentity(
            BeanDescriptor left,
            BeanDescriptor right) {

        return left.getBeanName().equals(
                    right.getBeanName())
                && left.getProjectName().equals(
                    right.getProjectName())
                && left.getQualifiedTypeName().equals(
                    right.getQualifiedTypeName())
                && left.getResourcePath().equals(
                    right.getResourcePath());
    }

    private void schedulePersistOnly() {
        Job job = new Job("Persist JSF EL bean index") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                persistSnapshot();
                return Status.OK_STATUS;
            }
        };

        job.setSystem(true);
        job.schedule(250L);
    }

    private void loadFromDisk() {
        if (!indexFile.isFile()) {
            completeIndex = false;
            return;
        }

        Map<String, List<BeanDescriptor>> loadedByName =
                new HashMap<String, List<BeanDescriptor>>();

        Map<String, List<BeanDescriptor>> loadedByPath =
                new HashMap<String, List<BeanDescriptor>>();

        DataInputStream in = null;

        try {
            in = new DataInputStream(
                    new BufferedInputStream(
                            new FileInputStream(indexFile)));

            if (in.readInt() != MAGIC) {
                return;
            }

            if (in.readInt() != FORMAT_VERSION) {
                return;
            }

            boolean loadedComplete = in.readBoolean();
            int count = in.readInt();

            if (count < 0 || count > 1000000) {
                return;
            }

            for (int i = 0; i < count; i++) {
                BeanDescriptor descriptor =
                        new BeanDescriptor(
                                in.readUTF(),
                                in.readUTF(),
                                in.readUTF(),
                                in.readUTF(),
                                in.readLong());

                addDescriptor(
                        loadedByName,
                        loadedByPath,
                        descriptor);
            }

            synchronized (lock) {
                byBeanName.clear();
                byBeanName.putAll(loadedByName);

                byResourcePath.clear();
                byResourcePath.putAll(loadedByPath);

                completeIndex = loadedComplete;
            }

        } catch (EOFException e) {
            clearLoadedIndex();
        } catch (IOException e) {
            clearLoadedIndex();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Nothing useful to do.
                }
            }
        }
    }

    private void clearLoadedIndex() {
        synchronized (lock) {
            byBeanName.clear();
            byResourcePath.clear();
            completeIndex = false;
        }
    }

    private void persistSnapshot() {
        List<BeanDescriptor> snapshot =
                new ArrayList<BeanDescriptor>();

        boolean snapshotComplete;

        synchronized (lock) {
            Set<String> seen =
                    new LinkedHashSet<String>();

            for (List<BeanDescriptor> values :
                    byBeanName.values()) {

                for (BeanDescriptor descriptor : values) {
                    String identity =
                            descriptor.getBeanName()
                            + "\n"
                            + descriptor.getProjectName()
                            + "\n"
                            + descriptor.getQualifiedTypeName()
                            + "\n"
                            + descriptor.getResourcePath();

                    if (seen.add(identity)) {
                        snapshot.add(descriptor);
                    }
                }
            }

            snapshotComplete = completeIndex;
        }

        File parent = indexFile.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File tempFile =
                new File(
                        indexFile.getParentFile(),
                        indexFile.getName() + ".tmp");

        DataOutputStream out = null;

        try {
            out = new DataOutputStream(
                    new BufferedOutputStream(
                            new FileOutputStream(tempFile)));

            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeBoolean(snapshotComplete);
            out.writeInt(snapshot.size());

            for (BeanDescriptor descriptor : snapshot) {
                out.writeUTF(descriptor.getBeanName());
                out.writeUTF(descriptor.getProjectName());
                out.writeUTF(descriptor.getQualifiedTypeName());
                out.writeUTF(descriptor.getResourcePath());
                out.writeLong(descriptor.getModificationStamp());
            }

            out.flush();
            out.close();
            out = null;

            try {
                Files.move(
                        tempFile.toPath(),
                        indexFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailed) {
                Files.move(
                        tempFile.toPath(),
                        indexFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // Nothing useful to do.
                }
            }
        }
    }
}
