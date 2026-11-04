package com.clean.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends Activity {
    private static final int COLS = 6;

    /** Streaming apps placed first on a fresh install; everything else is alphabetical. */
    private static final List<String> FAVORITES = Arrays.asList(
            "com.amazon.firetv.youtube",
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.avod",
            "com.amazon.firebat",
            "com.espn.score_center",
            "com.spotify.tv.android",
            "com.disney.disneyplus",
            "com.hulu.plus",
            "com.wbd.stream",
            "com.hbo.hbonow",
            "com.apple.atve.amazon.appletv",
            "com.cbs.ott",
            "com.peacocktv.peacockandroid",
            "com.plexapp.android");

    /** Packages that never get a tile: the stock launcher, and Settings (we have our own tile). */
    private static final Set<String> SKIP = new HashSet<>(Arrays.asList(
            "com.amazon.tv.launcher",
            "com.amazon.tv.settings",
            "com.amazon.tv.settings.v2",
            "com.android.tv.settings"));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppEntry> entries = new ArrayList<>();
    private final List<AppEntry> hiddenEntries = new ArrayList<>();
    private final List<TileView> tiles = new ArrayList<>();
    private final List<FrostedPanel> panels = new ArrayList<>();

    private Prefs prefs;
    private IconLoader loader;
    private float dp;
    private int screenH, tileW, tileH, gap, rowGap, side, pad, dockH, dockTop;

    private ScrollView scroll;
    private ImageView wallpaper;
    private View dim, leftInfo, rightColumn;
    private LinearLayout dockRow, grid;
    private TextView bigClock, dateText, hint;
    private NowPlaying nowPlaying;
    private Scores scores;

    private boolean moving;
    private int moveIndex;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Uri data = i.getData();
            if (data != null) loader.forget(data.getSchemeSpecificPart());
            reload(focusedKey());
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            long now = System.currentTimeMillis();
            handler.postDelayed(this, 60_000 - now % 60_000 + 50);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        // No screensaver or sleep while the home screen is showing; apps you open manage their own.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = new Prefs(this);
        measure();
        loader = new IconLoader(getPackageManager(), tileW, tileH);
        setContentView(buildUi());

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);

        reload(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        tick.run();
        enableOwnServices();
        updateHint();
        nowPlaying.start();
        scores.start();
        Wallpaper.load(this, new Wallpaper.Listener() {
            @Override
            public void onWallpaper(Bitmap full, Bitmap blurred) {
                wallpaper.setImageBitmap(full);
                FrostedPanel.backdrop = blurred;
                invalidatePanels();
            }
        });
        if (getCurrentFocus() == null && !tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        nowPlaying.stop();
        scores.stop();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        goTop();
    }

    @Override
    public void onBackPressed() {
        goTop(); // this is home: there is nowhere to go back to
    }

    private void goTop() {
        if (moving) endMove();
        scroll.smoothScrollTo(0, 0);
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    // ---------------------------------------------------------------- layout

    private void measure() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        dp = dm.density;
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        screenH = Math.min(dm.widthPixels, dm.heightPixels);
        side = px(36);
        pad = px(22);
        gap = px(24);
        rowGap = px(30);
        tileW = (w - 2 * side - 2 * pad - (COLS - 1) * gap) / COLS;
        tileH = tileW * 3 / 5;
        dockH = tileH + 2 * pad;
        // Leave a strip under the dock so the next row peeks out, like tvOS.
        dockTop = screenH - dockH - px(60);
    }

    private int px(float v) {
        return (int) (v * dp + 0.5f);
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0D12);

        wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x8C000000, 0x1A000000, 0x00000000, 0x59000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        dim = new View(this);
        dim.setBackgroundColor(0xFF000000);
        dim.setAlpha(0f);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        scroll = new NoAutoScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(side, 0, side, 0);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        content.addView(new View(this), new LinearLayout.LayoutParams(-1, dockTop));

        FrostedPanel dock = new FrostedPanel(this, px(30), 0x3DFFFFFF, 0x59FFFFFF, dp);
        panels.add(dock);
        dockRow = row();
        dockRow.setPadding(pad, pad, pad, pad);
        dock.addView(dockRow, new FrameLayout.LayoutParams(-1, -1));
        content.addView(dock, new LinearLayout.LayoutParams(-1, dockH));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(pad, 0, pad, screenH); // room so the last row can scroll up
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
        gl.topMargin = rowGap;
        content.addView(grid, gl);

        // Top left: time and date.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        bigClock = text(80, "sans-serif-thin", 0xFFFFFFFF);
        bigClock.setIncludeFontPadding(false);
        dateText = text(20, "sans-serif-light", 0xE6FFFFFF);
        hint = text(13, "sans-serif", 0x99FFFFFF);
        hint.setPadding(0, px(14), 0, 0);
        hint.setMaxWidth(px(420));
        left.addView(bigClock);
        left.addView(dateText);
        left.addView(hint);
        leftInfo = left;
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        ll.setMargins(side + px(8), px(30), 0, 0);
        root.addView(left, ll);

        // Top right: Spotify, then scores underneath.
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        nowPlaying = new NowPlaying(this, dp);
        right.addView(nowPlaying.view(), new LinearLayout.LayoutParams(-1, -2));
        scores = new Scores(this, dp);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = px(12);
        right.addView(scores.view(), sl);
        panels.add((FrostedPanel) nowPlaying.view());
        panels.add((FrostedPanel) scores.view());
        rightColumn = right;
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(px(340), -2, Gravity.TOP | Gravity.END);
        rl.setMargins(0, px(28), side, 0);
        root.addView(right, rl);

        scroll.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                float f = Math.min(1f, scroll.getScrollY() / (dockTop * 0.5f));
                leftInfo.setAlpha(1f - f);
                rightColumn.setAlpha(1f - f);
                dim.setAlpha(0.5f * f);
                invalidatePanels();
            }
        });
        return root;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        return r;
    }

    private void invalidatePanels() {
        for (FrostedPanel p : panels) p.invalidate();
    }

    private TextView text(float sp, String family, int color) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setShadowLayer(px(8), 0, px(1), 0x66000000);
        return t;
    }

    private void updateClock() {
        Date now = new Date();
        bigClock.setText(new SimpleDateFormat(DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm", Locale.getDefault()).format(now));
        dateText.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now));
    }

    private void updateHint() {
        boolean fireTv;
        try {
            getPackageManager().getPackageInfo("com.amazon.tv.launcher", 0);
            fireTv = true;
        } catch (PackageManager.NameNotFoundException e) {
            fireTv = false;
        }
        String on = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean redirecting = on != null && on.contains(getPackageName() + "/");
        if (fireTv && !redirecting) {
            hint.setText("Tip: to open this with the Home button, do the one-time setup at github.com/FlyGuard-A/clean-home");
            hint.setVisibility(View.VISIBLE);
        } else {
            hint.setVisibility(View.GONE);
        }
    }

    /**
     * Fire TV has no menu for third-party accessibility or notification access. Once
     * WRITE_SECURE_SETTINGS has been granted over adb we switch both on ourselves,
     * and again whenever Fire OS turns them off.
     */
    private void enableOwnServices() {
        if (checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return;
        try {
            ContentResolver cr = getContentResolver();
            addToList(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, new ComponentName(this, HomeRedirectService.class));
            Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            addToList(cr, "enabled_notification_listeners", new ComponentName(this, MediaListener.class));
        } catch (SecurityException ignored) {
        }
    }

    private static void addToList(ContentResolver cr, String key, ComponentName cn) {
        String cur = Settings.Secure.getString(cr, key);
        String me = cn.flattenToString();
        if (cur != null && cur.contains(me)) return;
        Settings.Secure.putString(cr, key, cur == null || cur.isEmpty() ? me : cur + ":" + me);
    }

    // ---------------------------------------------------------------- apps

    private void reload(String focusKey) {
        loadEntries();
        int idx = 0;
        if (focusKey != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).key.equals(focusKey)) idx = i;
            }
        }
        buildGrid(idx);
    }

    private void loadEntries() {
        PackageManager pm = getPackageManager();
        final Map<String, AppEntry> found = new HashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, found);
        collect(pm, Intent.CATEGORY_LAUNCHER, found);
        Intent settings = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        found.put(AppEntry.KEY_SETTINGS, new AppEntry(AppEntry.KEY_SETTINGS, "Settings", null, settings));

        Set<String> hidden = prefs.hidden();
        entries.clear();
        hiddenEntries.clear();
        Set<String> placed = new HashSet<>();
        for (String key : prefs.order()) {
            AppEntry e = found.get(key);
            if (e != null && !hidden.contains(key) && placed.add(key)) entries.add(e);
        }

        List<AppEntry> fresh = new ArrayList<>();
        for (AppEntry e : found.values()) {
            if (hidden.contains(e.key)) hiddenEntries.add(e);
            else if (!placed.contains(e.key)) fresh.add(e);
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(fresh, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                int ra = rank(a), rb = rank(b);
                if (ra != rb) return ra - rb;
                return collator.compare(a.label, b.label);
            }
        });
        Collections.sort(hiddenEntries, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return collator.compare(a.label, b.label);
            }
        });
        entries.addAll(fresh);
        saveOrder();
    }

    private static int rank(AppEntry e) {
        if (AppEntry.KEY_SETTINGS.equals(e.key)) return 1000;
        int i = FAVORITES.indexOf(e.key);
        return i >= 0 ? i : 500;
    }

    private void collect(PackageManager pm, String category, Map<String, AppEntry> found) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            String pkg = ai.packageName;
            if (pkg.equals(getPackageName()) || SKIP.contains(pkg) || found.containsKey(pkg)) continue;
            ComponentName cn = new ComponentName(pkg, ai.name);
            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            found.put(pkg, new AppEntry(pkg, String.valueOf(ri.loadLabel(pm)), cn, launch));
        }
    }

    private void saveOrder() {
        List<String> keys = new ArrayList<>();
        for (AppEntry e : entries) keys.add(e.key);
        prefs.setOrder(keys);
    }

    // ---------------------------------------------------------------- grid

    /** The first COLS apps go in the glass dock; the rest fill rows underneath. */
    private void buildGrid(int focusIndex) {
        dockRow.removeAllViews();
        grid.removeAllViews();
        tiles.clear();
        List<AppEntry> all = new ArrayList<>(entries);
        if (!hiddenEntries.isEmpty()) {
            all.add(new AppEntry(AppEntry.KEY_HIDDEN, "Hidden apps (" + hiddenEntries.size() + ")", null, null));
        }
        LinearLayout row = dockRow;
        for (int i = 0; i < all.size(); i++) {
            if (i >= COLS && i % COLS == 0) {
                row = row();
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                if (i > COLS) rl.topMargin = rowGap;
                grid.addView(row, rl);
            }
            TileView t = makeTile(all.get(i));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(tileW, tileH);
            if (i % COLS != 0) tl.leftMargin = gap;
            row.addView(t, tl);
            tiles.add(t);
        }
        if (!tiles.isEmpty()) tiles.get(Math.max(0, Math.min(focusIndex, tiles.size() - 1))).requestFocus();
    }

    private TileView makeTile(final AppEntry e) {
        final TileView t = new TileView(this, e, dp);
        if (AppEntry.KEY_HIDDEN.equals(e.key)) {
            t.setGlyph("•••");
        } else {
            Bitmap art = loader.cached(e.key);
            if (art != null) {
                t.setArt(art, false);
            } else {
                loader.load(e, new IconLoader.Callback() {
                    @Override
                    public void onLoaded(Bitmap tile) {
                        t.setArt(tile, true);
                    }
                });
            }
        }
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                t.showFocus(has);
                if (has) onTileFocused(t);
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (moving) endMove();
                else open(e);
            }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!moving) showOptions(t);
                return true;
            }
        });
        return t;
    }

    private void onTileFocused(TileView t) {
        int row = tiles.indexOf(t) / COLS;
        int target = 0;
        View rowView = row > 0 ? grid.getChildAt(row - 1) : null;
        if (rowView != null) {
            target = Math.max(0, dockTop + dockH + rowGap + rowView.getTop() - px(120));
        }
        scroll.smoothScrollTo(0, target);
    }

    private String focusedKey() {
        View f = getCurrentFocus();
        return f instanceof TileView ? ((TileView) f).entry.key : null;
    }

    private void open(AppEntry e) {
        if (AppEntry.KEY_HIDDEN.equals(e.key)) {
            showHidden();
            return;
        }
        try {
            startActivity(e.launch);
        } catch (Exception ex) {
            Toast.makeText(this, "Couldn't open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- remote keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        if (moving) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                switch (code) {
                    case KeyEvent.KEYCODE_DPAD_LEFT: moveBy(-1); return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT: moveBy(1); return true;
                    case KeyEvent.KEYCODE_DPAD_UP: moveBy(-COLS); return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN: moveBy(COLS); return true;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_BACK:
                    case KeyEvent.KEYCODE_MENU:
                        endMove();
                        return true;
                }
            }
            if (code != KeyEvent.KEYCODE_HOME) return true;
        }
        if (code == KeyEvent.KEYCODE_MENU && ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
            View f = getCurrentFocus();
            if (f instanceof TileView) {
                showOptions((TileView) f);
                return true;
            }
        }
        return super.dispatchKeyEvent(ev);
    }

    private void showOptions(final TileView t) {
        final AppEntry e = t.entry;
        if (AppEntry.KEY_HIDDEN.equals(e.key)) {
            showHidden();
            return;
        }
        final String[] items = e.isApp()
                ? new String[]{"Move", "Hide from Home", "App info", "Uninstall"}
                : new String[]{"Move"};
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(e.label)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String pick = items[which];
                        if (pick.equals("Move")) startMove(t);
                        else if (pick.equals("Hide from Home")) hide(e);
                        else if (pick.equals("App info")) system(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, e);
                        else if (pick.equals("Uninstall")) system(Intent.ACTION_DELETE, e);
                    }
                })
                .show();
    }

    private void system(String action, AppEntry e) {
        try {
            startActivity(new Intent(action, Uri.parse("package:" + e.packageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ex) {
            Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void hide(AppEntry e) {
        int idx = entries.indexOf(e);
        Set<String> hidden = prefs.hidden();
        hidden.add(e.key);
        prefs.setHidden(hidden);
        loadEntries();
        buildGrid(Math.max(0, idx));
    }

    private void showHidden() {
        if (hiddenEntries.isEmpty()) return;
        String[] labels = new String[hiddenEntries.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = hiddenEntries.get(i).label;
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Show on Home")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String key = hiddenEntries.get(which).key;
                        Set<String> hidden = prefs.hidden();
                        hidden.remove(key);
                        prefs.setHidden(hidden);
                        reload(key);
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- rearranging

    private void startMove(TileView t) {
        int idx = tiles.indexOf(t);
        if (idx < 0 || idx >= entries.size()) return;
        moving = true;
        moveIndex = idx;
        t.requestFocus();
        t.setWiggle(true);
        Toast.makeText(this, "Use the arrows to move · press select when done", Toast.LENGTH_SHORT).show();
    }

    private void moveBy(int delta) {
        int to = moveIndex + delta;
        if (to < 0 || to >= entries.size()) return;
        AppEntry e = entries.remove(moveIndex);
        entries.add(to, e);
        moveIndex = to;
        saveOrder();
        buildGrid(to);
        tiles.get(to).setWiggle(true);
    }

    private void endMove() {
        moving = false;
        if (moveIndex < tiles.size()) tiles.get(moveIndex).setWiggle(false);
    }

    /** Leaves scrolling to us, so rows land in a consistent spot instead of the edge. */
    private static final class NoAutoScrollView extends ScrollView {
        NoAutoScrollView(Context c) {
            super(c);
        }

        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect rect) {
            return 0;
        }

        @Override
        public boolean executeKeyEvent(KeyEvent event) {
            return false; // let the framework move focus between tiles
        }
    }
}
