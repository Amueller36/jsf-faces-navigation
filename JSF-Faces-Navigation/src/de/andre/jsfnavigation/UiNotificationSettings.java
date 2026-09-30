package de.andre.jsfnavigation;

import org.eclipse.jface.preference.IPreferenceStore;

public final class UiNotificationSettings {

    public static final String SHOW_STATUS_LINE_MESSAGES =
            "ui.statusLine.showPluginMessages";

    private UiNotificationSettings() {
    }

    public static void initializeDefaults(
            IPreferenceStore store) {

        store.setDefault(
                SHOW_STATUS_LINE_MESSAGES,
                false);
    }

    public static boolean showStatusLineMessages() {
        Activator plugin =
                Activator.getDefault();

        return plugin != null
                && plugin.getPreferenceStore()
                        .getBoolean(
                                SHOW_STATUS_LINE_MESSAGES);
    }
}
