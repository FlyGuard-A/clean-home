package com.clean.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live, upcoming and just-finished games from ESPN's public scoreboard feeds:
 * Champions League, LaLiga, Premier League, plus the Timberwolves' current or next game.
 */
final class Scores {
    private static final String ESPN = "https://site.api.espn.com/apis/site/v2/sports/";
    private static final String[][] LEAGUES = {
            {"UCL", "soccer/uefa.champions"},
            {"LALIGA", "soccer/esp.1"},
            {"EPL", "soccer/eng.1"},
    };
    private static final int MAX_ROWS = 6; // fits above the dock

    static final class Game {
        String league, a, b, aLogo, bLogo, aScore, bScore, state, detail;
        long time;
        boolean wolves;
    }

    private final Context ctx;
    private final float dp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService images = Executors.newFixedThreadPool(2);
    private final Map<String, Bitmap> logos = new HashMap<>();
    private final FrostedPanel panel;
    private final LinearLayout list;
    private boolean running;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            io.execute(new Runnable() {
                @Override
                public void run() {
                    List<Game> games = null;
                    try {
                        games = fetch();
                    } catch (Exception ignored) {
                    }
                    final List<Game> result = games;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            render(result);
                            if (!running) return;
                            boolean live = false;
                            if (result != null) for (Game g : result) live |= "in".equals(g.state);
                            main.postDelayed(refresh, live ? 30_000 : 120_000);
                        }
                    });
                }
            });
        }
    };

    Scores(Context c, float dp) {
        ctx = c;
        this.dp = dp;
        panel = new FrostedPanel(c, 18 * dp, 0x66000000, 0x33FFFFFF, dp);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(14), px(10), px(14), px(10));
        panel.addView(list);
        panel.setVisibility(View.GONE);
    }

    View view() {
        return panel;
    }

    void start() {
        running = true;
        main.removeCallbacks(refresh);
        refresh.run();
    }

    void stop() {
        running = false;
        main.removeCallbacks(refresh);
    }

    // ---------------------------------------------------------------- data

    private List<Game> fetch() throws Exception {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat ymd = new SimpleDateFormat("yyyyMMdd", Locale.US);
        String today = ymd.format(cal.getTime());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStart = cal.getTimeInMillis(), dayEnd = dayStart + 24 * 3600_000L;

        List<Game> out = new ArrayList<>();
        for (String[] l : LEAGUES) {
            try {
                JSONArray events = new JSONObject(Net.get(ESPN + l[1] + "/scoreboard?limit=100&dates=" + today))
                        .optJSONArray("events");
                for (int i = 0; events != null && i < events.length(); i++) {
                    Game g = parse(events.getJSONObject(i), l[0], false);
                    if (g == null) continue;
                    // Only today's games (plus anything still live from late last night).
                    if ("in".equals(g.state) || (g.time >= dayStart && g.time < dayEnd)) out.add(g);
                }
            } catch (Exception skipLeague) {
            }
        }
        Game w = wolves(today);
        if (w != null) out.add(w);

        Collections.sort(out, new Comparator<Game>() {
            @Override
            public int compare(Game x, Game y) {
                int px = rank(x), py = rank(y);
                if (px != py) return px - py;
                return "post".equals(x.state) ? Long.compare(y.time, x.time) : Long.compare(x.time, y.time);
            }
        });
        if (out.size() > MAX_ROWS) {
            List<Game> top = new ArrayList<>(out.subList(0, MAX_ROWS));
            if (w != null && !top.contains(w)) top.set(MAX_ROWS - 1, w);
            out = top;
        }
        return out;
    }

    private static int rank(Game g) {
        if ("in".equals(g.state)) return 0;
        if ("pre".equals(g.state)) return 1;
        return 2;
    }

    /** Today's Timberwolves game, if they play today. */
    private Game wolves(String today) {
        try {
            JSONArray events = new JSONObject(Net.get(ESPN + "basketball/nba/scoreboard?dates=" + today)).optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject ev = events.getJSONObject(i);
                if (!ev.toString().contains("\"abbreviation\":\"MIN\"")) continue;
                Game g = parse(ev, "NBA", true);
                if (g != null) {
                    g.wolves = true;
                    return g;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Handles both the scoreboard and team-schedule shapes of an ESPN event. */
    private static Game parse(JSONObject ev, String league, boolean awayFirst) {
        try {
            JSONObject comp = ev.getJSONArray("competitions").getJSONObject(0);
            JSONObject status = ev.optJSONObject("status");
            if (status == null) status = comp.getJSONObject("status");
            JSONObject type = status.getJSONObject("type");
            JSONObject home = null, away = null;
            JSONArray cs = comp.getJSONArray("competitors");
            for (int i = 0; i < cs.length(); i++) {
                JSONObject c = cs.getJSONObject(i);
                if ("home".equals(c.optString("homeAway"))) home = c;
                else away = c;
            }
            if (home == null || away == null) return null;
            JSONObject first = awayFirst ? away : home, second = awayFirst ? home : away;

            Game g = new Game();
            g.league = league;
            g.state = type.optString("state");
            g.detail = type.optString("shortDetail");
            g.time = parseDate(ev.optString("date", comp.optString("date")));
            g.a = abbr(first);
            g.b = abbr(second);
            g.aLogo = logo(first);
            g.bLogo = logo(second);
            g.aScore = score(first);
            g.bScore = score(second);
            return g;
        } catch (Exception e) {
            return null;
        }
    }

    private static String abbr(JSONObject c) {
        JSONObject t = c.optJSONObject("team");
        if (t == null) return "?";
        String a = t.optString("abbreviation");
        return a.isEmpty() ? t.optString("shortDisplayName", "?") : a;
    }

    private static String logo(JSONObject c) {
        JSONObject t = c.optJSONObject("team");
        if (t == null) return null;
        String l = t.optString("logo");
        if (!l.isEmpty()) return l;
        JSONArray ls = t.optJSONArray("logos");
        return ls != null && ls.length() > 0 ? ls.optJSONObject(0).optString("href", null) : null;
    }

    private static String score(JSONObject c) {
        Object s = c.opt("score");
        if (s instanceof JSONObject) return ((JSONObject) s).optString("displayValue", "");
        return s == null ? "" : String.valueOf(s);
    }

    private static long parseDate(String s) {
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm'Z'"};
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                f.setLenient(false);
                return f.parse(s).getTime();
            } catch (ParseException ignored) {
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- view

    private void render(List<Game> games) {
        list.removeAllViews();
        TextView header = text("TODAY", 10, "sans-serif-medium", 0x99FFFFFF);
        header.setLetterSpacing(0.12f);
        header.setPadding(0, 0, 0, px(4));
        list.addView(header);

        if (games == null) {
            list.addView(text("Scores unavailable right now", 13, "sans-serif", 0xB3FFFFFF));
        } else if (games.isEmpty()) {
            list.addView(text("No games today", 13, "sans-serif", 0xB3FFFFFF));
        } else {
            for (Game g : games) list.addView(row(g), new LinearLayout.LayoutParams(-1, px(34)));
        }
        panel.setVisibility(View.VISIBLE);
        panel.invalidate();
    }

    private View row(Game g) {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);

        TextView tag = text(g.wolves ? "WOLVES" : g.league, 8, "sans-serif-medium", 0xFFFFFFFF);
        tag.setGravity(Gravity.CENTER);
        tag.setLetterSpacing(0.06f);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(px(4));
        pill.setColor(leagueColor(g));
        tag.setBackground(pill);
        r.addView(tag, new LinearLayout.LayoutParams(px(46), px(16)));

        r.addView(crest(g.aLogo), crestParams(8));
        r.addView(team(g.a));

        boolean started = !"pre".equals(g.state);
        TextView score = text(started ? g.aScore + " – " + g.bScore : "vs", 14,
                started ? "sans-serif-medium" : "sans-serif-light", 0xFFFFFFFF);
        score.setGravity(Gravity.CENTER);
        r.addView(score, new LinearLayout.LayoutParams(px(46), -2));

        r.addView(team(g.b));
        r.addView(crest(g.bLogo), crestParams(4));

        TextView status = text(status(g), 11, "sans-serif", "in".equals(g.state) ? 0xFFFF5A50 : 0xB3FFFFFF);
        status.setGravity(Gravity.END);
        status.setSingleLine(true);
        r.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    private String status(Game g) {
        if ("in".equals(g.state)) return "● " + g.detail;
        if ("post".equals(g.state)) return g.detail.length() > 6 ? "Final" : g.detail;
        if (g.time == 0) return g.detail;
        Date d = new Date(g.time);
        String time = new SimpleDateFormat(DateFormat.is24HourFormat(ctx) ? "H:mm" : "h:mm a", Locale.getDefault()).format(d);
        Calendar now = Calendar.getInstance(), then = Calendar.getInstance();
        then.setTime(d);
        boolean today = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
        return today ? time : new SimpleDateFormat("EEE", Locale.getDefault()).format(d) + " " + time;
    }

    private static int leagueColor(Game g) {
        if (g.wolves) return 0xFF236192;
        switch (g.league) {
            case "UCL": return 0xFF1C3F94;
            case "LALIGA": return 0xFFE8364A;
            case "EPL": return 0xFF5B2A86;
            default: return 0xFF444444;
        }
    }

    private TextView team(String abbr) {
        TextView t = text(abbr, 13, "sans-serif-medium", 0xFFFFFFFF);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(px(38), -2));
        return t;
    }

    private LinearLayout.LayoutParams crestParams(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(20), px(20));
        lp.leftMargin = px(leftMargin);
        return lp;
    }

    private ImageView crest(final String url) {
        final ImageView iv = new ImageView(ctx);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (url == null) return iv;
        Bitmap hit = logos.get(url);
        if (hit != null) {
            iv.setImageBitmap(hit);
            return iv;
        }
        iv.setTag(url);
        images.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection c = Net.open(url);
                    Bitmap b;
                    try (InputStream in = c.getInputStream()) {
                        b = BitmapFactory.decodeStream(in);
                    } finally {
                        c.disconnect();
                    }
                    if (b == null) return;
                    final Bitmap small = Bitmap.createScaledBitmap(b, px(20) * 2, px(20) * 2 * b.getHeight() / Math.max(1, b.getWidth()), true);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            logos.put(url, small);
                            if (url.equals(iv.getTag())) iv.setImageBitmap(small);
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        });
        return iv;
    }

    private TextView text(String s, float sp, String family, int color) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private int px(float v) {
        return (int) (v * dp + 0.5f);
    }
}
