package de.andre.jsfnavigation;

import org.eclipse.jface.preference.IPreferenceStore;

public final class WebSphereHotSyncSettings {

    public static final String ENABLED =
            "websphere.hotSync.enabled";

    public static final String AUTO_SYNC =
            "websphere.hotSync.autoSync";

    public static final String PROFILE_PATH =
            "websphere.hotSync.profilePath";

    public static final String DEPLOYED_WEB_ROOT =
            "websphere.hotSync.deployedWebRoot";

    public static final String SOURCE_WEB_ROOT =
            "websphere.hotSync.sourceWebRoot";

    public static final String SYNC_XHTML =
            "websphere.hotSync.xhtml";

    public static final String SYNC_JS =
            "websphere.hotSync.js";

    public static final String SYNC_CSS =
            "websphere.hotSync.css";

    public static final String SYNC_PROPERTIES =
            "websphere.hotSync.properties";

    private WebSphereHotSyncSettings() {
    }

    public static void initializeDefaults(
            IPreferenceStore store) {

        store.setDefault(ENABLED, false);
        store.setDefault(AUTO_SYNC, false);
        store.setDefault(PROFILE_PATH, "");
        store.setDefault(DEPLOYED_WEB_ROOT, "");
        store.setDefault(SOURCE_WEB_ROOT, "WebContent");
        store.setDefault(SYNC_XHTML, true);
        store.setDefault(SYNC_JS, true);
        store.setDefault(SYNC_CSS, true);
        store.setDefault(SYNC_PROPERTIES, false);
    }

    public static IPreferenceStore store() {
        Activator plugin = Activator.getDefault();

        return plugin == null
                ? null
                : plugin.getPreferenceStore();
    }
}
