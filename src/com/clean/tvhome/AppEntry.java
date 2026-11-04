package com.clean.tvhome;

import android.content.ComponentName;
import android.content.Intent;

/** One tile on the home screen: an installed app, or a built-in tile like Settings. */
final class AppEntry {
    static final String KEY_SETTINGS = "::settings";
    static final String KEY_HIDDEN = "::hidden";

    final String key;
    final String label;
    final ComponentName component; // null for built-in tiles
    final Intent launch;

    AppEntry(String key, String label, ComponentName component, Intent launch) {
        this.key = key;
        this.label = label;
        this.component = component;
        this.launch = launch;
    }

    boolean isApp() {
        return component != null && !key.startsWith("::");
    }

    String packageName() {
        return component == null ? null : component.getPackageName();
    }
}
