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

public final class SmartDeployMappingStore {

    private static final int MAGIC = 0x53444D50; // SDMP
    private static final int FORMAT_VERSION = 2;

    private final File file;

    private final Map<String, List<SmartDeployTarget>> mappings =
            new LinkedHashMap<String, List<SmartDeployTarget>>();

    public SmartDeployMappingStore(File file) {
        this.file = file;
    }

    public synchronized void start() {
        load();
    }

    public synchronized void stop() {
        persist();
    }

    /**
     * Legacy/single-target accessor used by Java/Class Smart Deploy.
     * Web-resource mappings may contain multiple targets; callers that need
     * every target must use {@link #getAll(String)}.
     */
    public synchronized SmartDeployTarget get(
            String outputRoot) {

        List<SmartDeployTarget> targets =
                mappings.get(
                        outputRoot);

        return targets == null
                || targets.isEmpty()
                        ? null
                        : targets.get(0);
    }

    public synchronized List<SmartDeployTarget> getAll(
            String outputRoot) {

        List<SmartDeployTarget> targets =
                mappings.get(
                        outputRoot);

        if (targets == null
                || targets.isEmpty()) {

            return Collections.emptyList();
        }

        return new ArrayList<SmartDeployTarget>(
                targets);
    }

    public synchronized void put(
            String outputRoot,
            SmartDeployTarget target) {

        if (outputRoot == null
                || target == null) {

            return;
        }

        List<SmartDeployTarget> singleton =
                new ArrayList<SmartDeployTarget>();

        singleton.add(
                target);

        mappings.put(
                outputRoot,
                singleton);

        persist();
    }

    public synchronized void putAll(
            String outputRoot,
            List<SmartDeployTarget> targets) {

        if (outputRoot == null) {
            return;
        }

        List<SmartDeployTarget> normalized =
                normalize(
                        targets);

        if (normalized.isEmpty()) {
            mappings.remove(
                    outputRoot);
        } else {
            mappings.put(
                    outputRoot,
                    normalized);
        }

        persist();
    }

    public synchronized void remove(
            String outputRoot) {

        if (outputRoot != null) {
            mappings.remove(outputRoot);
            persist();
        }
    }

    public synchronized void clear() {
        mappings.clear();
        persist();
    }

    private void load() {
        mappings.clear();

        if (!file.isFile()) {
            return;
        }

        DataInputStream in = null;

        try {
            in =
                    new DataInputStream(
                            new BufferedInputStream(
                                    new FileInputStream(file)));

            if (in.readInt()
                    != MAGIC) {

                return;
            }

            int version =
                    in.readInt();

            if (version == 1) {
                loadVersion1(in);
                return;
            }

            if (version != FORMAT_VERSION) {
                return;
            }

            int count =
                    in.readInt();

            for (int i = 0;
                    i < count;
                    i++) {

                String key =
                        in.readUTF();

                int targetCount =
                        in.readInt();

                List<SmartDeployTarget> targets =
                        new ArrayList<SmartDeployTarget>();

                for (int j = 0;
                        j < targetCount;
                        j++) {

                    targets.add(
                            readTarget(
                                    in));
                }

                List<SmartDeployTarget> normalized =
                        normalize(
                                targets);

                if (!normalized.isEmpty()) {
                    mappings.put(
                            key,
                            normalized);
                }
            }

        } catch (EOFException e) {
            mappings.clear();

        } catch (IOException e) {
            mappings.clear();

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void loadVersion1(
            DataInputStream in)
            throws IOException {

        int count =
                in.readInt();

        for (int i = 0;
                i < count;
                i++) {

            String key =
                    in.readUTF();

            SmartDeployTarget target =
                    readTarget(
                            in);

            /*
             * Version 1 could remember only one web WAR. Reusing that WEB
             * entry would silently keep shared XHTML resources single-target
             * forever. Force only WEB mappings to be rediscovered once after
             * the v2 upgrade; Java/Class deploy mappings remain intact.
             */
            if (!key.startsWith(
                    "WEB|")) {

                List<SmartDeployTarget> singleton =
                        new ArrayList<SmartDeployTarget>();

                singleton.add(
                        target);

                mappings.put(
                        key,
                        singleton);
            }
        }

        /*
         * Persist lazily on the next mutation/stop. Existing installations
         * therefore migrate without losing their learned target.
         */
    }

    private void persist() {
        File parent =
                file.getParentFile();

        if (parent != null
                && !parent.exists()) {

            parent.mkdirs();
        }

        File tmp =
                new File(
                        parent,
                        file.getName() + ".tmp");

        DataOutputStream out = null;

        try {
            out =
                    new DataOutputStream(
                            new BufferedOutputStream(
                                    new FileOutputStream(tmp)));

            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeInt(mappings.size());

            for (Map.Entry<String, List<SmartDeployTarget>> entry :
                    mappings.entrySet()) {

                List<SmartDeployTarget> targets =
                        normalize(
                                entry.getValue());

                out.writeUTF(
                        entry.getKey());

                out.writeInt(
                        targets.size());

                for (SmartDeployTarget target :
                        targets) {

                    writeTarget(
                            out,
                            target);
                }
            }

            out.flush();
            out.close();
            out = null;

            try {
                Files.move(
                        tmp.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

            } catch (IOException atomicFailed) {
                Files.move(
                        tmp.toPath(),
                        file.toPath(),
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

    private static List<SmartDeployTarget> normalize(
            List<SmartDeployTarget> targets) {

        List<SmartDeployTarget> result =
                new ArrayList<SmartDeployTarget>();

        if (targets == null) {
            return result;
        }

        for (SmartDeployTarget target :
                targets) {

            if (target == null) {
                continue;
            }

            boolean duplicate =
                    false;

            for (SmartDeployTarget existing :
                    result) {

                if (existing.identity()
                        .equals(
                                target.identity())) {

                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                result.add(
                        target);
            }
        }

        return result;
    }

    private static void writeTarget(
            DataOutputStream out,
            SmartDeployTarget target)
            throws IOException {

        out.writeInt(
                target.getKind());

        out.writeUTF(
                target.getApplicationNameHint());

        out.writeUTF(
                target.getEarRoot()
                        .getAbsolutePath());

        out.writeUTF(
                target.getTarget()
                        .getAbsolutePath());

        nullableWrite(
                out,
                target.getContentUriPrefix());
    }

    private static SmartDeployTarget readTarget(
            DataInputStream in)
            throws IOException {

        int kind =
                in.readInt();

        String app =
                in.readUTF();

        File ear =
                new File(
                        in.readUTF());

        File target =
                new File(
                        in.readUTF());

        String contentUri =
                nullableRead(
                        in);

        return new SmartDeployTarget(
                kind,
                app,
                ear,
                target,
                contentUri);
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
