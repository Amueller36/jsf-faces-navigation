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
import java.util.LinkedHashMap;
import java.util.Map;

public final class SmartDeployMappingStore {

    private static final int MAGIC = 0x53444D50; // SDMP
    private static final int FORMAT_VERSION = 1;

    private final File file;
    private final Map<String, SmartDeployTarget> mappings =
            new LinkedHashMap<String, SmartDeployTarget>();

    public SmartDeployMappingStore(File file) {
        this.file = file;
    }

    public synchronized void start() {
        load();
    }

    public synchronized void stop() {
        persist();
    }

    public synchronized SmartDeployTarget get(
            String outputRoot) {

        return mappings.get(outputRoot);
    }

    public synchronized void put(
            String outputRoot,
            SmartDeployTarget target) {

        if (outputRoot == null
                || target == null) {

            return;
        }

        mappings.put(outputRoot, target);
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
        if (!file.isFile()) {
            return;
        }

        DataInputStream in = null;

        try {
            in =
                    new DataInputStream(
                            new BufferedInputStream(
                                    new FileInputStream(file)));

            if (in.readInt() != MAGIC
                    || in.readInt()
                            != FORMAT_VERSION) {

                return;
            }

            int count = in.readInt();

            for (int i = 0; i < count; i++) {
                String key = in.readUTF();
                int kind = in.readInt();
                String app = in.readUTF();
                File ear =
                        new File(in.readUTF());

                File target =
                        new File(in.readUTF());

                String contentUri =
                        nullableRead(in);

                mappings.put(
                        key,
                        new SmartDeployTarget(
                                kind,
                                app,
                                ear,
                                target,
                                contentUri));
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

    private void persist() {
        File parent = file.getParentFile();

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

            for (Map.Entry<String, SmartDeployTarget> entry :
                    mappings.entrySet()) {

                SmartDeployTarget target =
                        entry.getValue();

                out.writeUTF(entry.getKey());
                out.writeInt(target.getKind());
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
