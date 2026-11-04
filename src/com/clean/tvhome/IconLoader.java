package com.clean.tvhome;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Renders each app as a finished tile bitmap: the logo centred on the app's own
 * background color, like the reference design. Done off the main thread, cached in memory.
 */
final class IconLoader {
    interface Callback {
        void onLoaded(Bitmap tile);
    }

    private final PackageManager pm;
    private final int w, h;
    private final Map<String, Bitmap> cache = new HashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    IconLoader(PackageManager pm, int tileW, int tileH) {
        this.pm = pm;
        this.w = tileW;
        this.h = tileH;
    }

    Bitmap cached(String key) {
        return cache.get(key);
    }

    void forget(String key) {
        cache.remove(key);
    }

    void load(final AppEntry e, final Callback cb) {
        Bitmap hit = cache.get(e.key);
        if (hit != null) {
            cb.onLoaded(hit);
            return;
        }
        pool.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap tile = render(e);
                if (tile == null) return;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        cache.put(e.key, tile);
                        cb.onLoaded(tile);
                    }
                });
            }
        });
    }

    private Bitmap render(AppEntry e) {
        try {
            Drawable icon = icon(e);
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);

            if (Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable) {
                AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
                if (a.getBackground() != null) {
                    a.getBackground().setBounds(0, (h - w) / 2, w, (h + w) / 2);
                    a.getBackground().draw(c);
                }
                if (a.getForeground() != null) {
                    int s = (int) (h * 0.62f * 108 / 72); // the 72dp safe zone fills 62% of the tile height
                    a.getForeground().setBounds((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
                    a.getForeground().draw(c);
                }
                return out;
            }

            int size = 256;
            Bitmap ib = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, size, size);
            icon.draw(new Canvas(ib));

            int edge = edgeColor(ib);
            boolean fullBleed = Color.alpha(edge) > 200;
            c.drawColor(fullBleed ? (edge | 0xFF000000) : glass(dominant(ib)));
            int s = (int) (h * (fullBleed ? 0.84f : 0.6f));
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            c.drawBitmap(ib, null, new Rect((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2), p);
            ib.recycle();
            return out;
        } catch (Exception ex) {
            return null;
        }
    }

    /** The sharpest version of the icon the app ships. */
    private Drawable icon(AppEntry e) throws PackageManager.NameNotFoundException {
        ComponentName cn = e.component;
        if (cn == null && e.launch != null) {
            ActivityInfo ai = e.launch.resolveActivityInfo(pm, 0);
            if (ai != null) cn = new ComponentName(ai.packageName, ai.name);
        }
        if (cn == null) return pm.getDefaultActivityIcon();
        try {
            ActivityInfo ai = pm.getActivityInfo(cn, 0);
            Resources r = pm.getResourcesForApplication(ai.applicationInfo);
            Drawable d = r.getDrawableForDensity(ai.getIconResource(), DisplayMetrics.DENSITY_XXXHIGH, null);
            if (d != null) return d;
        } catch (Exception ignored) {
        }
        return pm.getActivityIcon(cn);
    }

    /** Average of the middle of each edge (skipping rounded corners). */
    private static int edgeColor(Bitmap b) {
        int n = b.getWidth(), in = n / 16, m = n / 2;
        int[] pts = {b.getPixel(m, in), b.getPixel(m, n - 1 - in), b.getPixel(in, m), b.getPixel(n - 1 - in, m),
                b.getPixel(n / 3, in), b.getPixel(2 * n / 3, n - 1 - in)};
        long r = 0, g = 0, bl = 0, a = 0;
        for (int p : pts) {
            r += Color.red(p);
            g += Color.green(p);
            bl += Color.blue(p);
            a += Color.alpha(p);
        }
        int k = pts.length;
        return Color.argb((int) (a / k), (int) (r / k), (int) (g / k), (int) (bl / k));
    }

    private static int dominant(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 12, 12, true);
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) {
                int p = s.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                n++;
            }
        }
        return n == 0 ? 0xFF3A3A44 : Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }

    /** A dark tile tinted toward the logo's color, for logos without their own background. */
    private static int glass(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(hsv[1], 0.35f);
        hsv[2] = 0.16f;
        return Color.HSVToColor(hsv);
    }
}
