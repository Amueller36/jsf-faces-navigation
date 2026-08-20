package de.andre.jsfnavigation;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

public final class XsdIndexService {

    private static final int MAGIC =
            0x58534449; // XSDI

    private static final int VERSION = 1;

    private static final int MAX_FILE_BYTES =
            8 * 1024 * 1024;

    private static final Pattern TARGET_NAMESPACE =
            Pattern.compile(
                    "\\btargetNamespace\\s*=\\s*(['\"])(.*?)\\1",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.DOTALL);

    private static final Pattern DEFINITION =
            Pattern.compile(
                    "<\\s*(?:[A-Za-z_][A-Za-z0-9_.-]*:)?"
                    + "(complexType|simpleType|element|attribute|group|attributeGroup)"
                    + "\\b[^>]*?\\bname\\s*=\\s*(['\"])(.*?)\\2",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.DOTALL);

    private final Object lock =
            new Object();

    private final File stateFile;

    private final Map<String, XsdIndexedFile>
            byResourcePath =
                    new LinkedHashMap<String, XsdIndexedFile>();

    private final Map<String, List<XsdDefinition>>
            byQualifiedName =
                    new LinkedHashMap<String, List<XsdDefinition>>();

    private final Map<String, List<XsdDefinition>>
            byLocalName =
                    new LinkedHashMap<String, List<XsdDefinition>>();

    private final AtomicBoolean fullBuildAttempted =
            new AtomicBoolean(false);

    private volatile boolean completeIndex;

    private IResourceChangeListener listener;
    private Job persistJob;

    public XsdIndexService(
            File stateFile) {

        this.stateFile = stateFile;
    }

    public void start() {
        loadSnapshot();
        installListener();
        scheduleValidationBuild();
    }

    public void stop() {
        if (listener != null) {
            ResourcesPlugin.getWorkspace()
                    .removeResourceChangeListener(
                            listener);

            listener = null;
        }

        if (persistJob != null) {
            persistJob.cancel();
            persistJob = null;
        }

        persistSnapshot();
    }

    public List<XsdDefinition> resolve(
            String namespace,
            String localName) {

        if (localName == null
                || localName.isEmpty()) {

            return Collections.emptyList();
        }

        ensureBuiltIfNeeded();

        synchronized (lock) {
            List<XsdDefinition> exact =
                    byQualifiedName.get(
                            key(
                                    namespace,
                                    localName));

            if (exact != null
                    && !exact.isEmpty()) {

                return new ArrayList<XsdDefinition>(
                        exact);
            }

            List<XsdDefinition> fallback =
                    byLocalName.get(
                            localName);

            return fallback == null
                    ? Collections
                            .<XsdDefinition>emptyList()
                    : new ArrayList<XsdDefinition>(
                            fallback);
        }
    }

    public List<XsdDefinition> definitionsForFile(
            IFile file) {

        if (file == null) {
            return Collections.emptyList();
        }

        ensureBuiltIfNeeded();

        synchronized (lock) {
            XsdIndexedFile indexed =
                    byResourcePath.get(
                            file.getFullPath()
                                    .toPortableString());

            return indexed == null
                    ? Collections
                            .<XsdDefinition>emptyList()
                    : new ArrayList<XsdDefinition>(
                            indexed.getDefinitions());
        }
    }

    public IFile fileFor(
            XsdDefinition definition) {

        if (definition == null) {
            return null;
        }

        IFile file =
                ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .getFile(
                                new org.eclipse.core.runtime.Path(
                                        definition
                                                .getResourcePath()));

        return file.exists()
                ? file
                : null;
    }

    public String targetNamespace(
            IFile file) {

        if (file == null) {
            return "";
        }

        ensureBuiltIfNeeded();

        synchronized (lock) {
            XsdIndexedFile indexed =
                    byResourcePath.get(
                            file.getFullPath()
                                    .toPortableString());

            return indexed == null
                    ? ""
                    : indexed
                            .getTargetNamespace();
        }
    }

