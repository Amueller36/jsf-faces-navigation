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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.Signature;

public final class FlowExplorerService {

    private static final int MAGIC = 0x464C4F57; // FLOW
    private static final int FORMAT_VERSION = 4;

    private final File stateFile;
    private final Map<String, FlowDefinition> flows =
            new LinkedHashMap<String, FlowDefinition>();

    private String currentFlowName;
    private boolean autoCapture = true;
    private boolean autoTestDiscovery = true;

    public FlowExplorerService(File stateFile) {
        this.stateFile = stateFile;
    }

    public synchronized void start() {
        load();

        if (flows.isEmpty()) {
            FlowDefinition defaultFlow =
                    new FlowDefinition("Current Task");

            flows.put(
                    defaultFlow.getName(),
                    defaultFlow);

            currentFlowName =
                    defaultFlow.getName();
        }

        if (currentFlowName == null
                || !flows.containsKey(currentFlowName)) {

            currentFlowName =
                    flows.keySet().iterator().next();
        }

        if (reclassifyExistingEntries()) {
            persist();
        }
    }

    private boolean reclassifyExistingEntries() {
        boolean changed = false;

        for (FlowDefinition flow :
                flows.values()) {

            List<FlowEntry> snapshot =
                    new ArrayList<FlowEntry>(
                            flow.getEntries());

            for (FlowEntry entry :
                    snapshot) {

                IFile file =
                        resolve(entry);

                if (file == null) {
                    continue;
                }

                String category =
                        FlowCategoryClassifier
                                .classify(file);

                if (!category.equals(
                        entry.getCategory())) {

                    flow.addOrReplace(
                            new FlowEntry(
                                    entry.getResourcePath(),
                                    category,
                                    entry.getAddedAt(),
                                    entry.getImpactDepth(),
                                    entry.getImpactOrigins()));

                    changed = true;
                }
            }
        }

        return changed;
    }


    public synchronized boolean reclassifyCurrentEntries() {
        FlowDefinition flow =
                getCurrentFlow();

        if (flow == null) {
            return false;
        }

        boolean changed = false;

        List<FlowEntry> snapshot =
                new ArrayList<FlowEntry>(
                        flow.getEntries());

        for (FlowEntry entry :
                snapshot) {

            IFile file =
                    resolve(entry);

            if (file == null) {
                continue;
            }

            String category =
                    FlowCategoryClassifier
                            .classify(file);

            if (!category.equals(
                    entry.getCategory())) {

                flow.addOrReplace(
                        new FlowEntry(
                                entry.getResourcePath(),
                                category,
                                entry.getAddedAt(),
                                entry.getImpactDepth(),
                                entry.getImpactOrigins()));

                changed = true;
            }
        }

        if (changed) {
            persist();
        }

        return changed;
    }

    public synchronized void stop() {
        persist();
    }

    public synchronized List<String> getFlowNames() {
        return new ArrayList<String>(flows.keySet());
    }

    public synchronized FlowDefinition getCurrentFlow() {
        return flows.get(currentFlowName);
    }

    public synchronized String getCurrentFlowName() {
        return currentFlowName;
    }

    public synchronized List<FlowEntry> getCurrentEntriesSnapshot() {
        FlowDefinition flow =
                getCurrentFlow();

        return flow == null
                ? Collections
                        .<FlowEntry>emptyList()
                : new ArrayList<FlowEntry>(
                        flow.getEntries());
    }


    public synchronized void setCurrentFlow(
            String name) {

        if (name != null
                && flows.containsKey(name)) {

            currentFlowName = name;
            persist();
        }
    }

    public synchronized FlowDefinition createFlow(
            String requestedName) {

        String name =
                requestedName == null
                        ? ""
                        : requestedName.trim();

        if (name.isEmpty()) {
            return null;
        }

        String unique = name;
        int suffix = 2;

        while (flows.containsKey(unique)) {
            unique =
                    name + " (" + suffix + ")";
            suffix++;
        }

        FlowDefinition flow =
                new FlowDefinition(unique);

        flows.put(unique, flow);
        currentFlowName = unique;
        persist();

        return flow;
    }

    public synchronized boolean renameCurrentFlow(
            String requestedName) {

        FlowDefinition current =
                getCurrentFlow();

        if (current == null
                || requestedName == null) {

            return false;
        }

        String name =
                requestedName.trim();

        if (name.isEmpty()
                || name.equals(current.getName())
                || flows.containsKey(name)) {

            return false;
        }

        String oldName =
                current.getName();

        FlowDefinition replacement =
                new FlowDefinition(name);

        for (FlowEntry entry :
                current.getEntries()) {

            replacement.addOrReplace(entry);
        }

        LinkedHashMap<String, FlowDefinition> reordered =
                new LinkedHashMap<String, FlowDefinition>();

        for (Map.Entry<String, FlowDefinition> entry :
                flows.entrySet()) {

            if (entry.getKey().equals(
                    current.getName())) {

                reordered.put(name, replacement);
            } else {
                reordered.put(
                        entry.getKey(),
                        entry.getValue());
            }
        }

        flows.clear();
        flows.putAll(reordered);
        currentFlowName = name;
        persist();

        FlowTestResultStore results =
                Activator.getFlowTestResultStore();

        if (results != null) {
            results.rename(
                    oldName,
                    name);
        }

        return true;
    }

