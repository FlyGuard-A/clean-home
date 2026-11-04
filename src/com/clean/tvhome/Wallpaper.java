package com.clean.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Bing's photo of the day as the background, refreshed every few hours; bundled fallback offline. */
final class Wallpaper {
    interface Listener {
        void onWallpaper(Bitmap full, Bitmap blurred);
    }

    private static final long MAX_AGE = 6 * 60 * 60 * 1000L;
    private static final Executor IO = Executors.newSingleThreadExecutor();

    static void load(final Context c, final Listener l) {
        final Context app = c.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        IO.execute(new Runnable() {
            @Override
            public void run() {
                File file = new File(app.getFilesDir(), "wall.jpg");
                Bitmap shown = file.exists() ? decode(file) : null;
                if (shown == null) shown = BitmapFactory.decodeResource(app.getResources(), R.drawable.wall, rgb565());
                post(main, l, shown);

                if (file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE) return;
                try {
                    JSONObject j = new JSONObject(Net.get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US"));
                    String path = j.getJSONArray("images").getJSONObject(0).getString("url");
                    File tmp = new File(app.getFilesDir(), "wall.tmp");
                    download("https://www.bing.com" + path, tmp);
                    Bitmap fresh = decode(tmp);
                    if (fresh != null && tmp.renameTo(file)) post(main, l, fresh);
                } catch (Exception offline) {
                    // keep whatever we already showed
                }
            }
        });
    }

    private static void post(Handler main, final Listener l, final Bitmap full) {
        if (full == null) return;
        Bitmap s1 = Bitmap.createScaledBitmap(full, 160, 90, true);
        Bitmap s2 = Bitmap.createScaledBitmap(s1, 40, 22, true);
        final Bitmap blurred = Bitmap.createScaledBitmap(s2, 320, 180, true);
        main.post(new Runnable() {
            @Override
            public void run() {
                l.onWallpaper(full, blurred);
            }
        });
    }

    private static BitmapFactory.Options rgb565() {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return o;
    }

    private static Bitmap decode(File f) {
        return BitmapFactory.decodeFile(f.getPath(), rgb565());
    }

    private static void download(String url, File to) throws Exception {
        HttpURLConnection c = Net.open(url);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            c.disconnect();
        }
    }
}
