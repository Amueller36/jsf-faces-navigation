package de.andre.jsfnavigation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

public final class SmartDeployService {

    private final SmartDeployMappingStore mappingStore;

    private final Map<String, Boolean> pending =
            new ConcurrentHashMap<String, Boolean>();

    private final AtomicBoolean scheduled =
            new AtomicBoolean(false);

    private IResourceChangeListener listener;

    public SmartDeployService(
            SmartDeployMappingStore mappingStore) {

        this.mappingStore = mappingStore;
    }

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
                        IResourceChangeEvent.POST_BUILD);
    }

    public void stop() {
        if (listener != null) {
            ResourcesPlugin.getWorkspace()
                    .removeResourceChangeListener(listener);

            listener = null;
        }

        pending.clear();
    }

    private void collect(IResourceDelta delta) {
        if (delta == null
                || !SmartDeploySettings.isEnabled()) {

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

                            if (!"class".equalsIgnoreCase(
                                    resource.getFileExtension())) {

                                return false;
                            }

                            int flags =
                                    child.getFlags();

                            boolean changed =
                                    child.getKind()
                                            == IResourceDelta.ADDED
                                    || (flags
                                            & IResourceDelta.CONTENT) != 0
                                    || (flags
                                            & IResourceDelta.REPLACED) != 0;

                            if (!changed) {
                                return false;
                            }

                            pending.put(
                                    resource.getFullPath()
                                            .toPortableString(),
                                    Boolean.TRUE);

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
                new Job("Smart WebSphere deploy") {
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
        job.schedule(650L);
    }

    private void processPending() {
        List<SmartDeployClassChange> changes =
                new ArrayList<SmartDeployClassChange>();

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

            SmartDeployClassChange change =
                    SmartDeployOutputResolver.resolve(file);

            if (change != null) {
                changes.add(change);
            }
        }

        if (changes.isEmpty()) {
            return;
        }

        Map<String, List<SmartDeployClassChange>> byOutput =
                new LinkedHashMap<String, List<SmartDeployClassChange>>();

        for (SmartDeployClassChange change : changes) {
            String key = change.mappingKey();

            List<SmartDeployClassChange> list =
                    byOutput.get(key);

            if (list == null) {
                list =
                        new ArrayList<SmartDeployClassChange>();

                byOutput.put(key, list);
            }

            list.add(change);
        }

        List<SmartDeployWsadminRunner.ArchiveOperation> archiveOperations =
                new ArrayList<SmartDeployWsadminRunner.ArchiveOperation>();

        for (Map.Entry<String, List<SmartDeployClassChange>> entry :
                byOutput.entrySet()) {

            List<SmartDeployClassChange> group =
                    entry.getValue();

            SmartDeployTarget target =
                    resolveTarget(
                            entry.getKey(),
                            group.get(0));

            if (target == null) {
                continue;
            }

            try {
                if (target.getKind()
                        == SmartDeployTarget.EXPLODED_WAR_CLASSES) {

                    syncWarClasses(
                            target,
                            group);

                } else {
                    archiveOperations.addAll(
                            archiveOperations(
                                    target,
                                    group));
                }

            } catch (Exception e) {
                WebSphereStatusLine.show(
                        "Smart Deploy failed: "
                        + e.getMessage());
            }
        }

        if (!archiveOperations.isEmpty()) {
            try {
                String output =
                        SmartDeployWsadminRunner.apply(
                                deduplicate(
                                        archiveOperations));

                WebSphereStatusLine.show(
                        "Smart Deploy updated "
                        + archiveOperations.size()
                        + " JAR/application class file(s) through wsadmin.");

                WebSphereLogsView.appendDeployStatus(
                        output);

            } catch (Exception e) {
                WebSphereStatusLine.show(
                        "Smart Deploy wsadmin failed: "
                        + e.getMessage());

                WebSphereLogsView.appendDeployStatus(
                        "Smart Deploy wsadmin failed:\n"
                        + e.getMessage());
            }
        }
    }

    private SmartDeployTarget resolveTarget(
            String mappingKey,
            SmartDeployClassChange sample) {

        SmartDeployTarget mapped =
                mappingStore.get(mappingKey);

        if (valid(mapped)) {
            return mapped;
        }

        if (mapped != null) {
            mappingStore.remove(mappingKey);
        }

        String matchPath =
                matchingClassPath(
                        sample.getRelativeClassPath()
                                .toPortableString());

        List<SmartDeployTarget> candidates =
                SmartDeployModuleScanner
                        .findTargets(matchPath);

        SmartDeployTarget selected =
                SmartDeployTargetChooser.choose(
                        matchPath,
                        candidates);

        if (selected != null) {
            mappingStore.put(
                    mappingKey,
                    selected);

            WebSphereStatusLine.show(
                    "Smart Deploy learned mapping: "
                    + mappingKey
                    + " → "
                    + selected.displayName());
        } else {
            WebSphereStatusLine.show(
                    candidates.isEmpty()
                            ? "Smart Deploy: no deployed module contains "
                                    + matchPath
                            : "Smart Deploy: no target selected for "
                                    + matchPath);
        }

        return selected;
    }

    private static boolean valid(
            SmartDeployTarget target) {

        if (target == null
                || target.getEarRoot() == null
                || !target.getEarRoot()
                        .isDirectory()) {

            return false;
        }

        if (target.getKind()
                == SmartDeployTarget.EXPLODED_WAR_CLASSES) {

            return target.getTarget() != null
                    && target.getTarget()
                            .isDirectory();
        }

        return target.getTarget() != null
                && target.getTarget()
                        .isFile();
    }

    private static void syncWarClasses(
            SmartDeployTarget target,
            List<SmartDeployClassChange> changes)
            throws IOException {

        Map<String, SmartDeployClassChange> representativeByBase =
                new LinkedHashMap<String, SmartDeployClassChange>();

        for (SmartDeployClassChange change : changes) {
            String relative =
                    change.getRelativeClassPath()
                            .toPortableString();

            representativeByBase.put(
                    baseClassPath(relative),
                    change);
        }

        int copied = 0;

        for (SmartDeployClassChange representative :
                representativeByBase.values()) {

            List<LocalClassFile> local =
                    siblings(representative);

            if (local.isEmpty()) {
                continue;
            }

            deleteStaleWarInnerClasses(
                    target,
                    local);

            for (LocalClassFile classFile :
                    local) {

                File destination =
                        new File(
                                target.getTarget(),
                                classFile.relativePath
                                    .replace(
                                            '/',
                                            File.separatorChar));

                File parent =
                        destination.getParentFile();

                if (parent != null
                        && !parent.exists()) {

                    parent.mkdirs();
                }

                Files.copy(
                        classFile.file.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);

                copied++;
            }
        }

        WebSphereStatusLine.show(
                "Smart Deploy copied "
                + copied
                + " class file(s) to "
                + target.displayName());
    }

    private static List<SmartDeployWsadminRunner.ArchiveOperation>
            archiveOperations(
                    SmartDeployTarget target,
                    List<SmartDeployClassChange> changes)
                    throws IOException {

        List<SmartDeployWsadminRunner.ArchiveOperation> result =
                new ArrayList<SmartDeployWsadminRunner.ArchiveOperation>();

        Map<String, SmartDeployClassChange> representativeByBase =
                new LinkedHashMap<String, SmartDeployClassChange>();

        for (SmartDeployClassChange change : changes) {
            String relative =
                    change.getRelativeClassPath()
                            .toPortableString();

            representativeByBase.put(
                    baseClassPath(relative),
                    change);
        }

        for (SmartDeployClassChange representative :
                representativeByBase.values()) {

            List<LocalClassFile> local =
                    siblings(representative);

            Set<String> current =
                    new HashSet<String>();

            for (LocalClassFile classFile : local) {
                current.add(classFile.relativePath);

                boolean exists =
                        archiveContains(
                                target,
                                classFile.relativePath);

                result.add(
                        new SmartDeployWsadminRunner.ArchiveOperation(
                                exists
                                        ? SmartDeployWsadminRunner
                                                .ArchiveOperation.UPDATE
                                        : SmartDeployWsadminRunner
                                                .ArchiveOperation.ADD,
                                classFile.file,
                                target,
                                classFile.relativePath));
            }

            for (String oldInner :
                    archiveInnerClasses(
                            target,
                            baseClassPath(
                                    representative
                                        .getRelativeClassPath()
                                        .toPortableString()))) {

                if (!current.contains(oldInner)) {
                    result.add(
                            new SmartDeployWsadminRunner.ArchiveOperation(
                                    SmartDeployWsadminRunner
                                            .ArchiveOperation.DELETE,
                                    null,
                                    target,
                                    oldInner));
                }
            }
        }

        return result;
    }

    private static List<SmartDeployWsadminRunner.ArchiveOperation>
            deduplicate(
                    List<SmartDeployWsadminRunner.ArchiveOperation> input) {

        Map<String, SmartDeployWsadminRunner.ArchiveOperation> unique =
                new LinkedHashMap<String, SmartDeployWsadminRunner.ArchiveOperation>();

        for (SmartDeployWsadminRunner.ArchiveOperation operation :
                input) {

            String key =
                    operation.getTarget().identity()
                    + "|"
                    + operation.getRelativeClassPath();

            unique.put(key, operation);
        }

        return new ArrayList<SmartDeployWsadminRunner.ArchiveOperation>(
                unique.values());
    }

    private static List<LocalClassFile> siblings(
            SmartDeployClassChange change) {

        List<LocalClassFile> result =
                new ArrayList<LocalClassFile>();

        if (change.getClassFile()
                .getLocation() == null) {

            return result;
        }

        File changed =
                change.getClassFile()
                        .getLocation()
                        .toFile();

        File directory =
                changed.getParentFile();

        if (directory == null
                || !directory.isDirectory()) {

            return result;
        }

        String fileName =
                changed.getName();

        String base =
                baseSimpleName(fileName);

        File[] children =
                directory.listFiles();

        if (children == null) {
            return result;
        }

        String parentRelative =
                change.getRelativeClassPath()
                        .removeLastSegments(1)
                        .toPortableString();

        for (File child : children) {
            String name =
                    child.getName();

            if (!child.isFile()
                    || (!name.equals(base + ".class")
                            && !(name.startsWith(base + "$")
                                    && name.endsWith(".class")))) {

                continue;
            }

            String relative =
                    parentRelative.isEmpty()
                            ? name
                            : parentRelative
                                    + "/"
                                    + name;

            result.add(
                    new LocalClassFile(
                            child,
                            relative));
        }

        return result;
    }

    private static void deleteStaleWarInnerClasses(
            SmartDeployTarget target,
            List<LocalClassFile> local) {

        if (local.isEmpty()) {
            return;
        }

        String sample =
                local.get(0).relativePath;

        String basePath =
                baseClassPath(sample);

        int slash =
                basePath.lastIndexOf('/');

        String parent =
                slash >= 0
                        ? basePath.substring(0, slash)
                        : "";

        String baseFile =
                slash >= 0
                        ? basePath.substring(slash + 1)
                        : basePath;

        String base =
                baseFile.substring(
                        0,
                        baseFile.length()
                                - ".class".length());

        File directory =
                new File(
                        target.getTarget(),
                        parent.replace(
                                '/',
                                File.separatorChar));

        File[] deployed =
                directory.listFiles();

        if (deployed == null) {
            return;
        }

        Set<String> keep =
                new HashSet<String>();

        for (LocalClassFile file : local) {
            keep.add(
                    new File(file.relativePath)
                            .getName());
        }

        for (File file : deployed) {
            String name = file.getName();

            if (file.isFile()
                    && name.startsWith(base + "$")
                    && name.endsWith(".class")
                    && !keep.contains(name)) {

                file.delete();
            }
        }
    }


    private static boolean archiveContains(
            SmartDeployTarget target,
            String relativePath)
            throws IOException {

        ZipFile zip =
                new ZipFile(
                        target.getTarget());

        try {
            return zip.getEntry(
                    relativePath) != null;

        } finally {
            zip.close();
        }
    }

    private static List<String> archiveInnerClasses(
            SmartDeployTarget target,
            String baseClassPath)
            throws IOException {

        List<String> result =
                new ArrayList<String>();

        ZipFile zip =
                new ZipFile(
                        target.getTarget());

        try {
            int slash =
                    baseClassPath.lastIndexOf('/');

            String parent =
                    slash >= 0
                            ? baseClassPath.substring(
                                    0,
                                    slash + 1)
                            : "";

            String file =
                    slash >= 0
                            ? baseClassPath.substring(
                                    slash + 1)
                            : baseClassPath;

            String base =
                    file.substring(
                            0,
                            file.length()
                                    - ".class".length());

            java.util.Enumeration<? extends ZipEntry> entries =
                    zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry =
                        entries.nextElement();

                String name =
                        entry.getName();

                if (name.startsWith(
                        parent + base + "$")
                        && name.endsWith(".class")) {

                    result.add(name);
                }
            }

        } finally {
            zip.close();
        }

        return result;
    }

    private static String matchingClassPath(
            String relative) {

        return baseClassPath(relative);
    }

    private static String baseClassPath(
            String relative) {

        int slash =
                relative.lastIndexOf('/');

        String parent =
                slash >= 0
                        ? relative.substring(
                                0,
                                slash + 1)
                        : "";

        String file =
                slash >= 0
                        ? relative.substring(
                                slash + 1)
                        : relative;

        return parent
                + baseSimpleName(file)
                + ".class";
    }

    private static String baseSimpleName(
            String classFileName) {

        String name =
                classFileName.endsWith(".class")
                        ? classFileName.substring(
                                0,
                                classFileName.length()
                                        - ".class".length())
                        : classFileName;

        int dollar =
                name.indexOf('$');

        return dollar >= 0
                ? name.substring(0, dollar)
                : name;
    }

    private static final class LocalClassFile {
        final File file;
        final String relativePath;

        LocalClassFile(
                File file,
                String relativePath) {

            this.file = file;
            this.relativePath = relativePath;
        }
    }
}
