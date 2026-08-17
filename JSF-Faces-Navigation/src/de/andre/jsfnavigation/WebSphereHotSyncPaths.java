package de.andre.jsfnavigation;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.preference.IPreferenceStore;

public final class WebSphereHotSyncPaths {

    private static final String[] COMMON_SOURCE_ROOTS = new String[] {
            "WebContent",
            "src/main/webapp",
            "web",
            "src/main/resources/META-INF/resources"
    };

    private WebSphereHotSyncPaths() {
    }

    public static IPath relativeWebPath(IFile source) {
        if (source == null) {
            return null;
        }

        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        String configured =
                store == null
                        ? ""
                        : store.getString(
                                WebSphereHotSyncSettings.SOURCE_WEB_ROOT);

        IPath projectRelative =
                source.getProjectRelativePath();

        IPath relative =
                stripRoot(
                        projectRelative,
                        configured);

        if (relative != null) {
            return relative;
        }

        for (String candidate : COMMON_SOURCE_ROOTS) {
            relative =
                    stripRoot(
                            projectRelative,
                            candidate);

            if (relative != null) {
                return relative;
            }
        }

        return null;
    }

    public static File configuredDeployedWebRoot() {
        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        if (store == null) {
            return null;
        }

        String path =
                store.getString(
                        WebSphereHotSyncSettings.DEPLOYED_WEB_ROOT);

        if (path == null
                || path.trim().isEmpty()) {

            return null;
        }

        File root =
                new File(path.trim());

        return root.isDirectory()
                ? root
                : null;
    }

    public static File resolveTarget(
            IFile source) {

        IPath relative =
                relativeWebPath(source);

        if (relative == null) {
            return null;
        }

        File root =
                configuredDeployedWebRoot();

        if (root == null) {
            List<File> candidates =
                    discoverDeployedWebRoots();

            if (candidates.size() == 1) {
                root = candidates.get(0);
            }
        }

        return root == null
                ? null
                : new File(
                        root,
                        relative.toOSString());
    }

    public static List<File> discoverDeployedWebRoots() {
        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        if (store == null) {
            return Collections.emptyList();
        }

        String profilePath =
                store.getString(
                        WebSphereHotSyncSettings.PROFILE_PATH);

        if (profilePath == null
                || profilePath.trim().isEmpty()) {

            return Collections.emptyList();
        }

        File installedApps =
                new File(
                        profilePath.trim(),
                        "installedApps");

        if (!installedApps.isDirectory()) {
            return Collections.emptyList();
        }

        List<File> result =
                new ArrayList<File>();

        scanForWebModules(
                installedApps,
                result,
                0);

        Collections.sort(
                result,
                new Comparator<File>() {
                    @Override
                    public int compare(
                            File left,
                            File right) {

                        return left.getAbsolutePath()
                                .compareToIgnoreCase(
                                        right.getAbsolutePath());
                    }
                });

        return result;
    }

    public static boolean isSyncable(
            IFile file) {

        if (file == null
                || !file.exists()) {

            return false;
        }

        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        if (store == null) {
            return false;
        }

        String extension =
                file.getFileExtension();

        if (extension == null) {
            return false;
        }

        String lower =
                extension.toLowerCase();

        if ("xhtml".equals(lower)
                || "html".equals(lower)
                || "htm".equals(lower)) {

            return store.getBoolean(
                    WebSphereHotSyncSettings.SYNC_XHTML);
        }

        if ("js".equals(lower)) {
            return store.getBoolean(
                    WebSphereHotSyncSettings.SYNC_JS);
        }

        if ("css".equals(lower)) {
            return store.getBoolean(
                    WebSphereHotSyncSettings.SYNC_CSS);
        }

        if ("properties".equals(lower)) {
            return store.getBoolean(
                    WebSphereHotSyncSettings.SYNC_PROPERTIES);
        }

        return false;
    }

    private static IPath stripRoot(
            IPath path,
            String sourceRoot) {

        if (path == null
                || sourceRoot == null
                || sourceRoot.trim().isEmpty()) {

            return null;
        }

        IPath root =
                new Path(
                        sourceRoot.trim()
                                .replace('\\', '/'));

        if (!root.isPrefixOf(path)) {
            return null;
        }

        return path.removeFirstSegments(
                root.segmentCount());
    }

    private static void scanForWebModules(
            File directory,
            List<File> result,
            int depth) {

        if (directory == null
                || !directory.isDirectory()
                || depth > 6) {

            return;
        }

        File[] children =
                directory.listFiles();

        if (children == null) {
            return;
        }

        for (File child : children) {
            if (!child.isDirectory()) {
                continue;
            }

            String name =
                    child.getName()
                            .toLowerCase();

            if (name.endsWith(".war")) {
                result.add(child);
                continue;
            }

            /*
             * Some exploded deployments are not literally named *.war.
             * WEB-INF is a strong signal that this directory is a web root.
             */
            if (new File(child, "WEB-INF").isDirectory()) {
                result.add(child);
                continue;
            }

            scanForWebModules(
                    child,
                    result,
                    depth + 1);
        }
    }
}