    private void installListener() {
        listener =
                new IResourceChangeListener() {
                    @Override
                    public void resourceChanged(
                            IResourceChangeEvent event) {

                        IResourceDelta delta =
                                event.getDelta();

                        if (delta == null) {
                            return;
                        }

                        try {
                            delta.accept(
                                    new IResourceDeltaVisitor() {
                                        @Override
                                        public boolean visit(
                                                IResourceDelta current)
                                                throws CoreException {

                                            IResource resource =
                                                    current.getResource();

                                            if (resource.getType()
                                                    != IResource.FILE) {

                                                return true;
                                            }

                                            if (!isXsd(
                                                    resource.getName())) {

                                                return false;
                                            }

                                            IFile file =
                                                    (IFile)
                                                            resource;

                                            if (current.getKind()
                                                    == IResourceDelta.REMOVED) {

                                                remove(
                                                        file.getFullPath()
                                                                .toPortableString());

                                            } else if ((current.getKind()
                                                    == IResourceDelta.ADDED)
                                                    || ((current.getFlags()
                                                            & IResourceDelta.CONTENT)
                                                            != 0)) {

                                                index(
                                                        file);
                                            }

                                            schedulePersist();
                                            return false;
                                        }
                                    });

                        } catch (CoreException e) {
                            // Best effort incremental index.
                        }
                    }
                };

        ResourcesPlugin.getWorkspace()
                .addResourceChangeListener(
                        listener,
                        IResourceChangeEvent.POST_CHANGE);
    }

