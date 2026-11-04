package com.clean.tvhome;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads app banners/icons off the main thread and keeps them in memory. */
final class IconLoader {
    static final class Art {
        final Bitmap bitmap;
        final boolean banner;
        final int color; // muted dominant color, used for tile and backdrop tint

        Art(Bitmap bitmap, boolean banner, int color) {
            this.bitmap = bitmap;
            this.banner = banner;
            this.color = color;
        }
    }

    interface Callback {
        void onLoaded(Art art);
    }

    private final PackageManager pm;
    private final int bannerW, bannerH, iconSize;
    private final Map<String, Art> cache = new HashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    IconLoader(PackageManager pm, int bannerW, int bannerH) {
        this.pm = pm;
        this.bannerW = bannerW;
        this.bannerH = bannerH;
        this.iconSize = Math.max(96, bannerH / 2);
    }

    Art cached(String key) {
        return cache.get(key);
    }

    void forget(String key) {
        cache.remove(key);
    }

    void load(final AppEntry e, final Callback cb) {
        Art hit = cache.get(e.key);
        if (hit != null) {
            cb.onLoaded(hit);
            return;
        }
        pool.execute(new Runnable() {
            @Override
            public void run() {
                final Art art = render(e);
                if (art == null) return;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        cache.put(e.key, art);
                        cb.onLoaded(art);
                    }
                });
            }
        });
    }

    private Art render(AppEntry e) {
        try {
            Drawable banner = null;
            Drawable icon = null;
            if (e.component != null) {
                try {
                    banner = pm.getActivityBanner(e.component);
                    if (banner == null) banner = pm.getApplicationBanner(e.component.getPackageName());
                } catch (PackageManager.NameNotFoundException ignored) {
                }
                if (banner == null) icon = pm.getActivityIcon(e.component);
            } else if (e.launch != null) {
                try {
                    icon = pm.getActivityIcon(e.launch);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            if (banner != null) {
                Bitmap b = draw(banner, bannerW, bannerH);
                return new Art(b, true, mute(average(b)));
            }
            if (icon == null) icon = pm.getDefaultActivityIcon();
            Bitmap b = draw(icon, iconSize, iconSize);
            return new Art(b, false, mute(average(b)));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Bitmap draw(Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return b;
    }

    /** Average of opaque, non-grey pixels, so logos win over backgrounds. */
    private static int average(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 12, 12, true);
        long r = 0, g = 0, b = 0, n = 0;
        float[] hsv = new float[3];
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) {
                int p = s.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                int w = hsv[1] > 0.25f ? 4 : 1;
                r += Color.red(p) * w;
                g += Color.green(p) * w;
                b += Color.blue(p) * w;
                n += w;
            }
        }
        if (s != src) s.recycle();
        if (n == 0) return 0xFF3A3A44;
        return Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }

    private static int mute(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(hsv[1], 0.55f);
        hsv[2] = Math.max(0.22f, Math.min(hsv[2], 0.42f));
        return Color.HSVToColor(hsv);
    }
}
