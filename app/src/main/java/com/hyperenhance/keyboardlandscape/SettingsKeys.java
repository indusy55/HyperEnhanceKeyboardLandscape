package com.hyperenhance.keyboardlandscape;

/** Shared settings keys. Plain class so the UI process does not load the Xposed entry. */
public final class SettingsKeys {
    public static final String PREFS_GROUP = "settings";
    public static final String LANDSCAPE_ENABLED = "landscape_enabled";

    private SettingsKeys() {
    }
}
