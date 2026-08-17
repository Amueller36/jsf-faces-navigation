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

public final class FlowExplorerService {

    private static final int MAGIC = 0x464C4F57; // FLOW
    private static final int FORMAT_VERSION = 1;

    private final File stateFile;
    private final Map<String, FlowDefinition> flows =
            new LinkedHashMap<String, FlowDefinition>();

    private String currentFlowName;
    private boolean autoCapture = true;

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

        return true;
    }

    public synchronized boolean deleteCurrentFlow() {
        if (flows.size() <= 1) {
            return false;
        }

        flows.remove(currentFlowName);
        currentFlowName =
                flows.keySet().iterator().next();

        persist();
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
            return;
        }

        flow.addOrReplace(
                new FlowEntry(
                        resourcePath,
                        FlowCategoryClassifier.classify(file),
                        System.currentTimeMillis()));

        persist();
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

            if (in.readInt() != MAGIC
                    || in.readInt()
                            != FORMAT_VERSION) {

                return;
            }

            currentFlowName =
                    nullableRead(in);

            autoCapture =
                    in.readBoolean();

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

                    flow.addOrReplace(
                            new FlowEntry(
                                    in.readUTF(),
                                    in.readUTF(),
                                    in.readLong()));
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
