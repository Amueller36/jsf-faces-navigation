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
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

public final class JsfViewIndexService {

    private static final int MAGIC = 0x4A534649; // JSFI
    private static final int FORMAT_VERSION = 1;

    private final File indexFile;
    private final Object lock = new Object();

    private final Map<String, IndexedViewFile> files =
            new HashMap<String, IndexedViewFile>();

    private final Map<Integer, Map<String, List<ViewSymbol>>> symbols =
            new HashMap<Integer, Map<String, List<ViewSymbol>>>();

    private final Map<String, Integer> pendingChanges =
            new ConcurrentHashMap<String, Integer>();

    private final AtomicBoolean updateScheduled =
            new AtomicBoolean(false);

    private final AtomicBoolean buildAttempted =
            new AtomicBoolean(false);

    private volatile boolean completeIndex;
    private IResourceChangeListener listener;

    public JsfViewIndexService(File indexFile) {
        this.indexFile = indexFile;
    }

    public void start() {
        load();

        listener = new IResourceChangeListener() {
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

        if (completeIndex) {
            scheduleReconcile();
        }
    }

    public void stop() {
        if (listener != null) {
            ResourcesPlugin.getWorkspace()
                    .removeResourceChangeListener(listener);

            listener = null;
        }

        persist();
    }

    public List<ViewSymbol> find(
            int kind,
            String name,
            String preferredProject) {

        ensureBuilt();

        List<ViewSymbol> result =
                new ArrayList<ViewSymbol>();

        synchronized (lock) {
            Map<String, List<ViewSymbol>> byName =
                    symbols.get(Integer.valueOf(kind));

            if (byName != null) {
                List<ViewSymbol> current =
                        byName.get(name);

                if (current != null) {
                    for (ViewSymbol symbol : current) {
                        if (symbol.isCurrent()) {
                            result.add(symbol);
                        }
                    }
                }
            }
        }

        Collections.sort(
                result,
                projectComparator(preferredProject));

        return result;
    }

    public List<ViewSymbol> symbolsInFile(
            IFile file,
            int kind) {

        ensureBuilt();

        if (file == null) {
            return Collections.emptyList();
        }

        String path =
                file.getFullPath().toPortableString();

        List<ViewSymbol> result =
                new ArrayList<ViewSymbol>();

        synchronized (lock) {
            IndexedViewFile indexed =
                    files.get(path);

            if (indexed == null) {
                return result;
            }

            for (ViewSymbol symbol :
                    indexed.getSymbols()) {

                if (symbol.getKind() == kind
                        && symbol.isCurrent()) {

                    result.add(symbol);
                }
            }
        }

        return result;
    }

    public List<ViewSymbol> referencesToComponent(
            String componentId,
            String preferredProject) {

        return find(
                ViewSymbol.COMPONENT_REFERENCE,
                componentId,
                preferredProject);
    }

    public List<ViewSymbol> referencesToWidget(
            String widgetVar,
            String preferredProject) {

        return find(
                ViewSymbol.WIDGET_REFERENCE,
                widgetVar,
                preferredProject);
    }

    public void rebuildAll() {
        final Map<String, IndexedViewFile> newFiles =
                new HashMap<String, IndexedViewFile>();

        IProject[] projects =
                ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .getProjects();

        for (IProject project : projects) {
            if (!project.isOpen()) {
                continue;
            }

            try {
                project.accept(
                        new IResourceProxyVisitor() {
                            @Override
                            public boolean visit(
                                    IResourceProxy proxy)
                                    throws CoreException {

                                if (proxy.getType()
                                        == IResource.FOLDER) {

                                    String name =
                                            proxy.getName();

                                    if ("target".equals(name)
                                            || "build".equals(name)
                                            || "bin".equals(name)
                                            || ".git".equals(name)) {

                                        return false;
                                    }

                                    return true;
                                }

                                if (proxy.getType()
                                        != IResource.FILE
                                        || !isViewFile(
                                                proxy.getName())) {

                                    return true;
                                }

                                IResource resource =
                                        proxy.requestResource();

                                if (resource instanceof IFile) {
                                    IndexedViewFile indexed =
                                            JsfViewParser.parse(
                                                    (IFile) resource);

                                    if (indexed != null) {
                                        newFiles.put(
                                                indexed.getResourcePath(),
                                                indexed);
                                    }
                                }

                                return false;
                            }
                        },
                        IResource.NONE);

            } catch (CoreException e) {
                // Continue with other projects.
            }
        }

        synchronized (lock) {
            files.clear();
            files.putAll(newFiles);
            rebuildSymbolMaps();
            completeIndex = true;
        }

        persist();
    }

    private void ensureBuilt() {
        if (completeIndex) {
            return;
        }

        if (buildAttempted.compareAndSet(
                false,
                true)) {

            rebuildAll();
        }
    }

    private void scheduleReconcile() {
        Job job =
                new Job("Reconcile JSF view index") {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        reconcile();
                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.schedule(500L);
    }

    private void reconcile() {
        final Set<String> seen =
                new LinkedHashSet<String>();

        IProject[] projects =
                ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .getProjects();

        for (IProject project : projects) {
            if (!project.isOpen()) {
                continue;
            }

            try {
                project.accept(
                        new IResourceProxyVisitor() {
                            @Override
                            public boolean visit(
                                    IResourceProxy proxy)
                                    throws CoreException {

                                if (proxy.getType()
                                        == IResource.FOLDER) {

                                    String name =
                                            proxy.getName();

                                    if ("target".equals(name)
                                            || "build".equals(name)
                                            || "bin".equals(name)
                                            || ".git".equals(name)) {

                                        return false;
                                    }

                                    return true;
                                }

                                if (proxy.getType()
                                        != IResource.FILE
                                        || !isViewFile(
                                                proxy.getName())) {

                                    return true;
                                }

                                IFile file =
                                        (IFile) proxy.requestResource();

                                String path =
                                        file.getFullPath()
                                                .toPortableString();

                                seen.add(path);

                                IndexedViewFile old;

                                synchronized (lock) {
                                    old = files.get(path);
                                }

                                if (old != null
                                        && old.getModificationStamp()
                                                == file.getModificationStamp()) {

                                    return false;
                                }

                                updateOne(file);
                                return false;
                            }
                        },
                        IResource.NONE);

            } catch (CoreException e) {
                // Continue.
            }
        }

        List<String> removed =
                new ArrayList<String>();

        synchronized (lock) {
            for (String path : files.keySet()) {
                if (!seen.contains(path)) {
                    removed.add(path);
                }
            }

            for (String path : removed) {
                files.remove(path);
            }

            if (!removed.isEmpty()) {
                rebuildSymbolMaps();
            }
        }

        persist();
    }

    private void collect(IResourceDelta delta) {
        if (delta == null) {
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
                                    == IResource.FILE
                                    && isViewFile(
                                            resource.getName())
                                    && isContentChange(child)) {

                                pendingChanges.put(
                                        resource.getFullPath()
                                                .toPortableString(),
                                        Integer.valueOf(
                                                child.getKind()));

                                return false;
                            }

                            return true;
                        }
                    });

        } catch (CoreException e) {
            return;
        }

        if (!pendingChanges.isEmpty()) {
            scheduleUpdate();
        }
    }

    private void scheduleUpdate() {
        if (!updateScheduled.compareAndSet(
                false,
                true)) {

            return;
        }

        Job job =
                new Job("Update JSF view index") {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        try {
                            processPending();
                        } finally {
                            updateScheduled.set(false);

                            if (!pendingChanges.isEmpty()) {
                                scheduleUpdate();
                            }
                        }

                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.schedule(250L);
    }

    private void processPending() {
        Map<String, Integer> batch =
                new LinkedHashMap<String, Integer>();

        for (Map.Entry<String, Integer> entry :
                pendingChanges.entrySet()) {

            if (pendingChanges.remove(
                    entry.getKey(),
                    entry.getValue())) {

                batch.put(
                        entry.getKey(),
                        entry.getValue());
            }
        }

        for (Map.Entry<String, Integer> entry :
                batch.entrySet()) {

            String path = entry.getKey();

            if (entry.getValue().intValue()
                    == IResourceDelta.REMOVED) {

                synchronized (lock) {
                    files.remove(path);
                    rebuildSymbolMaps();
                }

                continue;
            }

            IFile file =
                    ResourcesPlugin.getWorkspace()
                            .getRoot()
                            .getFile(new Path(path));

            if (file.exists()) {
                updateOne(file);
            }
        }

        persist();
    }

    private void updateOne(IFile file) {
        IndexedViewFile parsed =
                JsfViewParser.parse(file);

        if (parsed == null) {
            return;
        }

        synchronized (lock) {
            files.put(
                    parsed.getResourcePath(),
                    parsed);

            rebuildSymbolMaps();
        }
    }

    private void rebuildSymbolMaps() {
        symbols.clear();

        for (IndexedViewFile file :
                files.values()) {

            for (ViewSymbol symbol :
                    file.getSymbols()) {

                Integer kind =
                        Integer.valueOf(
                                symbol.getKind());

                Map<String, List<ViewSymbol>> byName =
                        symbols.get(kind);

                if (byName == null) {
                    byName =
                            new HashMap<String, List<ViewSymbol>>();

                    symbols.put(kind, byName);
                }

                List<ViewSymbol> list =
                        byName.get(
                                symbol.getName());

                if (list == null) {
                    list =
                            new ArrayList<ViewSymbol>();

                    byName.put(
                            symbol.getName(),
                            list);
                }

                list.add(symbol);
            }
        }
    }

    private Comparator<ViewSymbol> projectComparator(
            final String preferredProject) {

        return new Comparator<ViewSymbol>() {
            @Override
            public int compare(
                    ViewSymbol left,
                    ViewSymbol right) {

                boolean lp =
                        belongsTo(
                                left,
                                preferredProject);

                boolean rp =
                        belongsTo(
                                right,
                                preferredProject);

                if (lp == rp) {
                    return left.getResourcePath()
                            .compareTo(
                                    right.getResourcePath());
                }

                return lp ? -1 : 1;
            }
        };
    }

    private boolean belongsTo(
            ViewSymbol symbol,
            String project) {

        if (project == null) {
            return false;
        }

        IFile file = symbol.getFile();

        return file.exists()
                && project.equals(
                        file.getProject().getName());
    }


    private static boolean isContentChange(
            IResourceDelta delta) {

        if (delta.getKind() == IResourceDelta.ADDED
                || delta.getKind() == IResourceDelta.REMOVED) {

            return true;
        }

        int flags = delta.getFlags();

        return (flags & IResourceDelta.CONTENT) != 0
                || (flags & IResourceDelta.REPLACED) != 0;
    }

    private static boolean isViewFile(String name) {
        String lower =
                name.toLowerCase();

        return lower.endsWith(".xhtml")
                || lower.endsWith(".html")
                || lower.endsWith(".htm");
    }

    private void load() {
        if (!indexFile.isFile()) {
            return;
        }

        DataInputStream in = null;

        try {
            in =
                    new DataInputStream(
                            new BufferedInputStream(
                                    new FileInputStream(
                                            indexFile)));

            if (in.readInt() != MAGIC
                    || in.readInt()
                            != FORMAT_VERSION) {

                return;
            }

            boolean loadedComplete =
                    in.readBoolean();

            int fileCount =
                    in.readInt();

            Map<String, IndexedViewFile> loaded =
                    new HashMap<String, IndexedViewFile>();

            for (int i = 0; i < fileCount; i++) {
                String path = in.readUTF();
                long stamp = in.readLong();
                int symbolCount = in.readInt();

                List<ViewSymbol> list =
                        new ArrayList<ViewSymbol>();

                for (int j = 0; j < symbolCount; j++) {
                    list.add(
                            new ViewSymbol(
                                    in.readInt(),
                                    in.readUTF(),
                                    path,
                                    in.readInt(),
                                    nullableRead(in),
                                    nullableRead(in),
                                    stamp));
                }

                loaded.put(
                        path,
                        new IndexedViewFile(
                                path,
                                stamp,
                                list));
            }

            synchronized (lock) {
                files.clear();
                files.putAll(loaded);
                rebuildSymbolMaps();
                completeIndex = loadedComplete;
            }

        } catch (EOFException e) {
            clear();
        } catch (IOException e) {
            clear();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void clear() {
        synchronized (lock) {
            files.clear();
            symbols.clear();
            completeIndex = false;
        }
    }

    private void persist() {
        List<IndexedViewFile> snapshot;
        boolean complete;

        synchronized (lock) {
            snapshot =
                    new ArrayList<IndexedViewFile>(
                            files.values());

            complete = completeIndex;
        }

        File parent =
                indexFile.getParentFile();

        if (parent != null
                && !parent.exists()) {

            parent.mkdirs();
        }

        File tmp =
                new File(
                        indexFile.getParentFile(),
                        indexFile.getName() + ".tmp");

        DataOutputStream out = null;

        try {
            out =
                    new DataOutputStream(
                            new BufferedOutputStream(
                                    new FileOutputStream(tmp)));

            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeBoolean(complete);
            out.writeInt(snapshot.size());

            for (IndexedViewFile file : snapshot) {
                out.writeUTF(file.getResourcePath());
                out.writeLong(
                        file.getModificationStamp());

                out.writeInt(
                        file.getSymbols().size());

                for (ViewSymbol symbol :
                        file.getSymbols()) {

                    out.writeInt(symbol.getKind());
                    out.writeUTF(symbol.getName());
                    out.writeInt(symbol.getOffset());
                    nullableWrite(
                            out,
                            symbol.getAttributeName());
                    nullableWrite(
                            out,
                            symbol.getExtra());
                }
            }

            out.flush();
            out.close();
            out = null;

            try {
                Files.move(
                        tmp.toPath(),
                        indexFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

            } catch (IOException atomicFailed) {
                Files.move(
                        tmp.toPath(),
                        indexFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
            tmp.delete();

        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void nullableWrite(
            DataOutputStream out,
            String value)
            throws IOException {

        out.writeBoolean(value != null);

        if (value != null) {
            out.writeUTF(value);
        }
    }

    private static String nullableRead(
            DataInputStream in)
            throws IOException {

        return in.readBoolean()
                ? in.readUTF()
                : null;
    }
}
