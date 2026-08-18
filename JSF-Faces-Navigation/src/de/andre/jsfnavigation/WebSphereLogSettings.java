package de.andre.jsfnavigation;

import org.eclipse.jface.preference.IPreferenceStore;

public final class WebSphereLogSettings {

    public static final String SERVER_NAME =
            "websphere.logs.serverName";

    public static final String LOG_DIRECTORY =
            "websphere.logs.logDirectory";

    public static final String AUTO_REFRESH =
            "websphere.logs.autoRefresh";

    public static final String TAIL_BYTES =
            "websphere.logs.tailBytes";

    private WebSphereLogSettings() {
    }

    public static void initializeDefaults(
            IPreferenceStore store) {

        store.setDefault(SERVER_NAME, "");
        store.setDefault(LOG_DIRECTORY, "");
        store.setDefault(AUTO_REFRESH, true);
        store.setDefault(TAIL_BYTES, 262144);
    }
}
