package de.andre.jsfnavigation;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;

public final class SmartWebResourceResolver {

    private SmartWebResourceResolver() {
    }

    public static File resolveWebRoot(
            IFile source,
            IPath relativeWebPath) {

        List<File> roots =
                resolveWebRoots(
                        source,
                        relativeWebPath);

        return roots.isEmpty()
                ? null
                : roots.get(0);
    }

    public static List<File> resolveWebRoots(
            IFile source,
            IPath relativeWebPath) {

        if (source == null
                || relativeWebPath == null) {

            return Collections.emptyList();
        }

        /*
         * Web-resource hot sync is intentionally independent from the
         * opt-in Smart Java/Class Deploy setting. A shared XHTML/JS/CSS
         * resource may legitimately belong to multiple exploded WARs, so
         * web mappings are 1:n even though Java/Class deploy remains 1:1.
         */
        SmartDeployMappingStore mappings =
                Activator.getSmartDeployMappingStore();

        String key =
                "WEB|"
                + source.getProject().getName()
                + "|"
                + sourceRootKey(source);

        List<SmartDeployTarget> mapped =
                mappings == null
                        ? Collections
                                .<SmartDeployTarget>emptyList()
                        : mappings.getAll(
                                key);

        List<SmartDeployTarget> validMapped =
                validWebTargets(
                        mapped);

        if (!validMapped.isEmpty()) {
            if (mappings != null
                    && validMapped.size()
                            != mapped.size()) {

                mappings.putAll(
                        key,
                        validMapped);
            }

            return targetFiles(
                    validMapped);
        }

        List<SmartDeployTarget> candidates =
                SmartDeployModuleScanner
                        .findWebResourceTargets(
                                relativeWebPath
                                    .toPortableString());

        List<SmartDeployTarget> selected =
                SmartDeployTargetChooser
                        .chooseWebResources(
                                relativeWebPath
                                    .toPortableString(),
                                candidates);

        if (selected != null
                && !selected.isEmpty()) {

            if (mappings != null) {
                mappings.putAll(
                        key,
                        selected);
            }

            WebSphereStatusLine.show(
                    "Smart Deploy learned web mapping: "
                    + source.getProject().getName()
                    + " → "
                    + selected.size()
                    + (selected.size() == 1
                            ? " WAR"
                            : " WARs"));

            return targetFiles(
                    selected);
        }

        return Collections.emptyList();
    }

    private static List<SmartDeployTarget> validWebTargets(
            List<SmartDeployTarget> targets) {

        List<SmartDeployTarget> result =
                new ArrayList<SmartDeployTarget>();

        if (targets == null) {
            return result;
        }

        for (SmartDeployTarget target :
                targets) {

            if (target != null
                    && target.getKind()
                            == SmartDeployTarget.EXPLODED_WAR_ROOT
                    && target.getTarget() != null
                    && target.getTarget()
                            .isDirectory()) {

                result.add(
                        target);
            }
        }

        return result;
    }

    private static List<File> targetFiles(
            List<SmartDeployTarget> targets) {

        List<File> result =
                new ArrayList<File>();

        if (targets == null) {
            return result;
        }

        for (SmartDeployTarget target :
                targets) {

            if (target == null
                    || target.getTarget() == null) {

                continue;
            }

            File candidate =
                    target.getTarget();

            boolean duplicate =
                    false;

            for (File existing :
                    result) {

                if (existing.equals(
                        candidate)) {

                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                result.add(
                        candidate);
            }
        }

        return result;
    }

    private static String sourceRootKey(
            IFile source) {

        IPath relative =
                WebSphereHotSyncPaths
                        .relativeWebPath(source);

        if (relative == null) {
            return "";
        }

        IPath full =
                source.getProjectRelativePath();

        int rootSegments =
                full.segmentCount()
                - relative.segmentCount();

        return rootSegments <= 0
                ? ""
                : full.removeLastSegments(
                        relative.segmentCount())
                        .toPortableString();
    }
}
