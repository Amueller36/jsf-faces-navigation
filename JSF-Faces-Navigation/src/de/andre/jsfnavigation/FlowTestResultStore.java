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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlowTestResultStore {

    private static final int MAGIC =
            0x46545253; // FTRS

    private static final int VERSION = 1;

    /*
     * Corrupt state must never be allowed to allocate arbitrary amounts of
     * memory. Individual stored strings (normally stack traces) are capped
     * while reading at 16 MiB.
     */
    private static final int MAX_STRING_BYTES =
            16 * 1024 * 1024;

    private static final Charset UTF8 =
            Charset.forName("UTF-8");

    private final File stateFile;

    private final Map<String, FlowTestRunSummary>
            summaries =
                    new LinkedHashMap<String, FlowTestRunSummary>();

    public FlowTestResultStore(
            File stateFile) {

        this.stateFile = stateFile;
    }

    public synchronized void start() {
        load();
    }

    public synchronized void stop() {
        persist();
    }

    public synchronized FlowTestRunSummary get(
            String flowName) {

        return flowName == null
                ? null
                : summaries.get(
                        flowName);
    }

    public synchronized void put(
            FlowTestRunSummary summary) {

        if (summary == null
                || summary.getFlowName()
                        .isEmpty()) {

            return;
        }

        summaries.put(
                summary.getFlowName(),
                summary);

        persist();
    }

    public synchronized void clear(
            String flowName) {

        if (flowName == null) {
            return;
        }

        if (summaries.remove(
                flowName) != null) {

            persist();
        }
    }

    public synchronized void rename(
            String oldName,
            String newName) {

        if (oldName == null
                || newName == null
                || oldName.equals(newName)) {

            return;
        }

        FlowTestRunSummary old =
                summaries.remove(
                        oldName);

        if (old == null) {
            return;
        }

        summaries.put(
                newName,
                copyWithFlowName(
                        old,
                        newName));

        persist();
    }

    public synchronized void delete(
            String flowName) {

        clear(flowName);
    }

    private static FlowTestRunSummary copyWithFlowName(
            FlowTestRunSummary old,
            String newName) {

        return new FlowTestRunSummary(
                newName,
                old.getStartedAt(),
                old.getFinishedAt(),
                old.getClassesRun(),
                old.getArquillianSkipped(),
                old.getJpaSkipped(),
                old.getIntegrationSkipped(),
                old.isCanceled(),
                old.getResults());
    }

    private void load() {
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
                            != VERSION) {

                return;
            }

            int summaryCount =
                    in.readInt();

            if (summaryCount < 0
                    || summaryCount > 1000) {

                return;
            }

            for (int i = 0;
                    i < summaryCount;
                    i++) {

                FlowTestRunSummary summary =
                        readSummary(in);

                summaries.put(
                        summary.getFlowName(),
                        summary);
            }

        } catch (EOFException e) {
            summaries.clear();

        } catch (IOException e) {
            summaries.clear();

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private FlowTestRunSummary readSummary(
            DataInputStream in)
            throws IOException {

        String flowName =
                readString(in);

        long startedAt =
                in.readLong();

        long finishedAt =
                in.readLong();

        int classesRun =
                in.readInt();

        int arquillianSkipped =
                in.readInt();

        int jpaSkipped =
                in.readInt();

        int integrationSkipped =
                in.readInt();

        boolean canceled =
                in.readBoolean();

        int resultCount =
                in.readInt();

        if (resultCount < 0
                || resultCount > 100000) {

            throw new IOException(
                    "Invalid Flow test result count.");
        }

        List<FlowTestCaseResult> results =
                new ArrayList<FlowTestCaseResult>(
                        resultCount);

        for (int i = 0;
                i < resultCount;
                i++) {

            results.add(
                    new FlowTestCaseResult(
                            readString(in),
                            readString(in),
                            readString(in),
                            in.readInt(),
                            readString(in),
                            readString(in),
                            readString(in),
                            in.readDouble()));
        }

        return new FlowTestRunSummary(
                flowName,
                startedAt,
                finishedAt,
                classesRun,
                arquillianSkipped,
                jpaSkipped,
                integrationSkipped,
                canceled,
                results);
    }

    private void persist() {
        File parent =
                stateFile.getParentFile();

        if (parent != null
                && !parent.exists()) {

            parent.mkdirs();
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

            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(
                    summaries.size());

            for (FlowTestRunSummary summary :
                    summaries.values()) {

                writeSummary(
                        out,
                        summary);
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

            } catch (IOException atomicFailed) {
                Files.move(
                        tmp.toPath(),
                        stateFile.toPath(),
                        StandardCopyOption
                                .REPLACE_EXISTING);
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

    private static void writeSummary(
            DataOutputStream out,
            FlowTestRunSummary summary)
            throws IOException {

        writeString(
                out,
                summary.getFlowName());

        out.writeLong(
                summary.getStartedAt());

        out.writeLong(
                summary.getFinishedAt());

        out.writeInt(
                summary.getClassesRun());

        out.writeInt(
                summary.getArquillianSkipped());

        out.writeInt(
                summary.getJpaSkipped());

        out.writeInt(
                summary.getIntegrationSkipped());

        out.writeBoolean(
                summary.isCanceled());

        out.writeInt(
                summary.getResults()
                        .size());

        for (FlowTestCaseResult result :
                summary.getResults()) {

            writeString(
                    out,
                    result.getTestFilePath());

            writeString(
                    out,
                    result.getClassName());

            writeString(
                    out,
                    result.getMethodName());

            out.writeInt(
                    result.getStatus());

            writeString(
                    out,
                    result.getStackTrace());

            writeString(
                    out,
                    result.getExpected());

            writeString(
                    out,
                    result.getActual());

            out.writeDouble(
                    result.getElapsedSeconds());
        }
    }

    private static void writeString(
            DataOutputStream out,
            String value)
            throws IOException {

        byte[] bytes =
                (value == null
                        ? ""
                        : value)
                        .getBytes(UTF8);

        out.writeInt(
                bytes.length);

        out.write(bytes);
    }

    private static String readString(
            DataInputStream in)
            throws IOException {

        int length =
                in.readInt();

        if (length < 0
                || length
                        > MAX_STRING_BYTES) {

            throw new IOException(
                    "Invalid Flow test result string length.");
        }

        byte[] bytes =
                new byte[length];

        in.readFully(bytes);

        return new String(
                bytes,
                UTF8);
    }
}
