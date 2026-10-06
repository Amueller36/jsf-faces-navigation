package de.andre.jsfnavigation;

import org.eclipse.jface.preference.IPreferenceStore;

public final class JavaTestRunSettings {

    public static final String SHOW_GUTTER =
            "junit.gutter.showRunButtons";

    private JavaTestRunSettings() {
    }

    public static void initializeDefaults(
            IPreferenceStore store) {

        store.setDefault(
                SHOW_GUTTER,
                true);
    }

    public static boolean isEnabled() {
        Activator plugin =
                Activator.getDefault();

        return plugin == null
                || plugin.getPreferenceStore()
                        .getBoolean(
                                SHOW_GUTTER);
    }
}