    public synchronized boolean deleteCurrentFlow() {
        if (flows.size() <= 1) {
            return false;
        }

        String deletedName =
                currentFlowName;

        flows.remove(currentFlowName);
        currentFlowName =
                flows.keySet().iterator().next();

        persist();

        FlowTestResultStore results =
                Activator.getFlowTestResultStore();

        if (results != null) {
            results.delete(
                    deletedName);
        }

        return true;
    }

    public synchronized void addFile(
            IFile file) {

        if (file == null
                || !file.exists()) {

            return;
        }

        FlowDefinition flow =
                getCurrentFlow();

        if (flow == null) {
            return;
        }

        String resourcePath =
                file.getFullPath()
                        .toPortableString();

        if (flow.contains(resourcePath)) {
            String category =
                    FlowCategoryClassifier
                            .classify(
                                    file);

            for (FlowEntry existing :
                    flow.getEntries()) {

                if (!resourcePath.equals(
                        existing.getResourcePath())) {

                    continue;
                }

                if (!category.equals(
                        existing.getCategory())) {

                    flow.addOrReplace(
                            new FlowEntry(
                                    existing.getResourcePath(),
                                    category,
                                    existing.getAddedAt(),
                                    existing.getImpactDepth(),
                                    existing.getImpactOrigins()));

                    persist();
                }

                return;
            }

            return;
        }

        flow.addOrReplace(
                new FlowEntry(
                        resourcePath,
                        FlowCategoryClassifier.classify(file),
                        System.currentTimeMillis()));

        persist();
    }

    public synchronized void addImpactedTest(
            IFile file,
            IMethod changedMethod,
            int callerDepth) {

        if (file == null
                || !file.exists()
                || changedMethod == null
                || !changedMethod.exists()) {

            return;
        }

        IFile sourceFile =
                changedMethod.getResource()
                        instanceof IFile
                        ? (IFile)
                                changedMethod.getResource()
                        : null;

        if (sourceFile == null
                || !sourceFile.exists()) {

            return;
        }

        FlowDefinition flow =
                getCurrentFlow();

        if (flow == null) {
            return;
        }

        String resourcePath =
                file.getFullPath()
                        .toPortableString();

        FlowEntry existing = null;

        for (FlowEntry entry :
                flow.getEntries()) {

            if (resourcePath.equals(
                    entry.getResourcePath())) {

                existing = entry;
                break;
            }
        }

        List<FlowImpactOrigin> origins =
                new ArrayList<FlowImpactOrigin>();

        int legacyDepth = 0;
        long addedAt =
                System.currentTimeMillis();

        if (existing != null) {
            origins.addAll(
                    existing.getImpactOrigins());
            legacyDepth =
                    existing.getImpactDepth();
            addedAt =
                    existing.getAddedAt();
        }

        FlowImpactOrigin candidate =
                new FlowImpactOrigin(
                        sourceFile.getFullPath()
                                .toPortableString(),
                        changedMethod
                                .getHandleIdentifier(),
                        methodLabel(
                                changedMethod),
                        callerDepth);

        boolean matched = false;

        for (int i = 0;
                i < origins.size();
                i++) {

            FlowImpactOrigin current =
                    origins.get(i);

            if (!current.getIdentity()
                    .equals(
                            candidate.getIdentity())) {

                continue;
            }

            origins.set(
                    i,
                    new FlowImpactOrigin(
                            candidate.getSourceResourcePath(),
                            candidate.getMethodHandleIdentifier(),
                            candidate.getMethodLabel(),
                            Math.min(
                                    current.getDepth(),
                                    candidate.getDepth())));

            matched = true;
            break;
        }

        if (!matched) {
            origins.add(candidate);
        }

        flow.addOrReplace(
                new FlowEntry(
                        resourcePath,
                        FlowCategoryClassifier.TEST,
                        addedAt,
                        legacyDepth,
                        origins));

        persist();
    }

    private static String methodLabel(
            IMethod method) {

        StringBuilder label =
                new StringBuilder(
                        method.getElementName())
                        .append('(');

        String[] parameters =
                method.getParameterTypes();

        for (int i = 0;
                i < parameters.length;
                i++) {

            if (i > 0) {
                label.append(", ");
            }

            label.append(
                    Signature.toString(
                            parameters[i]));
        }

        return label.append(')')
                .toString();
    }

    public synchronized void removeFile(
            String resourcePath) {

        FlowDefinition flow =
                getCurrentFlow();

        if (flow != null
                && resourcePath != null) {

            flow.remove(resourcePath);
            persist();
        }
    }

    public synchronized void clearCurrentFlow() {
        FlowDefinition flow =
                getCurrentFlow();

        if (flow != null) {
            flow.clear();
            persist();
        }
    }

    public synchronized boolean isAutoCapture() {
        return autoCapture;
    }

    public synchronized void setAutoCapture(
            boolean enabled) {

        autoCapture = enabled;
        persist();
    }

