package de.andre.jsfnavigation;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;

public final class WebSphereLogPaths {

    private WebSphereLogPaths() {
    }

    public static File resolveLogDirectory() {
        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        if (store == null) {
            return null;
        }

        String explicit =
                store.getString(
                        WebSphereLogSettings.LOG_DIRECTORY);

        if (explicit != null
                && !explicit.trim().isEmpty()) {

            File dir = new File(explicit.trim());

            return dir.isDirectory()
                    ? dir
                    : null;
        }

        String profile =
                store.getString(
                        WebSphereHotSyncSettings.PROFILE_PATH);

        if (profile == null
                || profile.trim().isEmpty()) {

            return null;
        }

        String serverName =
                store.getString(
                        WebSphereLogSettings.SERVER_NAME);

        if (serverName != null
                && !serverName.trim().isEmpty()) {

            File dir =
                    new File(
                            new File(profile.trim(), "logs"),
                            serverName.trim());

            if (dir.isDirectory()) {
                return dir;
            }
        }

        List<File> candidates =
                discoverServerLogDirectories();

        return candidates.size() == 1
                ? candidates.get(0)
                : null;
    }

    public static List<File> discoverServerLogDirectories() {
        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        if (store == null) {
            return Collections.emptyList();
        }

        String profile =
                store.getString(
                        WebSphereHotSyncSettings.PROFILE_PATH);

        if (profile == null
                || profile.trim().isEmpty()) {

            return Collections.emptyList();
        }

        File logsRoot =
                new File(
                        profile.trim(),
                        "logs");

        if (!logsRoot.isDirectory()) {
            return Collections.emptyList();
        }

        File[] children =
                logsRoot.listFiles();

        if (children == null) {
            return Collections.emptyList();
        }

        List<File> result =
                new ArrayList<File>();

        for (File child : children) {
            if (!child.isDirectory()) {
                continue;
            }

            if (new File(child, "SystemOut.log").isFile()
                    || new File(child, "SystemErr.log").isFile()) {

                result.add(child);
            }
        }

        Collections.sort(
                result,
                new Comparator<File>() {
                    @Override
                    public int compare(
                            File left,
                            File right) {

                        return left.getName()
                                .compareToIgnoreCase(
                                        right.getName());
                    }
                });

        return result;
    }

    public static File systemOut() {
        File dir = resolveLogDirectory();

        return dir == null
                ? null
                : new File(dir, "SystemOut.log");
    }

    public static File systemErr() {
        File dir = resolveLogDirectory();

        return dir == null
                ? null
                : new File(dir, "SystemErr.log");
    }
}
