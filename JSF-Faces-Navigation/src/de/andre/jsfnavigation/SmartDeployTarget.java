package de.andre.jsfnavigation;

import java.io.File;

public final class SmartDeployTarget {

    public static final int EXPLODED_WAR_CLASSES = 1;
    public static final int ARCHIVE_ENTRY = 2;
    public static final int EXPLODED_WAR_ROOT = 3;

    private final int kind;
    private final String applicationNameHint;
    private final File earRoot;
    private final File target;
    private final String contentUriPrefix;

    public SmartDeployTarget(
            int kind,
            String applicationNameHint,
            File earRoot,
            File target,
            String contentUriPrefix) {

        this.kind = kind;
        this.applicationNameHint = applicationNameHint;
        this.earRoot = earRoot;
        this.target = target;
        this.contentUriPrefix = contentUriPrefix;
    }

    public int getKind() {
        return kind;
    }

    public String getApplicationNameHint() {
        return applicationNameHint;
    }

    public File getEarRoot() {
        return earRoot;
    }

    public File getTarget() {
        return target;
    }

    public String getContentUriPrefix() {
        return contentUriPrefix;
    }

    public String identity() {
        return kind
                + "|"
                + earRoot.getAbsolutePath()
                + "|"
                + target.getAbsolutePath()
                + "|"
                + (contentUriPrefix == null
                        ? ""
                        : contentUriPrefix);
    }

    public String displayName() {
        String kindLabel;

        if (kind == EXPLODED_WAR_CLASSES) {
            kindLabel = "WAR classes";
        } else if (kind == EXPLODED_WAR_ROOT) {
            kindLabel = "WAR resources";
        } else {
            kindLabel = "JAR/archive";
        }

        return applicationNameHint
                + " → "
                + contentUriPrefix
                + "  ["
                + kindLabel
                + "]";
    }
}
