package com.clean.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire TV ignores third-party home apps, so when the stock home screen comes to the
 * front (Home button, boot, exiting an app) we bring Clean Home up over it.
 * Only listens to com.amazon.tv.launcher (see res/xml/redirect.xml) and reads no content.
 */
public class HomeRedirectService extends AccessibilityService {
    private long last;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (e.getPackageName() == null || e.getClassName() == null) return;
        if (!"com.amazon.tv.launcher".equals(e.getPackageName().toString())) return;
        // Settings and "Your Apps" screens also live in this package; only replace the home page.
        String cls = e.getClassName().toString();
        if (!cls.contains("HomeActivity") && !cls.endsWith(".ui.MainActivity")) return;

        long now = SystemClock.uptimeMillis();
        if (now - last < 700) return;
        last = now;

        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(i);
    }

    @Override
    public void onInterrupt() {
    }
}
