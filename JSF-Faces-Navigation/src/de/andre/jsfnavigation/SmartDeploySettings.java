package de.andre.jsfnavigation;

import java.io.File;

import org.eclipse.jface.preference.IPreferenceStore;

public final class SmartDeploySettings {

    public static final String ENABLED =
            "websphere.smartDeploy.enabled";

    public static final String WSADMIN_PATH =
            "websphere.smartDeploy.wsadminPath";

    public static final String WSADMIN_EXTRA_ARGS =
            "websphere.smartDeploy.wsadminExtraArgs";

    public static final String STATUS_IN_LOG =
            "websphere.smartDeploy.statusInLog";

    private SmartDeploySettings() {
    }

    public static void initializeDefaults(
            IPreferenceStore store) {

        store.setDefault(ENABLED, false);
        store.setDefault(WSADMIN_PATH, "");
        store.setDefault(WSADMIN_EXTRA_ARGS, "");
        store.setDefault(STATUS_IN_LOG, true);
    }

    public static boolean isEnabled() {
        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        return store != null
                && store.getBoolean(ENABLED);
    }

    public static File resolveWsadmin() {
        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        if (store == null) {
            return null;
        }

        String explicit =
                store.getString(WSADMIN_PATH);

        if (explicit != null
                && !explicit.trim().isEmpty()) {

            File file =
                    new File(explicit.trim());

            return file.isFile()
                    ? file
                    : null;
        }

        String profile =
                store.getString(
                        WebSphereHotSyncSettings.PROFILE_PATH);

        if (profile == null
                || profile.trim().isEmpty()) {

            return null;
        }

        File bin =
                new File(profile.trim(), "bin");

        File bat =
                new File(bin, "wsadmin.bat");

        if (bat.isFile()) {
            return bat;
        }

        File sh =
                new File(bin, "wsadmin.sh");

        return sh.isFile()
                ? sh
                : null;
    }
}