    public synchronized boolean isAutoTestDiscovery() {
        return autoTestDiscovery;
    }

    public synchronized void setAutoTestDiscovery(
            boolean enabled) {

        autoTestDiscovery = enabled;
        persist();
    }

    public synchronized boolean containsFile(
            IFile file) {

        if (file == null) {
            return false;
        }

        FlowDefinition flow =
                getCurrentFlow();

        return flow != null
                && flow.contains(
                        file.getFullPath()
                                .toPortableString());
    }

    public synchronized List<FlowEntry> entriesForCategory(
            String category) {

        FlowDefinition flow =
                getCurrentFlow();

        if (flow == null) {
            return Collections.emptyList();
        }

        List<FlowEntry> result =
                new ArrayList<FlowEntry>();

        for (FlowEntry entry :
                flow.getEntries()) {

            if (category.equals(
                    entry.getCategory())) {

                result.add(entry);
            }
        }

        Collections.sort(
                result,
                new java.util.Comparator<FlowEntry>() {
                    @Override
                    public int compare(
                            FlowEntry left,
                            FlowEntry right) {

                        return left.getResourcePath()
                                .compareToIgnoreCase(
                                        right.getResourcePath());
                    }
                });

        return result;
    }

    public IFile resolve(FlowEntry entry) {
        if (entry == null) {
            return null;
        }

        IFile file =
                ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .getFile(
                                new Path(
                                        entry.getResourcePath()));

        return file.exists()
                ? file
                : null;
    }

    private synchronized void load() {
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

            if (in.readInt() != MAGIC) {
                return;
            }

            int version =
                    in.readInt();

            if (version != 1
                    && version != 2
                    && version != 3
                    && version != FORMAT_VERSION) {

                return;
            }

            currentFlowName =
                    nullableRead(in);

            autoCapture =
                    in.readBoolean();

            autoTestDiscovery =
                    version >= 2
                            ? in.readBoolean()
                            : true;

            int flowCount =
                    in.readInt();

            for (int i = 0;
                    i < flowCount;
                    i++) {

                String name =
                        in.readUTF();

                FlowDefinition flow =
                        new FlowDefinition(name);

                int entryCount =
                        in.readInt();

                for (int j = 0;
                        j < entryCount;
                        j++) {

                    String resourcePath =
                            in.readUTF();

                    String category =
                            in.readUTF();

                    long addedAt =
                            in.readLong();

                    int impactDepth =
                            version >= 3
                                    ? in.readInt()
                                    : 0;

                    List<FlowImpactOrigin> origins =
                            new ArrayList<FlowImpactOrigin>();

                    if (version >= 4) {
                        int originCount =
                                in.readInt();

                        for (int k = 0;
                                k < originCount;
                                k++) {

                            origins.add(
                                    new FlowImpactOrigin(
                                            in.readUTF(),
                                            in.readUTF(),
                                            in.readUTF(),
                                            in.readInt()));
                        }
                    }

                    flow.addOrReplace(
                            new FlowEntry(
                                    resourcePath,
                                    category,
                                    addedAt,
                                    impactDepth,
                                    origins));
                }

                flows.put(name, flow);
            }

        } catch (EOFException e) {
            flows.clear();
            currentFlowName = null;

        } catch (IOException e) {
            flows.clear();
            currentFlowName = null;

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private synchronized void persist() {
        File parent =
                stateFile.getParentFile();

        if (parent != null
                && !parent.exists()) {

            parent.mkdirs();
        }

        File tmp =
                new File(
                        parent,
                        stateFile.getName() + ".tmp");

        DataOutputStream out = null;

        try {
            out =
                    new DataOutputStream(
                            new BufferedOutputStream(
                                    new FileOutputStream(tmp)));

            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            nullableWrite(
                    out,
                    currentFlowName);

            out.writeBoolean(autoCapture);
            out.writeBoolean(
                    autoTestDiscovery);
            out.writeInt(flows.size());

            for (FlowDefinition flow :
                    flows.values()) {

                out.writeUTF(flow.getName());
                out.writeInt(
                        flow.getEntries().size());

                for (FlowEntry entry :
                        flow.getEntries()) {

                    out.writeUTF(
                            entry.getResourcePath());

                    out.writeUTF(
                            entry.getCategory());

                    out.writeLong(
                            entry.getAddedAt());

                    out.writeInt(
                            entry.getImpactDepth());

                    out.writeInt(
                            entry.getImpactOrigins()
                                    .size());

                    for (FlowImpactOrigin origin :
                            entry.getImpactOrigins()) {

                        out.writeUTF(
                                origin.getSourceResourcePath());
                        out.writeUTF(
                                origin.getMethodHandleIdentifier());
                        out.writeUTF(
                                origin.getMethodLabel());
                        out.writeInt(
                                origin.getDepth());
                    }
                }
            }

            out.flush();
            out.close();
            out = null;

            try {
                Files.move(
                        tmp.toPath(),
                        stateFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

            } catch (IOException atomicFailed) {
                Files.move(
                        tmp.toPath(),
                        stateFile.toPath(),
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
