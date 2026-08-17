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
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

public final class WebIndexService {

    private static final int MAGIC = 0x4A534657; // JSFW
    private static final int FORMAT_VERSION = 1;
    private static final String IDENT = "[A-Za-z_$][A-Za-z0-9_$]*";

    private static final Pattern FUNCTION_DECL = Pattern.compile(
            "\\bfunction\\s+(" + IDENT + ")\\s*\\(");

    private static final Pattern FUNCTION_ASSIGN = Pattern.compile(
            "(?:\\b(?:var|let|const)\\s+)?(" + IDENT + ")\\s*=\\s*function\\s*\\(");

    private static final Pattern ARROW_ASSIGN = Pattern.compile(
            "(?:\\b(?:var|let|const)\\s+)?(" + IDENT + ")\\s*=\\s*(?:\\([^)]*\\)|" + IDENT + ")\\s*=>");

    private static final Pattern EL_BLOCK = Pattern.compile("[#\\$]\\{([^}]*)\\}");
    private static final Pattern EL_ROOT = Pattern.compile(
            "(?<![A-Za-z0-9_$\\.])(" + IDENT + ")\\s*(?=\\.|\\[|$|\\s|\\)|,)");

    private final File indexFile;
    private final Object lock = new Object();

    private final Map<String, List<JavaScriptDefinition>> functions =
            new HashMap<String, List<JavaScriptDefinition>>();
    private final Map<String, List<BeanUsage>> beanUsages =
            new HashMap<String, List<BeanUsage>>();
    private final Map<String, IndexedWebFile> byResourcePath =
            new HashMap<String, IndexedWebFile>();

    private final Map<String, Integer> pendingChanges =
            new ConcurrentHashMap<String, Integer>();
    private final AtomicBoolean updateScheduled = new AtomicBoolean(false);
    private final AtomicBoolean fullBuildAttempted = new AtomicBoolean(false);

    private volatile boolean completeIndex;
    private IResourceChangeListener listener;

    public WebIndexService(File indexFile) {
        this.indexFile = indexFile;
    }

    public void start() {
        loadFromDisk();

        listener = new IResourceChangeListener() {
            @Override
            public void resourceChanged(IResourceChangeEvent event) {
                collectChanges(event.getDelta());
            }
        };

        ResourcesPlugin.getWorkspace().addResourceChangeListener(
                listener,
                IResourceChangeEvent.POST_CHANGE);

        if (completeIndex) {
            scheduleStartupReconcile();
        }
    }