    private void scheduleValidationBuild() {
        Job job =
                new Job(
                        "Validate XSD navigation index") {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        rebuildAll(
                                monitor);

                        return monitor.isCanceled()
                                ? Status.CANCEL_STATUS
                                : Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.schedule(
                900L);
    }

    private void ensureBuiltIfNeeded() {
        if (completeIndex) {
            return;
        }

        if (fullBuildAttempted
                .compareAndSet(
                        false,
                        true)) {

            rebuildAll(
                    null);
        }
    }

    private void rebuildAll(
            final IProgressMonitor monitor) {

        final Map<String, XsdIndexedFile>
                rebuilt =
                        new LinkedHashMap<String, XsdIndexedFile>();

        IProject[] projects =
                ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .getProjects();

        for (IProject project :
                projects) {

            if (monitor != null
                    && monitor.isCanceled()) {

                return;
            }

            if (!project.isAccessible()) {
                continue;
            }

            try {
                project.accept(
                        new IResourceVisitor() {
                            @Override
                            public boolean visit(
                                    IResource resource)
                                    throws CoreException {

                                if (monitor != null
                                        && monitor.isCanceled()) {

                                    return false;
                                }

                                if (resource.getType()
                                        == IResource.FILE
                                        && isXsd(
                                                resource.getName())) {

                                    IFile file =
                                            (IFile)
                                                    resource;

                                    XsdIndexedFile indexed =
                                            parse(
                                                    file);

                                    if (indexed != null) {
                                        rebuilt.put(
                                                indexed
                                                        .getResourcePath(),
                                                indexed);
                                    }

                                    return false;
                                }

                                return true;
                            }
                        });

            } catch (CoreException e) {
                // Continue with remaining projects.
            }
        }

        synchronized (lock) {
            byResourcePath.clear();
            byResourcePath.putAll(
                    rebuilt);

            rebuildLookupMapsLocked();

            completeIndex = true;
            fullBuildAttempted.set(
                    true);
        }

        persistSnapshot();
    }

    private void index(
            IFile file) {

        if (file == null
                || !file.exists()) {

            return;
        }

        XsdIndexedFile indexed =
                parse(
                        file);

        if (indexed == null) {
            return;
        }

        synchronized (lock) {
            byResourcePath.put(
                    indexed.getResourcePath(),
                    indexed);

            rebuildLookupMapsLocked();
        }
    }

    private void remove(
            String resourcePath) {

        synchronized (lock) {
            if (byResourcePath.remove(
                    resourcePath) != null) {

                rebuildLookupMapsLocked();
            }
        }
    }

    private void rebuildLookupMapsLocked() {
        byQualifiedName.clear();
        byLocalName.clear();

        for (XsdIndexedFile file :
                byResourcePath.values()) {

            for (XsdDefinition definition :
                    file.getDefinitions()) {

                add(
                        byQualifiedName,
                        key(
                                definition.getNamespace(),
                                definition.getName()),
                        definition);

                add(
                        byLocalName,
                        definition.getName(),
                        definition);
            }
        }

        Comparator<XsdDefinition> comparator =
                new Comparator<XsdDefinition>() {
                    @Override
                    public int compare(
                            XsdDefinition left,
                            XsdDefinition right) {

                        return left.getResourcePath()
                                .compareToIgnoreCase(
                                        right.getResourcePath());
                    }
                };

        for (List<XsdDefinition> list :
                byQualifiedName.values()) {

            Collections.sort(
                    list,
                    comparator);
        }

        for (List<XsdDefinition> list :
                byLocalName.values()) {

            Collections.sort(
                    list,
                    comparator);
        }
    }

    private static void add(
            Map<String, List<XsdDefinition>> map,
            String key,
            XsdDefinition value) {

        List<XsdDefinition> list =
                map.get(
                        key);

        if (list == null) {
            list =
                    new ArrayList<XsdDefinition>();

            map.put(
                    key,
                    list);
        }

        list.add(
                value);
    }

    private static XsdIndexedFile parse(
            IFile file) {

        String text =
                read(
                        file);

        if (text == null) {
            return null;
        }

        Matcher namespaceMatcher =
                TARGET_NAMESPACE.matcher(
                        text);

        String namespace =
                namespaceMatcher.find()
                        ? namespaceMatcher
                                .group(2)
                                .trim()
                        : "";

        List<XsdDefinition> definitions =
                new ArrayList<XsdDefinition>();

        Matcher matcher =
                DEFINITION.matcher(
                        text);

        String path =
                file.getFullPath()
                        .toPortableString();

        while (matcher.find()) {
            String kind =
                    matcher.group(1);

            String name =
                    matcher.group(3)
                            .trim();

            if (name.isEmpty()) {
                continue;
            }

            definitions.add(
                    new XsdDefinition(
                            path,
                            namespace,
                            name,
                            kind,
                            matcher.start(3)));
        }

        return new XsdIndexedFile(
                path,
                file.getModificationStamp(),
                namespace,
                definitions);
    }

    private static String read(
            IFile file) {

        InputStream in = null;

        try {
            if (file.getLocation() != null
                    && file.getLocation()
                            .toFile()
                            .length()
                            > MAX_FILE_BYTES) {

                return null;
            }

            in =
                    file.getContents();

            ByteArrayOutputStream out =
                    new ByteArrayOutputStream();

            byte[] buffer =
                    new byte[8192];

            int total = 0;
            int count;

            while ((count =
                    in.read(
                            buffer)) >= 0) {

                total += count;

                if (total > MAX_FILE_BYTES) {
                    return null;
                }

                out.write(
                        buffer,
                        0,
                        count);
            }

            return new String(
                    out.toByteArray(),
                    Charset.forName(
                            file.getCharset()));

        } catch (Exception e) {
            return null;

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void loadSnapshot() {
        if (!stateFile.isFile()) {
            return;
        }

        DataInputStream in = null;

        try {
            in =
                    new DataInputStream(
                            new BufferedInputStream(
                                    new FileInputStream(
                                            stateFile)));

            if (in.readInt()
                    != MAGIC
                    || in.readInt()
                            != VERSION) {

                return;
            }

            int fileCount =
                    in.readInt();

            if (fileCount < 0
                    || fileCount > 100000) {

                return;
            }

            Map<String, XsdIndexedFile>
                    loaded =
                            new LinkedHashMap<String, XsdIndexedFile>();

            for (int i = 0;
                    i < fileCount;
                    i++) {

                String path =
                        in.readUTF();

                long stamp =
                        in.readLong();

                String namespace =
                        in.readUTF();

                int definitionCount =
                        in.readInt();

                if (definitionCount < 0
                        || definitionCount > 100000) {

                    throw new EOFException(
                            "Invalid XSD definition count.");
                }

                List<XsdDefinition> definitions =
                        new ArrayList<XsdDefinition>(
                                definitionCount);

                for (int j = 0;
                        j < definitionCount;
                        j++) {

                    definitions.add(
                            new XsdDefinition(
                                    path,
                                    in.readUTF(),
                                    in.readUTF(),
                                    in.readUTF(),
                                    in.readInt()));
                }

                loaded.put(
                        path,
                        new XsdIndexedFile(
                                path,
                                stamp,
                                namespace,
                                definitions));
            }

            synchronized (lock) {
                byResourcePath.clear();
                byResourcePath.putAll(
                        loaded);

                rebuildLookupMapsLocked();

                /*
                 * Snapshot can serve navigation immediately. A background
                 * validation rebuild checks files changed while Eclipse was
                 * closed.
                 */
                completeIndex =
                        !loaded.isEmpty();
            }

        } catch (Exception e) {
            synchronized (lock) {
                byResourcePath.clear();
                byQualifiedName.clear();
                byLocalName.clear();
                completeIndex = false;
            }

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void schedulePersist() {
        synchronized (lock) {
            if (persistJob != null) {
                persistJob.cancel();
            }

            persistJob =
                    new Job(
                            "Persist XSD navigation index") {

                        @Override
                        protected IStatus run(
                                IProgressMonitor monitor) {

                            persistSnapshot();

                            synchronized (lock) {
                                persistJob = null;
                            }

                            return Status.OK_STATUS;
                        }
                    };

            persistJob.setSystem(
                    true);

            persistJob.schedule(
                    650L);
        }
    }

    private void persistSnapshot() {
        File parent =
                stateFile.getParentFile();

        if (parent != null
                && !parent.exists()) {

            parent.mkdirs();
        }

        List<XsdIndexedFile> snapshot;

        synchronized (lock) {
            snapshot =
                    new ArrayList<XsdIndexedFile>(
                            byResourcePath
                                    .values());
        }

        File tmp =
                new File(
                        parent,
                        stateFile.getName()
                                + ".tmp");

        DataOutputStream out = null;

        try {
            out =
                    new DataOutputStream(
                            new BufferedOutputStream(
                                    new FileOutputStream(
                                            tmp)));

            out.writeInt(
                    MAGIC);

            out.writeInt(
                    VERSION);

            out.writeInt(
                    snapshot.size());

            for (XsdIndexedFile file :
                    snapshot) {

                out.writeUTF(
                        file.getResourcePath());

                out.writeLong(
                        file.getModificationStamp());

                out.writeUTF(
                        file.getTargetNamespace());

                out.writeInt(
                        file.getDefinitions()
                                .size());

                for (XsdDefinition definition :
                        file.getDefinitions()) {

                    out.writeUTF(
                            definition.getNamespace());

                    out.writeUTF(
                            definition.getName());

                    out.writeUTF(
                            definition.getKind());

                    out.writeInt(
                            definition.getOffset());
                }
            }

            out.flush();
            out.close();
            out = null;

            try {
                Files.move(
                        tmp.toPath(),
                        stateFile.toPath(),
                        StandardCopyOption
                                .REPLACE_EXISTING,
                        StandardCopyOption
                                .ATOMIC_MOVE);

            } catch (Exception atomicFailed) {
                Files.move(
                        tmp.toPath(),
                        stateFile.toPath(),
                        StandardCopyOption
                                .REPLACE_EXISTING);
            }

        } catch (Exception e) {
            tmp.delete();

        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean isXsd(
            String name) {

        return name != null
                && name.toLowerCase()
                        .endsWith(
                                ".xsd");
    }

    private static String key(
            String namespace,
            String localName) {

        return (namespace == null
                ? ""
                : namespace)
                + '\u0000'
                + (localName == null
                        ? ""
                        : localName);
    }
}
