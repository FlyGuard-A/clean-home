package com.clean.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Remembers tile order and which apps are hidden. */
final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    List<String> order() {
        String raw = sp.getString("order", "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\n")));
    }

    void setOrder(List<String> keys) {
        sp.edit().putString("order", TextUtils.join("\n", keys)).apply();
    }

    Set<String> hidden() {
        return new HashSet<>(sp.getStringSet("hidden", new HashSet<String>()));
    }

    void setHidden(Set<String> keys) {
        sp.edit().putStringSet("hidden", new HashSet<>(keys)).apply();
    }
}
