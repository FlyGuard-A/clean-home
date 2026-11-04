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
            boolean adaptive = Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable;
            Bitmap sq = square(icon, adaptive);
            Rect box = opaqueBounds(sq);
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            if (box == null) {
                c.drawColor(DARK);
                return out;
            }
            Bitmap logo = Bitmap.createBitmap(sq, box.left, box.top, box.width(), box.height());
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

            if (adaptive || fullBleed(logo)) {
                // The icon has its own background: extend that background to fill the tile,
                // by stretching the icon's outermost pixels outward, so there is no seam.
                logo = solidEdges(logo);
                int lw = logo.getWidth(), lh = logo.getHeight();
                c.drawColor(logo.getPixel(0, lh / 2) | 0xFF000000);
                float scale = adaptive ? 1.2f : 1f; // adaptive layers have spare margin around the logo
                int dh = (int) (h * scale), dw = dh * lw / lh;
                if (dw <= w) {
                    int l = (w - dw) / 2, t = (h - dh) / 2;
                    c.drawBitmap(logo, null, new Rect(l, t, l + dw, t + dh), p);
                    c.drawBitmap(logo, new Rect(0, 0, 1, lh), new Rect(0, t, l + 1, t + dh), p);
                    c.drawBitmap(logo, new Rect(lw - 1, 0, lw, lh), new Rect(l + dw - 1, t, w, t + dh), p);
                } else {
                    dw = w;
                    dh = w * lh / lw;
                    int t = (h - dh) / 2;
                    c.drawBitmap(logo, null, new Rect(0, t, w, t + dh), p);
                    c.drawBitmap(logo, new Rect(0, 0, lw, 1), new Rect(0, 0, w, t + 1), p);
                    c.drawBitmap(logo, new Rect(0, lh - 1, lw, lh), new Rect(0, t + dh - 1, w, h), p);
                }
            } else {
                // A bare logo with no background of its own: put it on a plain dark tile.
                c.drawColor(DARK);
                float fit = Math.min(h * 0.6f / logo.getHeight(), w * 0.7f / logo.getWidth());
                int dw = (int) (logo.getWidth() * fit), dh = (int) (logo.getHeight() * fit);
                c.drawBitmap(logo, null, new Rect((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), p);
            }
            return out;
        } catch (Exception ex) {
            return null;
        }
    }

    private static final int DARK = 0xFF1C1C21;
    private static final int SIZE = 256;

    /** The icon drawn square; adaptive icons are drawn unmasked (background + foreground layers). */
    private static Bitmap square(Drawable icon, boolean adaptive) {
        Bitmap b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (adaptive) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            Drawable[] layers = {a.getBackground(), a.getForeground()};
            for (Drawable d : layers) {
                if (d == null) continue;
                d.setBounds(0, 0, SIZE, SIZE);
                d.draw(c);
            }
        } else {
            icon.setBounds(0, 0, SIZE, SIZE);
            icon.draw(c);
        }
        return b;
    }

    /** Bounds of the visible pixels, which trims any transparent padding the app added. */
    private static Rect opaqueBounds(Bitmap b) {
        int n = b.getWidth();
        int[] px = new int[n * n];
        b.getPixels(px, 0, n, 0, 0, n, n);
        int l = n, t = n, r = -1, bo = -1;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if ((px[y * n + x] >>> 24) > 24) {
                    if (x < l) l = x;
                    if (x > r) r = x;
                    if (y < t) t = y;
                    if (y > bo) bo = y;
                }
            }
        }
        return r < 0 ? null : new Rect(l, t, r + 1, bo + 1);
    }

    /** True when the icon is a filled shape (square, rounded square, circle-free), not a bare logo. */
    private static boolean fullBleed(Bitmap b) {
        int bw = b.getWidth(), bh = b.getHeight(), ix = Math.max(2, bw / 40), iy = Math.max(2, bh / 40);
        int[][] pts = {
                {bw / 2, iy}, {bw / 2, bh - 1 - iy}, {ix, bh / 2}, {bw - 1 - ix, bh / 2},
                {bw / 3, iy}, {2 * bw / 3, iy}, {bw / 3, bh - 1 - iy}, {2 * bw / 3, bh - 1 - iy},
                {ix, bh / 3}, {ix, 2 * bh / 3}, {bw - 1 - ix, bh / 3}, {bw - 1 - ix, 2 * bh / 3}};
        for (int[] q : pts) {
            if (Color.alpha(b.getPixel(q[0], q[1])) < 230) return false;
        }
        return true;
    }

    /**
     * Fills rounded corners and anti-aliased edges with the nearest solid pixel in the
     * same row, so stretching the outer columns gives a clean continuation.
     */
    private static Bitmap solidEdges(Bitmap src) {
        int bw = src.getWidth(), bh = src.getHeight();
        int[] px = new int[bw * bh];
        src.getPixels(px, 0, bw, 0, 0, bw, bh);
        int lastGood = -1;
        for (int y = 0; y < bh; y++) {
            int o = y * bw, first = -1, last = -1;
            for (int x = 0; x < bw; x++) if ((px[o + x] >>> 24) > 230) { first = x; break; }
            for (int x = bw - 1; x >= 0; x--) if ((px[o + x] >>> 24) > 230) { last = x; break; }
            if (first < 0) continue;
            for (int x = 0; x < first; x++) px[o + x] = px[o + first];
            for (int x = last + 1; x < bw; x++) px[o + x] = px[o + last];
            if (lastGood < 0) {
                for (int yy = 0; yy < y; yy++) System.arraycopy(px, o, px, yy * bw, bw);
            }
            lastGood = y;
        }
        if (lastGood < 0) return src;
        for (int y = lastGood + 1; y < bh; y++) System.arraycopy(px, lastGood * bw, px, y * bw, bw);
        for (int y = 0; y < bh; y++) {
            int o = y * bw;
            boolean empty = true;
            for (int x = 0; x < bw && empty; x++) if ((px[o + x] >>> 24) > 230) empty = false;
            if (empty && y > 0) System.arraycopy(px, o - bw, px, o, bw);
        }
        for (int i = 0; i < px.length; i++) px[i] |= 0xFF000000;
        Bitmap out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, bw, 0, 0, bw, bh);
        return out;
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
}