    private void scheduleStartupReconcile() {
        Job job = new Job("Reconcile JSF web navigation index") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                reconcileWorkspace();
                return Status.OK_STATUS;
            }
        };
        job.setSystem(true);
        job.schedule(500L);
    }

    /*
     * Fast restart path: traverse resource proxies, but only re-parse files
     * whose modification stamp changed (or which are new). This preserves the
     * disk-cache benefit while still noticing edits/additions/removals that
     * happened while Eclipse was closed.
     */
    private void reconcileWorkspace() {
        final Set<String> seen = new LinkedHashSet<String>();
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();

        for (IProject project : projects) {
            if (!project.isOpen()) {
                continue;
            }

            try {
                project.accept(new IResourceProxyVisitor() {
                    @Override
                    public boolean visit(IResourceProxy proxy) throws CoreException {
                        if (proxy.getType() == IResource.FOLDER) {
                            String name = proxy.getName();
                            if ("target".equals(name) || "build".equals(name)
                                    || "bin".equals(name) || ".git".equals(name)) {
                                return false;
                            }
                            return true;
                        }

                        if (proxy.getType() != IResource.FILE || !isWebFile(proxy.getName())) {
                            return true;
                        }

                        IResource resource = proxy.requestResource();
                        if (!(resource instanceof IFile)) {
                            return false;
                        }

                        IFile file = (IFile) resource;
                        String path = file.getFullPath().toPortableString();
                        seen.add(path);

                        IndexedWebFile old;
                        synchronized (lock) {
                            old = byResourcePath.get(path);
                        }

                        if (old != null && old.modificationStamp == file.getModificationStamp()) {
                            return false;
                        }

                        removeResource(path);
                        IndexedWebFile indexed = parseFile(file);
                        if (indexed != null) {
                            synchronized (lock) {
                                byResourcePath.put(path, indexed);
                                addIndexed(functions, beanUsages, indexed);
                            }
                        }
                        return false;
                    }
                }, IResource.NONE);
            } catch (CoreException e) {
                // Continue with remaining projects.
            }
        }

        List<String> removed = new ArrayList<String>();
        synchronized (lock) {
            for (String indexedPath : byResourcePath.keySet()) {
                if (!seen.contains(indexedPath)) {
                    removed.add(indexedPath);
                }
            }
        }

        for (String path : removed) {
            removeResource(path);
        }

        persistSnapshot();
    }

    public void stop() {
        if (listener != null) {
            ResourcesPlugin.getWorkspace().removeResourceChangeListener(listener);
            listener = null;
        }
        persistSnapshot();
    }

    public List<JavaScriptDefinition> findFunctions(
            String functionName,
            String preferredProjectName) {

        ensureBuiltIfNeeded();
        List<JavaScriptDefinition> result = new ArrayList<JavaScriptDefinition>();
        boolean staleFound = false;

        synchronized (lock) {
            List<JavaScriptDefinition> current = functions.get(functionName);
            if (current != null) {
                for (JavaScriptDefinition definition : current) {
                    if (definition.isCurrent()) {
                        result.add(definition);
                    } else {
                        staleFound = true;
                    }
                }
            }
        }

        if (staleFound) {
            rebuildAll();
            result.clear();
            synchronized (lock) {
                List<JavaScriptDefinition> refreshed = functions.get(functionName);
                if (refreshed != null) {
                    result.addAll(refreshed);
                }
            }
        }

        Collections.sort(result, projectComparator(preferredProjectName));
        return result;
    }

    public List<BeanUsage> findBeanUsages(
            String beanName,
            String preferredProjectName) {

        ensureBuiltIfNeeded();
        List<BeanUsage> result = new ArrayList<BeanUsage>();
        boolean staleFound = false;

        synchronized (lock) {
            List<BeanUsage> current = beanUsages.get(beanName);
            if (current != null) {
                for (BeanUsage usage : current) {
                    if (usage.isCurrent()) {
                        result.add(usage);
                    } else {
                        staleFound = true;
                    }
                }
            }
        }

        if (staleFound) {
            rebuildAll();
            result.clear();
            synchronized (lock) {
                List<BeanUsage> refreshed = beanUsages.get(beanName);
                if (refreshed != null) {
                    result.addAll(refreshed);
                }
            }
        }

        Collections.sort(result, beanProjectComparator(preferredProjectName));
        return result;
    }

    public void rebuildAll() {
        final Map<String, List<JavaScriptDefinition>> newFunctions =
                new HashMap<String, List<JavaScriptDefinition>>();
        final Map<String, List<BeanUsage>> newUsages =
                new HashMap<String, List<BeanUsage>>();
        final Map<String, IndexedWebFile> newFiles =
                new HashMap<String, IndexedWebFile>();

        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();

        for (IProject project : projects) {
            if (!project.isOpen()) {
                continue;
            }

            try {
                project.accept(new IResourceProxyVisitor() {
                    @Override
                    public boolean visit(IResourceProxy proxy) throws CoreException {
                        if (proxy.getType() == IResource.FOLDER) {
                            String name = proxy.getName();
                            if ("target".equals(name) || "build".equals(name)
                                    || "bin".equals(name) || ".git".equals(name)) {
                                return false;
                            }
                            return true;
                        }

                        if (proxy.getType() != IResource.FILE || !isWebFile(proxy.getName())) {
                            return true;
                        }

                        IResource resource = proxy.requestResource();
                        if (resource instanceof IFile) {
                            IndexedWebFile indexed = parseFile((IFile) resource);
                            if (indexed != null) {
                                newFiles.put(indexed.resourcePath, indexed);
                                addIndexed(newFunctions, newUsages, indexed);
                            }
                        }
                        return false;
                    }
                }, IResource.NONE);
            } catch (CoreException e) {
                // Continue with remaining projects.
            }
        }

        synchronized (lock) {
            functions.clear();
            functions.putAll(newFunctions);
            beanUsages.clear();
            beanUsages.putAll(newUsages);
            byResourcePath.clear();
            byResourcePath.putAll(newFiles);
            completeIndex = true;
        }

        persistSnapshot();
    }

    private void ensureBuiltIfNeeded() {
        if (completeIndex) {
            return;
        }

        if (fullBuildAttempted.compareAndSet(false, true)) {
            rebuildAll();
        }
    }

    private void scheduleFullRebuild() {
        Job job = new Job("Rebuild JSF web navigation index") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                rebuildAll();
                return Status.OK_STATUS;
            }
        };
        job.setSystem(true);
        job.schedule(250L);
    }

    private void collectChanges(IResourceDelta rootDelta) {
        if (rootDelta == null) {
            return;
        }

        try {
            rootDelta.accept(new IResourceDeltaVisitor() {
                @Override
                public boolean visit(IResourceDelta delta) throws CoreException {
                    IResource resource = delta.getResource();
                    if (resource.getType() == IResource.FILE && isWebFile(resource.getName())) {
                        pendingChanges.put(
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

        if (!pendingChanges.isEmpty()) {
            scheduleIncrementalUpdate();
        }
    }

    private void scheduleIncrementalUpdate() {
        if (!updateScheduled.compareAndSet(false, true)) {
            return;
        }

        Job job = new Job("Update JSF web navigation index") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    processPendingChanges();
                } finally {
                    updateScheduled.set(false);
                    if (!pendingChanges.isEmpty()) {
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
        Map<String, Integer> batch = new HashMap<String, Integer>();

        for (Map.Entry<String, Integer> entry : pendingChanges.entrySet()) {
            if (pendingChanges.remove(entry.getKey(), entry.getValue())) {
                batch.put(entry.getKey(), entry.getValue());
            }
        }

        for (Map.Entry<String, Integer> entry : batch.entrySet()) {
            String resourcePath = entry.getKey();
            removeResource(resourcePath);

            if (entry.getValue().intValue() != IResourceDelta.REMOVED) {
                IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(resourcePath));
                if (file.exists()) {
                    IndexedWebFile indexed = parseFile(file);
                    if (indexed != null) {
                        synchronized (lock) {
                            byResourcePath.put(resourcePath, indexed);
                            addIndexed(functions, beanUsages, indexed);
                        }
                    }
                }
            }
        }

        persistSnapshot();
    }

    private IndexedWebFile parseFile(IFile file) {
        InputStream input = null;
        try {
            input = file.getContents();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) >= 0) {
                buffer.write(chunk, 0, read);
            }
            String source = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            long stamp = file.getModificationStamp();
            String path = file.getFullPath().toPortableString();

            List<JavaScriptDefinition> definitions = new ArrayList<JavaScriptDefinition>();
            collectFunctions(FUNCTION_DECL, source, path, stamp, definitions);
            collectFunctions(FUNCTION_ASSIGN, source, path, stamp, definitions);
            collectFunctions(ARROW_ASSIGN, source, path, stamp, definitions);

            List<BeanUsage> usages = new ArrayList<BeanUsage>();
            if (file.getName().toLowerCase().endsWith(".xhtml")) {
                collectBeanUsages(source, path, stamp, usages);
            }

            return new IndexedWebFile(path, stamp, definitions, usages);

        } catch (CoreException e) {
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void collectFunctions(
            Pattern pattern,
            String source,
            String path,
            long stamp,
            List<JavaScriptDefinition> output) {

        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            output.add(new JavaScriptDefinition(
                    matcher.group(1),
                    path,
                    matcher.start(1),
                    stamp));
        }
    }

    private static void collectBeanUsages(
            String source,
            String path,
            long stamp,
            List<BeanUsage> output) {

        Matcher blockMatcher = EL_BLOCK.matcher(source);
        Set<String> unique = new LinkedHashSet<String>();

        while (blockMatcher.find()) {
            String body = blockMatcher.group(1);
            int bodyOffset = blockMatcher.start(1);
            Matcher rootMatcher = EL_ROOT.matcher(body);

            while (rootMatcher.find()) {
                String name = rootMatcher.group(1);
                if (isELKeyword(name)) {
                    continue;
                }

                String identity = name + "@" + (bodyOffset + rootMatcher.start(1));
                if (unique.add(identity)) {
                    output.add(new BeanUsage(
                            name,
                            path,
                            bodyOffset + rootMatcher.start(1),
                            stamp));
                }
            }
        }
    }

    private static boolean isELKeyword(String value) {
        return "and".equals(value) || "or".equals(value)
                || "not".equals(value) || "empty".equals(value)
                || "true".equals(value) || "false".equals(value)
                || "null".equals(value) || "eq".equals(value)
                || "ne".equals(value) || "lt".equals(value)
                || "gt".equals(value) || "le".equals(value)
                || "ge".equals(value) || "div".equals(value)
                || "mod".equals(value);
    }

    private void removeResource(String resourcePath) {
        synchronized (lock) {
            IndexedWebFile old = byResourcePath.remove(resourcePath);
            if (old == null) {
                return;
            }

            for (JavaScriptDefinition definition : old.functions) {
                List<JavaScriptDefinition> values = functions.get(definition.getFunctionName());
                if (values != null) {
                    removeDefinition(values, definition);
                    if (values.isEmpty()) {
                        functions.remove(definition.getFunctionName());
                    }
                }
            }

            for (BeanUsage usage : old.beanUsages) {
                List<BeanUsage> values = beanUsages.get(usage.getBeanName());
                if (values != null) {
                    removeUsage(values, usage);
                    if (values.isEmpty()) {
                        beanUsages.remove(usage.getBeanName());
                    }
                }
            }
        }
    }

    private static void addIndexed(
            Map<String, List<JavaScriptDefinition>> functionMap,
            Map<String, List<BeanUsage>> usageMap,
            IndexedWebFile indexed) {

        for (JavaScriptDefinition definition : indexed.functions) {
            List<JavaScriptDefinition> values = functionMap.get(definition.getFunctionName());
            if (values == null) {
                values = new ArrayList<JavaScriptDefinition>();
                functionMap.put(definition.getFunctionName(), values);
            }
            values.add(definition);
        }

        for (BeanUsage usage : indexed.beanUsages) {
            List<BeanUsage> values = usageMap.get(usage.getBeanName());
            if (values == null) {
                values = new ArrayList<BeanUsage>();
                usageMap.put(usage.getBeanName(), values);
            }
            values.add(usage);
        }
    }

    private static void removeDefinition(
            List<JavaScriptDefinition> values,
            JavaScriptDefinition target) {
        for (int i = values.size() - 1; i >= 0; i--) {
            JavaScriptDefinition current = values.get(i);
            if (current.getResourcePath().equals(target.getResourcePath())
                    && current.getOffset() == target.getOffset()) {
                values.remove(i);
            }
        }
    }

    private static void removeUsage(List<BeanUsage> values, BeanUsage target) {
        for (int i = values.size() - 1; i >= 0; i--) {
            BeanUsage current = values.get(i);
            if (current.getResourcePath().equals(target.getResourcePath())
                    && current.getOffset() == target.getOffset()) {
                values.remove(i);
            }
        }
    }

    private static boolean isWebFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".xhtml") || lower.endsWith(".js");
    }

    private Comparator<JavaScriptDefinition> projectComparator(final String preferred) {
        return new Comparator<JavaScriptDefinition>() {
            @Override
            public int compare(JavaScriptDefinition left, JavaScriptDefinition right) {
                boolean lp = preferred != null && preferred.equals(left.getProjectName());
                boolean rp = preferred != null && preferred.equals(right.getProjectName());
                if (lp != rp) {
                    return lp ? -1 : 1;
                }
                return left.getResourcePath().compareTo(right.getResourcePath());
            }
        };
    }

    private Comparator<BeanUsage> beanProjectComparator(final String preferred) {
        return new Comparator<BeanUsage>() {
            @Override
            public int compare(BeanUsage left, BeanUsage right) {
                boolean lp = preferred != null && preferred.equals(left.getProjectName());
                boolean rp = preferred != null && preferred.equals(right.getProjectName());
                if (lp != rp) {
                    return lp ? -1 : 1;
                }
                return left.getResourcePath().compareTo(right.getResourcePath());
            }
        };
    }

    private void loadFromDisk() {
        if (!indexFile.isFile()) {
            return;
        }

        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(indexFile)));
            if (in.readInt() != MAGIC || in.readInt() != FORMAT_VERSION) {
                return;
            }

            boolean loadedComplete = in.readBoolean();
            int fileCount = in.readInt();
            if (fileCount < 0 || fileCount > 1000000) {
                return;
            }

            Map<String, IndexedWebFile> loadedFiles = new HashMap<String, IndexedWebFile>();
            Map<String, List<JavaScriptDefinition>> loadedFunctions =
                    new HashMap<String, List<JavaScriptDefinition>>();
            Map<String, List<BeanUsage>> loadedUsages =
                    new HashMap<String, List<BeanUsage>>();

            for (int i = 0; i < fileCount; i++) {
                String path = in.readUTF();
                long stamp = in.readLong();

                int functionCount = in.readInt();
                List<JavaScriptDefinition> defs = new ArrayList<JavaScriptDefinition>();
                for (int j = 0; j < functionCount; j++) {
                    defs.add(new JavaScriptDefinition(in.readUTF(), path, in.readInt(), stamp));
                }

                int usageCount = in.readInt();
                List<BeanUsage> usages = new ArrayList<BeanUsage>();
                for (int j = 0; j < usageCount; j++) {
                    usages.add(new BeanUsage(in.readUTF(), path, in.readInt(), stamp));
                }

                IndexedWebFile indexed = new IndexedWebFile(path, stamp, defs, usages);
                loadedFiles.put(path, indexed);
                addIndexed(loadedFunctions, loadedUsages, indexed);
            }

            synchronized (lock) {
                byResourcePath.clear();
                byResourcePath.putAll(loadedFiles);
                functions.clear();
                functions.putAll(loadedFunctions);
                beanUsages.clear();
                beanUsages.putAll(loadedUsages);
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

    private void persistSnapshot() {
        List<IndexedWebFile> snapshot;
        boolean snapshotComplete;

        synchronized (lock) {
            snapshot = new ArrayList<IndexedWebFile>(byResourcePath.values());
            snapshotComplete = completeIndex;
        }

        File parent = indexFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File temp = new File(parent, indexFile.getName() + ".tmp");
        DataOutputStream out = null;

        try {
            out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)));
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeBoolean(snapshotComplete);
            out.writeInt(snapshot.size());

            for (IndexedWebFile indexed : snapshot) {
                out.writeUTF(indexed.resourcePath);
                out.writeLong(indexed.modificationStamp);
                out.writeInt(indexed.functions.size());
                for (JavaScriptDefinition definition : indexed.functions) {
                    out.writeUTF(definition.getFunctionName());
                    out.writeInt(definition.getOffset());
                }
                out.writeInt(indexed.beanUsages.size());
                for (BeanUsage usage : indexed.beanUsages) {
                    out.writeUTF(usage.getBeanName());
                    out.writeInt(usage.getOffset());
                }
            }

            out.flush();
            out.close();
            out = null;

            try {
                Files.move(
                        temp.toPath(),
                        indexFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailed) {
                Files.move(
                        temp.toPath(),
                        indexFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
            if (temp.exists()) {
                temp.delete();
            }
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void clear() {
        synchronized (lock) {
            functions.clear();
            beanUsages.clear();
            byResourcePath.clear();
            completeIndex = false;
        }
    }

    private static final class IndexedWebFile {
        private final String resourcePath;
        private final long modificationStamp;
        private final List<JavaScriptDefinition> functions;
        private final List<BeanUsage> beanUsages;

        private IndexedWebFile(
                String resourcePath,
                long modificationStamp,
                List<JavaScriptDefinition> functions,
                List<BeanUsage> beanUsages) {
            this.resourcePath = resourcePath;
            this.modificationStamp = modificationStamp;
            this.functions = functions;
            this.beanUsages = beanUsages;
        }
    }
}
