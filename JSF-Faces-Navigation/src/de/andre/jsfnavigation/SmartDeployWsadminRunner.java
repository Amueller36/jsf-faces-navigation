package de.andre.jsfnavigation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;

public final class SmartDeployWsadminRunner {

    public static final class ArchiveOperation {

        public static final int UPDATE = 1;
        public static final int DELETE = 2;
        public static final int ADD = 3;

        private final int operation;
        private final File sourceClass;
        private final SmartDeployTarget target;
        private final String relativeClassPath;

        public ArchiveOperation(
                int operation,
                File sourceClass,
                SmartDeployTarget target,
                String relativeClassPath) {

            this.operation = operation;
            this.sourceClass = sourceClass;
            this.target = target;
            this.relativeClassPath = relativeClassPath;
        }

        public int getOperation() {
            return operation;
        }

        public File getSourceClass() {
            return sourceClass;
        }

        public SmartDeployTarget getTarget() {
            return target;
        }

        public String getRelativeClassPath() {
            return relativeClassPath;
        }
    }

    private SmartDeployWsadminRunner() {
    }

    public static String apply(
            List<ArchiveOperation> operations)
            throws IOException, InterruptedException {

        if (operations == null
                || operations.isEmpty()) {

            return "No archive updates.";
        }

        File wsadmin =
                SmartDeploySettings.resolveWsadmin();

        if (wsadmin == null) {
            throw new IOException(
                    "wsadmin executable could not be resolved. "
                    + "Set the WebSphere profile directory or explicit wsadmin path in Preferences.");
        }

        File script =
                Files.createTempFile(
                        "jsf-nav-smart-deploy-",
                        ".py")
                        .toFile();

        try {
            writeScript(
                    script,
                    operations);

            List<String> command =
                    new ArrayList<String>();

            command.add(
                    wsadmin.getAbsolutePath());

            command.addAll(
                    parseArguments(
                            extraArgs()));

            command.add("-lang");
            command.add("jython");
            command.add("-f");
            command.add(
                    script.getAbsolutePath());

            ProcessBuilder builder =
                    new ProcessBuilder(command);

            File parent =
                    wsadmin.getParentFile();

            if (parent != null) {
                builder.directory(parent);
            }

            builder.redirectErrorStream(true);

            Process process =
                    builder.start();

            StringBuilder output =
                    new StringBuilder();

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(),
                                    Charset.defaultCharset()));

            try {
                String line;

                while ((line = reader.readLine()) != null) {
                    output.append(line)
                            .append('\n');
                }

            } finally {
                reader.close();
            }

            int exit =
                    process.waitFor();

            if (exit != 0) {
                throw new IOException(
                        "wsadmin exited with code "
                        + exit
                        + ".\n\n"
                        + output.toString());
            }

            return output.toString();

        } finally {
            script.delete();
        }
    }

    private static void writeScript(
            File script,
            List<ArchiveOperation> operations)
            throws IOException {

        FileWriter writer =
                new FileWriter(script);

        try {
            writer.write(
                    "apps = [x.strip() for x in AdminApp.list().splitlines() if x.strip()]\n");

            writer.write(
                    "def resolveApp(hint):\n"
                    + "    if hint in apps:\n"
                    + "        return hint\n"
                    + "    h = hint.lower().replace('-', '').replace('_', '')\n"
                    + "    matches = []\n"
                    + "    for a in apps:\n"
                    + "        n = a.lower().replace('-', '').replace('_', '')\n"
                    + "        if n == h or h in n or n in h:\n"
                    + "            matches.append(a)\n"
                    + "    if len(matches) == 1:\n"
                    + "        return matches[0]\n"
                    + "    raise Exception('Could not uniquely resolve application: ' + hint + ' from ' + str(apps))\n\n");

            for (ArchiveOperation operation :
                    operations) {

                SmartDeployTarget target =
                        operation.getTarget();

                String app =
                        py(target.getApplicationNameHint());

                String contentUri =
                        py(target.getContentUriPrefix()
                                + "/"
                                + operation.getRelativeClassPath());

                writer.write(
                        "app = resolveApp("
                                + app
                                + ")\n");

                if (operation.getOperation()
                        == ArchiveOperation.DELETE) {

                    writer.write(
                            "print 'SmartDeploy: deleting %s' % ("
                                    + contentUri
                                    + ",)\n");

                    writer.write(
                            "AdminApp.update(app, 'file', "
                                    + "'[-operation delete -contenturi \"' + "
                                    + contentUri
                                    + " + '\"]')\n");

                } else {
                    String contents =
                            py(operation.getSourceClass()
                                    .getAbsolutePath()
                                    .replace('\\', '/'));

                    String operationName =
                            operation.getOperation()
                                    == ArchiveOperation.ADD
                                            ? "add"
                                            : "update";

                    writer.write(
                            "print 'SmartDeploy: "
                                    + operationName
                                    + " %s -> %s' % ("
                                    + contents
                                    + ", "
                                    + contentUri
                                    + ")\n");

                    writer.write(
                            "AdminApp.update(app, 'file', "
                                    + "'[-operation "
                                    + operationName
                                    + " -contents \"' + "
                                    + contents
                                    + " + '\" -contenturi \"' + "
                                    + contentUri
                                    + " + '\"]')\n");
                }
            }

            writer.write("AdminConfig.save()\n");
            writer.write(
                    "print 'SmartDeploy: configuration saved.'\n");

        } finally {
            writer.close();
        }
    }

    private static String extraArgs() {
        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        return store == null
                ? ""
                : store.getString(
                        SmartDeploySettings.WSADMIN_EXTRA_ARGS);
    }

    private static String py(String value) {
        String escaped =
                value.replace("\\", "\\\\")
                        .replace("'", "\\'");

        return "'" + escaped + "'";
    }

    static List<String> parseArguments(
            String input) {

        List<String> result =
                new ArrayList<String>();

        if (input == null
                || input.trim().isEmpty()) {

            return result;
        }

        StringBuilder current =
                new StringBuilder();

        boolean quoted = false;
        char quote = 0;

        for (int i = 0;
                i < input.length();
                i++) {

            char c = input.charAt(i);

            if ((c == '"'
                    || c == '\'')
                    && (!quoted
                            || quote == c)) {

                if (quoted) {
                    quoted = false;
                    quote = 0;
                } else {
                    quoted = true;
                    quote = c;
                }

                continue;
            }

            if (Character.isWhitespace(c)
                    && !quoted) {

                if (current.length() > 0) {
                    result.add(
                            current.toString());

                    current.setLength(0);
                }

                continue;
            }

            current.append(c);
        }

        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }
}
