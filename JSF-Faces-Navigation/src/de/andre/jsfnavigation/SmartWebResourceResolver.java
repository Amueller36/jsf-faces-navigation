package de.andre.jsfnavigation;

import java.io.File;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;

public final class SmartWebResourceResolver {

    private SmartWebResourceResolver() {
    }

    public static File resolveWebRoot(
            IFile source,
            IPath relativeWebPath) {

        if (!SmartDeploySettings.isEnabled()
                || source == null
                || relativeWebPath == null) {

            return null;
        }

        SmartDeployMappingStore mappings =
                Activator.getSmartDeployMappingStore();

        if (mappings == null) {
            return null;
        }

        String key =
                "WEB|"
                + source.getProject().getName()
                + "|"
                + sourceRootKey(source);

        SmartDeployTarget mapped =
                mappings.get(key);

        if (mapped != null
                && mapped.getKind()
                        == SmartDeployTarget.EXPLODED_WAR_ROOT
                && mapped.getTarget() != null
                && mapped.getTarget()
                        .isDirectory()) {

            return mapped.getTarget();
        }

        List<SmartDeployTarget> candidates =
                SmartDeployModuleScanner
                        .findWebResourceTargets(
                                relativeWebPath
                                    .toPortableString());

        SmartDeployTarget selected =
                SmartDeployTargetChooser.choose(
                        relativeWebPath
                            .toPortableString(),
                        candidates);

        if (selected != null) {
            mappings.put(key, selected);

            WebSphereStatusLine.show(
                    "Smart Deploy learned web mapping: "
                    + source.getProject().getName()
                    + " → "
                    + selected.displayName());

            return selected.getTarget();
        }

        return null;
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
