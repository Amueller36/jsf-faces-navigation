package de.andre.jsfnavigation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.jface.preference.IPreferenceStore;

public final class SmartDeployModuleScanner {

    private SmartDeployModuleScanner() {
    }

    public static List<SmartDeployTarget> findTargets(
            String relativeClassPath) {

        if (relativeClassPath == null
                || relativeClassPath.isEmpty()) {

            return Collections.emptyList();
        }

        File installedApps =
                installedAppsRoot();

        if (installedApps == null
                || !installedApps.isDirectory()) {

            return Collections.emptyList();
        }

        List<SmartDeployTarget> result =
                new ArrayList<SmartDeployTarget>();

        File[] cellDirs =
                installedApps.listFiles();

        if (cellDirs == null) {
            return result;
        }

        for (File cell : cellDirs) {
            if (!cell.isDirectory()) {
                continue;
            }

            File[] apps = cell.listFiles();

            if (apps == null) {
                continue;
            }

            for (File earRoot : apps) {
                if (!earRoot.isDirectory()
                        || !earRoot.getName()
                                .toLowerCase()
                                .endsWith(".ear")) {

                    continue;
                }

                scanEar(
                        earRoot,
                        relativeClassPath,
                        result);
            }
        }

        Collections.sort(
                result,
                new Comparator<SmartDeployTarget>() {
                    @Override
                    public int compare(
                            SmartDeployTarget left,
                            SmartDeployTarget right) {

                        return left.displayName()
                                .compareToIgnoreCase(
                                        right.displayName());
                    }
                });

        return result;
    }


    public static List<SmartDeployTarget> findWebResourceTargets(
            String relativeWebPath) {

        if (relativeWebPath == null
                || relativeWebPath.isEmpty()) {

            return Collections.emptyList();
        }

        File installedApps =
                installedAppsRoot();

        if (installedApps == null
                || !installedApps.isDirectory()) {

            return Collections.emptyList();
        }

        List<SmartDeployTarget> result =
                new ArrayList<SmartDeployTarget>();

        File[] cellDirs = installedApps.listFiles();

        if (cellDirs == null) {
            return result;
        }

        for (File cell : cellDirs) {
            if (!cell.isDirectory()) {
                continue;
            }

            File[] apps = cell.listFiles();

            if (apps == null) {
                continue;
            }

            for (File earRoot : apps) {
                if (!earRoot.isDirectory()
                        || !earRoot.getName()
                                .toLowerCase()
                                .endsWith(".ear")) {

                    continue;
                }

                String appName =
                        stripSuffix(
                                earRoot.getName(),
                                ".ear");

                File[] modules =
                        earRoot.listFiles();

                if (modules == null) {
                    continue;
                }

                for (File module : modules) {
                    if (!module.isDirectory()
                            || !module.getName()
                                    .toLowerCase()
                                    .endsWith(".war")) {

                        continue;
                    }

                    File candidate =
                            new File(
                                    module,
                                    relativeWebPath
                                            .replace(
                                                    '/',
                                                    File.separatorChar));

                    if (candidate.isFile()) {
                        result.add(
                                new SmartDeployTarget(
                                        SmartDeployTarget
                                                .EXPLODED_WAR_ROOT,
                                        appName,
                                        earRoot,
                                        module,
                                        module.getName()));
                    }
                }
            }
        }

        Collections.sort(
                result,
                new Comparator<SmartDeployTarget>() {
                    @Override
                    public int compare(
                            SmartDeployTarget left,
                            SmartDeployTarget right) {

                        return left.displayName()
                                .compareToIgnoreCase(
                                        right.displayName());
                    }
                });

        return result;
    }

    private static void scanEar(
            File earRoot,
            String relativeClassPath,
            List<SmartDeployTarget> result) {

        String appName =
                stripSuffix(
                        earRoot.getName(),
                        ".ear");

        File[] children =
                earRoot.listFiles();

        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()
                    && child.getName()
                            .toLowerCase()
                            .endsWith(".war")) {

                File classes =
                        new File(
                                child,
                                "WEB-INF"
                                + File.separator
                                + "classes");

                File candidate =
                        new File(
                                classes,
                                relativeClassPath
                                        .replace(
                                                '/',
                                                File.separatorChar));

                if (candidate.isFile()) {
                    result.add(
                            new SmartDeployTarget(
                                    SmartDeployTarget
                                            .EXPLODED_WAR_CLASSES,
                                    appName,
                                    earRoot,
                                    classes,
                                    child.getName()
                                            + "/WEB-INF/classes"));
                }

            }

            if (child.isFile()
                    && child.getName()
                            .toLowerCase()
                            .endsWith(".jar")) {

                addJarIfContains(
                        earRoot,
                        child,
                        appName,
                        relativeClassPath,
                        result,
                        child.getName());
            }

            if (child.isDirectory()
                    && "lib".equalsIgnoreCase(
                            child.getName())) {

                scanArchives(
                        earRoot,
                        child,
                        appName,
                        relativeClassPath,
                        result,
                        "lib");
            }
        }
    }

    private static void scanArchives(
            File earRoot,
            File directory,
            String appName,
            String relativeClassPath,
            List<SmartDeployTarget> result,
            String contentPrefix) {

        File[] files =
                directory.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanArchives(
                        earRoot,
                        file,
                        appName,
                        relativeClassPath,
                        result,
                        contentPrefix
                                + "/"
                                + file.getName());

                continue;
            }

            if (!file.getName()
                    .toLowerCase()
                    .endsWith(".jar")) {

                continue;
            }

            addJarIfContains(
                    earRoot,
                    file,
                    appName,
                    relativeClassPath,
                    result,
                    contentPrefix
                            + "/"
                            + file.getName());
        }
    }

    private static void addJarIfContains(
            File earRoot,
            File jar,
            String appName,
            String relativeClassPath,
            List<SmartDeployTarget> result,
            String contentUriPrefix) {

        ZipFile zip = null;

        try {
            zip = new ZipFile(jar);

            ZipEntry entry =
                    zip.getEntry(
                            relativeClassPath);

            if (entry != null) {
                result.add(
                        new SmartDeployTarget(
                                SmartDeployTarget
                                        .ARCHIVE_ENTRY,
                                appName,
                                earRoot,
                                jar,
                                contentUriPrefix));
            }

        } catch (IOException e) {
            // Ignore an archive that cannot be inspected.

        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static File installedAppsRoot() {
        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        if (store == null) {
            return null;
        }

        String profile =
                store.getString(
                        WebSphereHotSyncSettings.PROFILE_PATH);

        if (profile == null
                || profile.trim().isEmpty()) {

            return null;
        }

        return new File(
                profile.trim(),
                "installedApps");
    }

    private static String stripSuffix(
            String value,
            String suffix) {

        return value.toLowerCase()
                .endsWith(
                        suffix.toLowerCase())
                ? value.substring(
                        0,
                        value.length()
                                - suffix.length())
                : value;
    }
}
